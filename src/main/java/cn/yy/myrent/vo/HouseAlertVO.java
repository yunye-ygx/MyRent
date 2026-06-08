package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HouseAlertVO {

    private Long id;

    private String city;

    private String region;

    private Integer maxPrice;

    private Integer rentType;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
