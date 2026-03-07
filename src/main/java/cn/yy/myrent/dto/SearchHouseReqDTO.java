package cn.yy.myrent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class SearchHouseReqDTO {

    @NotNull(message = "纬度不能为空")
    private Double latitude;

    @NotNull(message = "经度不能为空")
    private Double longitude;

    // 搜索半径（单位：公里），默认 3 公里
    private String distance = "3km";

    // 分页参数
    private Integer page = 1;
    private Integer size = 10;
}

