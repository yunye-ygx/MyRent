package cn.yy.myrent.vo;

import lombok.Data;

import java.util.List;

@Data
public class HouseSearchResultVO {

    /**
     * 房源列表数据。
     */
    private List<HouseVO> houses;

    /**
     * ES是否不可用。
     * true: ES超时/异常，已进入兜底链路。
     * false: ES可用。
     */
    private Boolean esDown;

    /**
     * 本次数据来源。
     * 可选值：ES、REDIS_HOT、DB_CITY_HOT。
     */
    private String fallbackSource;

    /**
     * 给前端的提示文案。
     * 例如："附近房源加载异常，已为你展示推荐房源"。
     */
    private String tipMessage;
}

