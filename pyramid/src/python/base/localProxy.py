from com.github.catvod import Proxy as CatVodProxy

class Proxy:
    def getUrl(self, local):
        return CatVodProxy.getUrl(local).replace('/proxy', '')

    def getPort(self):
        return CatVodProxy.getPort()
