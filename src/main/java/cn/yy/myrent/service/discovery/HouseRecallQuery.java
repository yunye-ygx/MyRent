package cn.yy.myrent.service.discovery;

import lombok.Builder;

@Builder
public record HouseRecallQuery(
        String keyword,
        String locationName,
        String city,
        String region,
        Integer minPriceYuan,
        Integer maxPriceYuan,
        Integer budgetYuan,
        String budgetScope,
        String rentMode,
        Integer rentType,
        Boolean nearSubway,
        Boolean privateBathroom,
        Boolean hasBalcony,
        Boolean civilWaterElectric,
        Boolean supportStudentDepositFree,
        Integer page,
        Integer size,
        HouseRecallProfile recallProfile
) {
}
