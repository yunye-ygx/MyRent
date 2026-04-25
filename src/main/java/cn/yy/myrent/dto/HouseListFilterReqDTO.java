package cn.yy.myrent.dto;

import lombok.Data;

@Data
public class HouseListFilterReqDTO {

    private String city;

    private String region;

    private Integer rentType;

    private Integer minPriceYuan;

    private Integer maxPriceYuan;

    private Boolean nearSubway;

    private Boolean privateBathroom;

    private Boolean hasBalcony;

    private Boolean civilWaterElectric;

    private Integer page = 1;

    private Integer size = 8;
}
