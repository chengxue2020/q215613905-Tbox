package com.github.tvbox.osc.bean;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;

public class AbsSortJson implements Serializable {

    @SerializedName(value = "class")
    public ArrayList<AbsJsonClass> classes;

    @SerializedName(value = "list")
    public ArrayList<AbsJson.AbsJsonVod> list;

    public AbsSortXml toAbsSortXml() {
        AbsSortXml absSortXml = new AbsSortXml();
        MovieSort movieSort = new MovieSort();
        movieSort.sortList = new ArrayList<>();
        if (classes == null) {
            classes = new ArrayList<>();
        }
        for (AbsJsonClass cls : classes) {
            if (cls == null || cls.type_id == null || cls.type_name == null) {
                continue;
            }
            MovieSort.SortData sortData = new MovieSort.SortData();
            sortData.id = cls.type_id;
            sortData.name = cls.type_name;
            sortData.flag = cls.type_flag;
            movieSort.sortList.add(sortData);
        }
        if (list != null && !list.isEmpty()) {
            Movie movie = new Movie();
            ArrayList<Movie.Video> videos = new ArrayList<>();
            for (AbsJson.AbsJsonVod vod : list) {
                videos.add(vod.toXmlVideo());
            }
            movie.videoList = videos;
            absSortXml.list = movie;
        } else {
            absSortXml.list = null;
        }
        absSortXml.classes = movieSort;
        return absSortXml;
    }

    public class AbsJsonClass implements Serializable {
        @SerializedName(value = "type_id", alternate = "id")
        public String type_id;
        @SerializedName(value = "type_name", alternate = "name")
        public String type_name;
        public String type_flag;
    }

}
