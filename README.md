# TVBoxXD

[![AutoBuildTest](https://github.com/chengxue2020/q215613905-Tbox/actions/workflows/auto_build_with_remark.yml/badge.svg)](https://github.com/chengxue2020/q215613905-Tbox/actions/workflows/auto_build_with_remark.yml)
[![Download](https://img.shields.io/github/v/release/chengxue2020/q215613905-Tbox?color=green&logoColor=green&label=Download&logo=DocuSign)](https://github.com/chengxue2020/q215613905-Tbox/releases)

# TVBoxOSC

![Build](https://shields.io/github/actions/workflow/status/o0HalfLife0o/TVBoxOSC/test.yml?branch=master&logo=github&label=Build)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/TVBoxOSC)
[![Download](https://img.shields.io/github/v/release/o0HalfLife0o/TVBoxOSC?color=orange&logoColor=orange&label=Download&logo=DocuSign)](https://github.com/o0HalfLife0o/TVBoxOSC/releases/latest) 
[![Total](https://shields.io/github/downloads/o0HalfLife0o/TVBoxOSC/total?logo=Bookmeter&label=Counts&logoColor=yellow&color=yellow)](https://github.com/o0HalfLife0o/TVBoxOSC/releases)

## Credits
This repo relies on the following third-party projects:
- [CatVodTVOfficial/TVBoxOSC](https://github.com/CatVodTVOfficial/TVBoxOSC)
- [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS) (Updated: 1a94c846b5845c0556decb1c2c2ca19db2fabe07)
- [chengxue2020/q215613905-Tbox](https://github.com/chengxue2020/q215613905-Tbox) (Updated: 1a94c846b5845c0556decb1c2c2ca19db2fabe07)





-
- # q215613905-Tbox
复刻自TG群俊于phper的库：https://github.com/q215613905/TVBoxOS/ 


1、修改软件名称地址
https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/res/values/strings.xml

2、修改版本号
https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/build.gradle

3、修改图标、背景、载入动画
背景
https://github.com/chengxue2020/q215613905-Tbox/tree/main/app/src/main/res/drawable/app_bg.png
图标
https://github.com/chengxue2020/q215613905-Tbox/tree/main/app/src/main/res/drawable/app_banner.png
https://github.com/chengxue2020/q215613905-Tbox/tree/main/app/src/main/res/drawable-xxxhdpi/app_icon.png
https://github.com/chengxue2020/q215613905-Tbox/tree/main/app/src/main/res/drawable-xxhdpi/app_icon.png
https://github.com/chengxue2020/q215613905-Tbox/tree/main/app/src/main/res/drawable-xhdpi/app_icon.png
https://github.com/chengxue2020/q215613905-Tbox/tree/main/app/src/main/res/drawable-hdpi/app_icon.png
载入动画
https://github.com/chengxue2020/q215613905-Tbox/tree/main/app/src/main/res/drawable/icon_loading.png

4、修改内置源
俊老仓库打开下面,第83行
https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java
takagen99大佬仓库 改这里;app/src/main/res/values-zh/strings.xml


5、修改默认缩略图、硬解、dns
地址：
https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/java/com/github/tvbox/osc/base/App.java

    private void initParams() {
        // 自定义默认配置
        Hawk.init(this).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
		Hawk.put(HawkConfig.HOME_REC, 1);       // Home Rec 0=豆瓣, 1=站点推荐, 2=历史
		Hawk.put(HawkConfig.SEARCH_VIEW, 1);    // 0 文字搜索列表 1 缩略图搜索列表
		Hawk.put(HawkConfig.IJK_CODEC, "硬解码");  //解码
		Hawk.put(HawkConfig.DOH_URL, 2);		//DOH
		Hawk.put(HawkConfig.SEARCH_VIEW, 1);

        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
            Hawk.put(HawkConfig.PLAY_TYPE, 1);
			
        }
    }

或

	//自定义默认配置，硬解，安全dns，缩略图
         if (!Hawk.contains(HawkConfig.IJK_CODEC)) {
	 Hawk.put(HawkConfig.IJK_CODEC, "硬解码");
  	}
   	if (!Hawk.contains(HawkConfig.DOH_URL)) {
    	Hawk.put(HawkConfig.DOH_URL, 2);
     	}
      	if (!Hawk.contains(HawkConfig.SEARCH_VIEW)) {
       	Hawk.put(HawkConfig.SEARCH_VIEW, 2);
	}

6、修改直播界面频道列表中跳动条的颜色、个数
https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/java/com/github/tvbox/osc/ui/tv/widget/AudioWaveView.java


7、修改点播详情界面字体的颜色（27行）
https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/java/com/github/tvbox/osc/ui/adapter/SeriesAdapter.java


8、修改DOH
https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/java/com/github/tvbox/osc/util/OkGoHelper.java


9、修改点播界面进度条颜色
https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/res/drawable/shape_player_control_vod_seek.xml

10、修改EPG
tk版178行 俊版204行
 https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/java/com/github/tvbox/osc/ui/activity/LivePlayActivity.java

// Getting EPG Address epgStringAddress = Hawk.get(HawkConfig.EPG_URL, ""); 
if (StringUtils.isBlank(epgStringAddress)) { epgStringAddress = "https://epg.112114.xyz/"; 
// Hawk.put(HawkConfig.EPG_URL, epgStringAddress);


11、修改首页源列数

 https://github.com/chengxue2020/q215613905-Tbox/blob/main/app/src/main/java/com/github/tvbox/osc/ui/activity/HomeActivity.java

624行
	spanCount = (int)Math.floor(sites.size()/60);
        spanCount = Math.min(spanCount, 2);
	
Q版624行，T版770行


## 额外新的参数如下

壁纸:
```javascript
{
    "wallpaper": "壁纸路径" //例子 "https://picsum.photos/1920/1080/?blur=10"
}
```

单Jar的定义方式不变，直接放路径即可:
```javascript
{
    ...
    "spider": "jar地址"
    ...
```

多Jars:
```javascript
{
    ...
    "spider": [
        { "n": "default", "v": "jar地址" }, //默认jar
        { "n": "jar1", "v": "jar1 地址" },
        { "n": "jar2", "v": "jar2 地址" }
        ...
    ],
    "sites": [
        ...
        { "key": "csp_csp1", "name": "CSP1", ..., "spider": "jar1" }, //对应spider里的n值
        { "key": "csp_csp2", "name": "CSP2", ..., "spider": "jar2" },
        { "key": "csp_csp3", "name": "CSP3", ... },  //没有spider参数的话，使用默认jar
        { "key": "csp_csp4", "name": "CSP4", ..., "spider": "jar2" },
        ...
    ],
    ...
}
```

