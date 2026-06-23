package com.github.tvbox.osc.bean;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Map;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItem {
    /**
     * channelIndex : 频道索引号
     * channelNum : 频道名称
     * channelSourceNames : 频道源名称
     * channelUrls : 频道源地址
     * sourceIndex : 频道源索引
     * sourceNum : 频道源总数
     */
    private int channelIndex;
    private int channelNum;
    private String channelName;
    private String channelLogo;
    private String channelEpg;
    private String channelUa;
    private String channelClick;
    private String channelFormat;
    private String channelOrigin;
    private String channelReferer;
    private String channelTvgId;
    private String channelTvgName;
    private JsonObject channelCatchup;
    private Map<String, String> channelHeader;
    private Integer channelParse;
    private ArrayList<String> channelSourceNames;
    private ArrayList<String> channelUrls;
    public int sourceIndex = 0;
    public int sourceNum = 0;
    public boolean include_back = false;

    public void setinclude_back(boolean include_back) {
        this.include_back = include_back;
    }

    public boolean getinclude_back() {
        return include_back;
    }

    public void setChannelIndex(int channelIndex) {
        this.channelIndex = channelIndex;
    }

    public int getChannelIndex() {
        return channelIndex;
    }

    public void setChannelNum(int channelNum) {
        this.channelNum = channelNum;
    }

    public int getChannelNum() {
        return channelNum;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelLogo(String channelLogo) {
        this.channelLogo = channelLogo;
    }

    public String getChannelLogo() {
        return channelLogo == null ? "" : channelLogo;
    }

    public void setChannelEpg(String channelEpg) {
        this.channelEpg = channelEpg;
    }

    public String getChannelEpg() {
        return channelEpg == null ? "" : channelEpg;
    }

    public void setChannelUa(String channelUa) {
        this.channelUa = channelUa;
    }

    public String getChannelUa() {
        return channelUa == null ? "" : channelUa;
    }

    public void setChannelClick(String channelClick) {
        this.channelClick = channelClick;
    }

    public String getChannelClick() {
        return channelClick == null ? "" : channelClick;
    }

    public void setChannelFormat(String channelFormat) {
        this.channelFormat = channelFormat;
    }

    public String getChannelFormat() {
        return channelFormat == null ? "" : channelFormat;
    }

    public void setChannelOrigin(String channelOrigin) {
        this.channelOrigin = channelOrigin;
    }

    public String getChannelOrigin() {
        return channelOrigin == null ? "" : channelOrigin;
    }

    public void setChannelReferer(String channelReferer) {
        this.channelReferer = channelReferer;
    }

    public String getChannelReferer() {
        return channelReferer == null ? "" : channelReferer;
    }

    public void setChannelTvgId(String channelTvgId) {
        this.channelTvgId = channelTvgId;
    }

    public String getChannelTvgId() {
        return channelTvgId == null ? "" : channelTvgId;
    }

    public void setChannelTvgName(String channelTvgName) {
        this.channelTvgName = channelTvgName;
    }

    public String getChannelTvgName() {
        return channelTvgName == null ? "" : channelTvgName;
    }

    public void setChannelCatchup(JsonObject channelCatchup) {
        this.channelCatchup = channelCatchup;
    }

    public JsonObject getChannelCatchup() {
        return channelCatchup == null ? new JsonObject() : channelCatchup;
    }

    public boolean hasCatchup() {
        return channelCatchup != null && channelCatchup.entrySet().size() > 0;
    }

    public void setChannelHeader(Map<String, String> channelHeader) {
        this.channelHeader = channelHeader;
    }

    public Map<String, String> getChannelHeader() {
        return channelHeader == null ? new HashMap<String, String>() : channelHeader;
    }

    public void setChannelParse(Integer channelParse) {
        this.channelParse = channelParse;
    }

    public int getChannelParse() {
        return channelParse == null ? 0 : channelParse.intValue();
    }

    public Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>(getChannelHeader());
        if (!getChannelUa().isEmpty()) headers.put("User-Agent", getChannelUa());
        if (!getChannelOrigin().isEmpty()) headers.put("Origin", getChannelOrigin());
        if (!getChannelReferer().isEmpty()) headers.put("Referer", getChannelReferer());
        return headers;
    }

    public ArrayList<String> getChannelUrls() {
        return channelUrls;
    }

    public void setChannelUrls(ArrayList<String> channelUrls) {
        this.channelUrls = channelUrls;
        sourceNum = channelUrls.size();
    }
    public void preSource() {
        sourceIndex--;
        if (sourceIndex < 0) sourceIndex = sourceNum - 1;
    }
    public void nextSource() {
        sourceIndex++;
        if (sourceIndex == sourceNum) sourceIndex = 0;
    }

    public void setSourceIndex(int sourceIndex) {
        this.sourceIndex = sourceIndex;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public String getUrl() {
        return channelUrls.get(sourceIndex);
    }

    public int getSourceNum() {
        return sourceNum;
    }

    public ArrayList<String> getChannelSourceNames() {
        return channelSourceNames;
    }

    public void setChannelSourceNames(ArrayList<String> channelSourceNames) {
        this.channelSourceNames = channelSourceNames;
    }

    public String getSourceName() {
        return channelSourceNames.get(sourceIndex);
    }

    public boolean isEmptyCatchup() {
        return channelCatchup == null || channelCatchup.entrySet().size() == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiveChannelItem that = (LiveChannelItem) o;
        return Objects.equals(channelName, that.channelName)
                && Objects.equals(channelUrls.get(sourceIndex), that.getUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelName, channelUrls.get(sourceIndex));
    }
}