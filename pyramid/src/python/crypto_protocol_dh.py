import importlib
import sys
import types


def _compute_ecdh(key_priv, key_pub, long_to_bytes):
    point = key_pub.pointQ * key_priv.d
    if point.is_point_at_infinity():
        raise ValueError("Invalid ECDH point")
    return long_to_bytes(point.x, point.size_in_bytes())


def _install():
    try:
        importlib.import_module("Crypto.Protocol.DH")
        return
    except ModuleNotFoundError as error:
        if error.name != "Crypto.Protocol.DH":
            raise

    from Crypto.PublicKey import ECC
    from Crypto.PublicKey.ECC import EccKey
    from Crypto.Util.number import bytes_to_long, long_to_bytes

    if not hasattr(EccKey, "_export_SEC1"):
        export_key = EccKey.export_key

        def export_sec1_compatible(self, *args, **kwargs):
            key_format = kwargs.get("format", args[0] if args else "PEM")
            if key_format == "SEC1":
                point = self.pointQ
                size = point.size_in_bytes()
                x = long_to_bytes(point.x, size)
                if kwargs.get("compress", False):
                    return (b"\x03" if int(point.y) & 1 else b"\x02") + x
                return b"\x04" + x + long_to_bytes(point.y, size)
            return export_key(self, *args, **kwargs)

        EccKey.export_key = export_sec1_compatible

        import_key = ECC.import_key

        def import_sec1_compatible(encoded, passphrase=None, curve_name=None):
            raw = bytes(encoded) if isinstance(encoded, bytearray) else encoded
            if curve_name and isinstance(raw, bytes) and len(raw) > 1 and raw[0] == 4:
                size = (len(raw) - 1) // 2
                if len(raw) != 1 + size * 2:
                    raise ValueError("Invalid SEC1 public key")
                return ECC.construct(
                    curve=curve_name,
                    point_x=bytes_to_long(raw[1:1 + size]),
                    point_y=bytes_to_long(raw[1 + size:]),
                )
            return import_key(encoded, passphrase)

        ECC.import_key = import_sec1_compatible

    def key_agreement(**kwargs):
        kdf = kwargs.get("kdf")
        if kdf is None:
            raise ValueError("'kdf' is mandatory")

        static_priv = kwargs.get("static_priv")
        static_pub = kwargs.get("static_pub")
        eph_priv = kwargs.get("eph_priv")
        eph_pub = kwargs.get("eph_pub")
        curve = None
        count_priv = 0
        count_pub = 0

        def check_curve(key, name, private):
            nonlocal curve
            if not isinstance(key, EccKey):
                raise TypeError("'%s' must be an ECC key" % name)
            if private and not key.has_private():
                raise TypeError("'%s' must be a private ECC key" % name)
            if curve is None:
                curve = key.curve
            elif curve != key.curve:
                raise TypeError("'%s' is defined on an incompatible curve" % name)

        if static_priv is not None:
            check_curve(static_priv, "static_priv", True)
            count_priv += 1
        if static_pub is not None:
            check_curve(static_pub, "static_pub", False)
            count_pub += 1
        if eph_priv is not None:
            check_curve(eph_priv, "eph_priv", True)
            count_priv += 1
        if eph_pub is not None:
            check_curve(eph_pub, "eph_pub", False)
            count_pub += 1

        if count_priv + count_pub < 2 or count_priv == 0 or count_pub == 0:
            raise ValueError("Too few keys for the ECDH key agreement")

        static_secret = b""
        ephemeral_secret = b""
        if static_priv and static_pub:
            static_secret = _compute_ecdh(static_priv, static_pub, long_to_bytes)
        if eph_priv and eph_pub:
            if bool(static_priv) != bool(static_pub):
                raise ValueError("DH mode C(2e, 1s) is not supported")
            ephemeral_secret = _compute_ecdh(eph_priv, eph_pub, long_to_bytes)
        elif eph_priv and static_pub:
            ephemeral_secret = _compute_ecdh(eph_priv, static_pub, long_to_bytes)
        elif eph_pub and static_priv:
            ephemeral_secret = _compute_ecdh(static_priv, eph_pub, long_to_bytes)

        return kdf(ephemeral_secret + static_secret)

    module = types.ModuleType("Crypto.Protocol.DH")
    module.key_agreement = key_agreement
    sys.modules["Crypto.Protocol.DH"] = module
    setattr(importlib.import_module("Crypto.Protocol"), "DH", module)


_install()
