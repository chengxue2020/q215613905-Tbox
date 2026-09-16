#coding=utf-8
#!/usr/bin/python
import os
import requests
from importlib.machinery import SourceFileLoader  ### 导入这个模块
from urllib import parse
import json
import sys
import crypto_protocol_dh
sys.dont_write_bytecode = True

# Keep Python requests as the default and fall back to the app's configured
# Java HTTP client when an HTTPS endpoint rejects the embedded transport.
_session_request = requests.sessions.Session.request
try:
    from com.undcover.freedom.pyramid import PythonHttp as _PythonHttp
except Exception:
    _PythonHttp = None

class _JavaResponse:
    def __init__(self, payload):
        self.status_code = int(payload.get("status_code", 0))
        self.headers = payload.get("headers", {})
        self.text = payload.get("text", "")
        self.content = self.text.encode("utf-8")

    def json(self):
        return json.loads(self.text)

def _java_request(session, method, url, kwargs):
    params = kwargs.get("params")
    if params:
        query = parse.urlencode(params, doseq=True)
        url += ("&" if "?" in url else "?") + query
    headers = dict(session.headers)
    headers.update(kwargs.get("headers") or {})
    # Let OkHttp's transparent gzip interceptor decode the response.
    headers.pop("Accept-Encoding", None)
    cookies = getattr(session, "cookies", None)
    if cookies:
        cookie_header = "; ".join(str(key) + "=" + str(value) for key, value in cookies.items())
        if cookie_header:
            headers["Cookie"] = cookie_header
    body = kwargs.get("json")
    if body is not None:
        body = json.dumps(body, ensure_ascii=False)
        headers.setdefault("Content-Type", "application/json")
    elif kwargs.get("data") is not None:
        body = kwargs.get("data")
        if not isinstance(body, str):
            body = str(body)
    else:
        body = ""
    raw = _PythonHttp.request(
        method,
        url,
        json.dumps(headers, ensure_ascii=False),
        body,
        kwargs.get("allow_redirects", True),
    )
    payload = json.loads(str(raw))
    if "error" in payload:
        raise requests.RequestException(payload["error"])
    response = _JavaResponse(payload)
    set_cookie = response.headers.get("Set-Cookie") or response.headers.get("set-cookie")
    if set_cookie and cookies is not None:
        for item in str(set_cookie).split(","):
            pair = item.split(";", 1)[0].strip()
            if "=" in pair:
                key, value = pair.split("=", 1)
                cookies.set(key.strip(), value.strip())
    return response

_JAVA_FALLBACK_STATUS = (403, 429, 495, 496, 497, 525, 526, 527)

if not getattr(_session_request, "_tvbox_java_http_fallback", False):
    def _tvbox_session_request(self, method, url, **kwargs):
        if _PythonHttp is None or not str(url).lower().startswith("https://") or kwargs.get("stream"):
            return _session_request(self, method, url, **kwargs)
        try:
            response = _session_request(self, method, url, **kwargs)
            if response.status_code not in _JAVA_FALLBACK_STATUS:
                return response
        except requests.RequestException:
            response = None
        fallback = _java_request(self, method, url, kwargs)
        return fallback if response is None or fallback.status_code < response.status_code else response
    _tvbox_session_request._tvbox_java_http_fallback = True
    requests.sessions.Session.request = _tvbox_session_request

PLUGIN_DOWNLOAD_TIMEOUT = 20

def createFile(file_path):
    if os.path.exists(file_path) is False:
        os.makedirs(file_path)

def redirectResponse(tUrl):
  rsp = requests.get(tUrl, allow_redirects=False, verify=False, timeout=PLUGIN_DOWNLOAD_TIMEOUT)
  if 'Location' in rsp.headers:
    return redirectResponse(rsp.headers['Location'])
  else:
    return rsp

def downloadFile(name,url):
    try:
        rsp = redirectResponse(url)
        with open(name,'wb') as f:
            f.write(rsp.content)
        print(url)
    except:
        print(name + ' =======================================> error')
        print(url)

def downloadPlugin(basePath,url):
    createFile(basePath)
    name = url.split('/')[-1].split('.')[0]
    pyName = ''
    if url.startswith('file://'):
        pyName = url.replace('file://','')
    else:
        pyName = basePath + name+'.py'
        downloadFile(pyName,url)
    sPath = gParam['SpiderPath']
    sPath[name] = pyName
    sParam = gParam['SpiderParam']
    paramList = parse.parse_qs(parse.urlparse(url).query).get('extend')
    if paramList == None:
        paramList = ['']
    sParam[name] = paramList[0]
    return pyName

def registerPluginAlias(alias,fileName):
    if alias == None or alias == '':
        return
    name = fileName.split('/')[-1].split('.')[0]
    sPath = gParam['SpiderPath']
    sPath[alias] = fileName
    sParam = gParam['SpiderParam']
    sParam[alias] = sParam[name] if name in sParam.keys() else ''

def loadFromDisk(fileName):
    name = fileName.split('/')[-1].split('.')[0]
    spList = gParam['SpiderList']
    sp = SourceFileLoader(name, fileName).load_module().Spider()
    spList[name] = sp
    return spList[name]

def str2json(content):
    return json.loads(content)

def getDependenceList(ru):
    get_dependence = getattr(ru, 'getDependence', None)
    if callable(get_dependence):
        result = get_dependence()
        return result if result is not None else []
    return []

def setExtendInfo(ru, extend):
    setter = getattr(ru, 'setExtendInfo', None)
    if callable(setter):
        setter(extend)
    else:
        setattr(ru, 'extend', extend)

gParam = {
    "SpiderList":{},
    "SpiderPath":{},
    "SpiderParam":{}
}

def getDependence(ru):
    return getDependenceList(ru)

def getName(ru):
    result = ru.getName()
    return result

def init(ru,extend):
    spoList = []
    spList = gParam['SpiderList']
    sPath = gParam['SpiderPath']
    sParam = gParam['SpiderParam']
    for key in getDependenceList(ru):
        sp = None
        if key in spList.keys():
            sp = spList[key]
        elif key in sPath.keys():
            sp = loadFromDisk(sPath[key])
        if sp != None:
            setExtendInfo(sp, sParam[key])
            spoList.append(sp)
    setExtendInfo(ru, extend)
    ru.init(spoList if len(spoList) > 0 else extend)

def homeContent(ru,filter):
    result = ru.homeContent(filter)
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def homeVideoContent(ru):
    result = ru.homeVideoContent()
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def categoryContent(ru,tid, pg, filter, extend):
    result = ru.categoryContent(tid, pg, filter, str2json(extend))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def detailContent(ru,array):
    result = ru.detailContent(str2json(array))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def playerContent(ru,flag,id,vipFlags):
    result = ru.playerContent(flag,id,str2json(vipFlags))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def liveContent(ru,url):
    result = ru.liveContent(url)
    return result

def searchContent(ru,key,quick):
    result = ru.searchContent(key,quick)
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def localProxy(ru,param):
    result = ru.localProxy(str2json(param))
    return result

def destroy(ru):
    ru.destroy()

def run():
    pass

if __name__ == '__main__':
    run()
