package cn.yy.myrent.service.discovery;

import lombok.Builder;

import java.util.Map;

@Builder
public record HouseRankQuery(
        Integer budgetYuan,
        String budgetScope,
        String rentMode,
        Integer page,
        Integer size,
        Boolean nearSubway,
        Boolean privateBathroom,
        Boolean hasBalcony,
        Boolean civilWaterElectric,
        Boolean supportStudentDepositFree,
        Map<String, Integer> preferenceWeightMap,
        Boolean budgetRelaxable,
        Integer budgetRelaxLimitYuan,
        HouseRankingProfile rankingProfile,
        Integer minPriceYuan,
        Integer maxPriceYuan,
        boolean recallAlreadyPaged
) {
}
