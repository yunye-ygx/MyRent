package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendSlots {

    private String city;

    private String locationName;

    private Integer budgetYuan;

    private String budgetScope;

    private String rentMode;

    private String priority;

    @Builder.Default
    private List<String> preferences = new ArrayList<>();

    @Builder.Default
    private List<AiWeightedPreference> weightedPreferences = new ArrayList<>();

    private Boolean budgetRelaxable;

    private Integer budgetRelaxLimitYuan;

    private String tradeoffReason;
}
