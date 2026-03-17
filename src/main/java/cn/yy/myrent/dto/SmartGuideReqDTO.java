package cn.yy.myrent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SmartGuideReqDTO {

    @NotNull(message = "预算不能为空")
    @Min(value = 300, message = "预算最低为300元")
    @Max(value = 50000, message = "预算最高为50000元")
    private Integer budgetYuan;

    @NotBlank(message = "预算口径不能为空")
    private String budgetScope;

    @NotBlank(message = "租住方式不能为空")
    private String rentMode;

    @NotBlank(message = "通勤地铁站不能为空")
    private String commuteMetroStation;

    private Double stationLatitude;

    private Double stationLongitude;

    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大为50")
    private Integer size = 10;
}
