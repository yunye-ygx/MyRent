package cn.yy.myrent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "智能搜房推荐结果")
public class SmartGuideResultVO {

    @Schema(description = "用户原始预算（元）")
    private Integer originalBudgetYuan;

    @Schema(description = "是否触发预算放宽")
    private Boolean relaxedBudget;

    @Schema(description = "是否找到更贴近用户目标预算的房源")
    private Boolean matchedExpectation;

    @Schema(description = "放宽后的预算（元）")
    private Integer relaxedBudgetYuan;

    @Schema(description = "提示信息")
    private String tipMessage;

    @Schema(description = "推荐房源列表")
    private List<SmartGuideItemVO> recommendations;
}
