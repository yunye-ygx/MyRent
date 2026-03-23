package cn.yy.myrent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "智能找房推荐条目")
public class SmartGuideItemVO {

    @Schema(description = "房源ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long houseId;

    @Schema(description = "发布人用户ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long publisherUserId;

    @Schema(description = "房源标题")
    private String title;

    @Schema(description = "房源状态：1可租、2暂锁")
    private Integer status;

    @Schema(description = "月租价格（元）")
    private BigDecimal price;

    @Schema(description = "押金（元）")
    private BigDecimal depositAmount;

    @Schema(description = "首月总成本（月租+押金，元）")
    private BigDecimal totalCost;

    @Schema(description = "到目标地点距离（公里）")
    private BigDecimal distanceToMetroKm;

    @Schema(description = "预估通勤时长（分钟）")
    private Integer estimatedCommuteMinutes;

    @Schema(description = "综合评分（降序排序）")
    private BigDecimal score;

    @Schema(description = "推荐理由（最多3条）")
    private List<String> reasons;
}
