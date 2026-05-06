package cn.yy.myrent.service.smartguide;

import lombok.Builder;

@Builder
public record SmartGuideCandidateQuery(
        String locationName,
        Integer budgetYuan,
        String budgetScope,
        String rentMode,
        Integer size
) {
}
