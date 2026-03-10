package cn.yy.myrent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class SearchHouseReqDTO {

    @NotNull(message = "纬度不能为空")
    private Double latitude;

    @NotNull(message = "经度不能为空")
    private Double longitude;

    // 搜索半径（单位：公里），默认 3 公里
    @Min(value = 100, message = "搜索半径最小为100米")
    @Max(value = 50000, message = "搜索半径最大为50km")
    private String radius = "5km";

    // 分页参数
    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大不能超过50")
    private Integer size = 10;
}

