package cn.yy.myrent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HouseVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long publisherUserId;

    private String publisherName;

    private String title;

    private String city;

    private String region;

    private Boolean nearSubway;

    private Boolean privateBathroom;

    private Boolean hasBalcony;

    private Boolean civilWaterElectric;

    private BigDecimal price;

    private BigDecimal depositAmount;

    private String distance;

    private Integer status;

    private Long favoriteCount;

    private Long recentFavoriteCount;

    private Long recentConsultCount;

    private Long recentReplyCount;

    private Double hotScore;
}
