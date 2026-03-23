package cn.yy.myrent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SmartGuideReqDTO {

    @NotNull(message = "预算不能为空")
    @Min(value = 300, message = "预算最低为300元")
    @Max(value = 50000, message = "预算最高为50000元")
    private Integer budgetYuan;

    private String budgetScope;

    private String rentMode;

    private String locationName;

    // Compatibility fallback for older callers.
    private String commuteMetroStation;

    // Retained for compatibility; V2 no longer depends on caller-provided coordinates.
    private Double stationLatitude;

    private Double stationLongitude;

    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大为50")
    private Integer size = 10;
}
