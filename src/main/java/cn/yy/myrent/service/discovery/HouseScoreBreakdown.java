package cn.yy.myrent.service.discovery;

import lombok.Builder;

@Builder
public record HouseScoreBreakdown(
        double recallScore,
        double textRelevanceScore,
        double locationDistanceScore,
        double budgetCloseScore,
        double rentModeMatchScore,
        double nearSubwayScore,
        double privateBathroomScore,
        double hasBalconyScore,
        double civilWaterElectricScore,
        double supportStudentDepositFreeScore,
        double primaryPreferenceScore,
        double secondaryPreferenceScore,
        double relaxationAcceptanceScore,
        double relaxationPenaltyOrAdjustment,
        double freshnessScore
) {

    public double totalScore() {
        return recallScore
                + textRelevanceScore
                + locationDistanceScore
                + budgetCloseScore
                + rentModeMatchScore
                + nearSubwayScore
                + privateBathroomScore
                + hasBalconyScore
                + civilWaterElectricScore
                + supportStudentDepositFreeScore
                + primaryPreferenceScore
                + secondaryPreferenceScore
                + relaxationAcceptanceScore
                + relaxationPenaltyOrAdjustment
                + freshnessScore;
    }
}
