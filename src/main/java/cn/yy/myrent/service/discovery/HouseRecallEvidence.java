package cn.yy.myrent.service.discovery;

import lombok.Builder;

@Builder
public record HouseRecallEvidence(
        boolean locationMatched,
        boolean textMatched,
        Double locationDistanceMeters,
        Integer locationRank,
        Integer textRank,
        Float textScore,
        boolean exactConstraintMatched,
        boolean relaxedBudgetApplied,
        boolean relaxedRadiusApplied,
        boolean nearSubwayMatched,
        boolean privateBathroomMatched,
        boolean hasBalconyMatched,
        boolean civilWaterElectricMatched,
        boolean supportStudentDepositFreeMatched
) {
}
