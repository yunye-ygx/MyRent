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
public class AiRecommendRankingPayload {

    private SlotSnapshot slots;

    @Builder.Default
    private List<ListingPayload> topListings = new ArrayList<>();

    @Builder.Default
    private List<String> sharedReasonCodes = new ArrayList<>();

    @Builder.Default
    private List<String> sharedReasonHighlights = new ArrayList<>();

    @Builder.Default
    private List<String> primaryReasonHighlights = new ArrayList<>();

    @Builder.Default
    private List<String> secondaryReasonHighlights = new ArrayList<>();

    @Builder.Default
    private List<String> relaxationHighlights = new ArrayList<>();

    private String tradeoffSummary;

    private String summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotSnapshot {
        private String city;
        private String locationName;
        private Integer budgetYuan;
        private String budgetScope;
        private String rentMode;
        private String priority;
        @Builder.Default
        private List<String> preferences = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListingPayload {
        private Long houseId;
        private String title;
        private Double score;
        @Builder.Default
        private List<String> reasonCodes = new ArrayList<>();
        @Builder.Default
        private List<String> reasonHighlights = new ArrayList<>();
        private ScoreBreakdownPayload scoreBreakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBreakdownPayload {
        private Double recallScore;
        private Double textRelevanceScore;
        private Double locationDistanceScore;
        private Double budgetCloseScore;
        private Double rentModeMatchScore;
        private Double nearSubwayScore;
        private Double privateBathroomScore;
        private Double hasBalconyScore;
        private Double civilWaterElectricScore;
        private Double supportStudentDepositFreeScore;
        private Double primaryPreferenceScore;
        private Double secondaryPreferenceScore;
        private Double relaxationAcceptanceScore;
        private Double relaxationPenaltyOrAdjustment;
        private Double freshnessScore;
    }
}
