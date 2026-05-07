package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class HouseRankingServiceImpl implements HouseRankingService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int BUDGET_REASON_MAX_DIFF_YUAN = 300;
    private static final double BUDGET_REASON_MIN_SCORE = 120.0d;
    private final Clock clock;

    public HouseRankingServiceImpl() {
        this(Clock.systemDefaultZone());
    }

    HouseRankingServiceImpl(Clock clock) {
        this.clock = clock;
    }

    @Override
    public HouseRankResult rank(List<HouseRecallCandidate> candidates, HouseRankQuery query) {
        List<HouseRankedItem> rankedItems = (candidates == null ? List.<HouseRecallCandidate>of() : candidates).stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.house() != null)
                .map(candidate -> rankCandidate(candidate, query))
                .sorted(Comparator
                        .comparingDouble(HouseRankedItem::score).reversed()
                        .thenComparing(item -> item.house().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.house().getId(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<HouseRankedItem> currentPageItems = shouldKeepCurrentCandidatePage(query)
                ? rankedItems
                : slicePage(rankedItems, resolvePage(query), resolveSize(query));
        return new HouseRankResult(rankedItems, currentPageItems, rankedItems.size());
    }

    private HouseRankedItem rankCandidate(HouseRecallCandidate candidate, HouseRankQuery query) {
        House house = candidate.house();
        HouseRecallEvidence evidence = candidate.recallEvidence();
        HouseRankingProfile profile = query == null || query.rankingProfile() == null
                ? HouseRankingProfile.SEARCH_DEFAULT
                : query.rankingProfile();

        double recallScore = buildRecallScore(evidence);
        double textRelevanceScore = buildTextRelevanceScore(evidence, profile);
        double locationDistanceScore = buildLocationDistanceScore(evidence, profile);
        double budgetCloseScore = buildBudgetCloseScore(house, query, profile);
        double rentModeMatchScore = buildRentModeMatchScore(house, query);
        double nearSubwayScore = buildFeatureScore(Boolean.TRUE.equals(query == null ? null : query.nearSubway()), isEnabled(house.getNearSubway()), 35.0d);
        double privateBathroomScore = buildFeatureScore(Boolean.TRUE.equals(query == null ? null : query.privateBathroom()), isEnabled(house.getPrivateBathroom()), 35.0d);
        double hasBalconyScore = buildFeatureScore(Boolean.TRUE.equals(query == null ? null : query.hasBalcony()), isEnabled(house.getHasBalcony()), 25.0d);
        double civilWaterElectricScore = buildFeatureScore(Boolean.TRUE.equals(query == null ? null : query.civilWaterElectric()), isEnabled(house.getCivilWaterElectric()), 25.0d);
        double supportStudentDepositFreeScore = buildFeatureScore(
                Boolean.TRUE.equals(query == null ? null : query.supportStudentDepositFree()),
                isEnabled(house.getSupportStudentDepositFree()),
                30.0d
        );
        double primaryPreferenceScore = buildWeightedPreferenceScore(house, query, 3, 80.0d);
        double secondaryPreferenceScore = buildWeightedPreferenceScore(house, query, 2, 45.0d)
                + buildWeightedPreferenceScore(house, query, 1, 15.0d);
        double relaxationAcceptanceScore = buildRelaxationAcceptanceScore(query, evidence);
        double relaxationAdjustment = buildRelaxationAdjustment(evidence);
        double freshnessScore = buildFreshnessScore(house);

        HouseScoreBreakdown scoreBreakdown = HouseScoreBreakdown.builder()
                .recallScore(recallScore)
                .textRelevanceScore(textRelevanceScore)
                .locationDistanceScore(locationDistanceScore)
                .budgetCloseScore(budgetCloseScore)
                .rentModeMatchScore(rentModeMatchScore)
                .nearSubwayScore(nearSubwayScore)
                .privateBathroomScore(privateBathroomScore)
                .hasBalconyScore(hasBalconyScore)
                .civilWaterElectricScore(civilWaterElectricScore)
                .supportStudentDepositFreeScore(supportStudentDepositFreeScore)
                .primaryPreferenceScore(primaryPreferenceScore)
                .secondaryPreferenceScore(secondaryPreferenceScore)
                .relaxationAcceptanceScore(relaxationAcceptanceScore)
                .relaxationPenaltyOrAdjustment(relaxationAdjustment)
                .freshnessScore(freshnessScore)
                .build();

        return new HouseRankedItem(
                house,
                scoreBreakdown.totalScore(),
                scoreBreakdown,
                buildReasonCodes(house, query, evidence, scoreBreakdown)
        );
    }

    private double buildRecallScore(HouseRecallEvidence evidence) {
        if (evidence == null) {
            return 0;
        }
        double score = 0;
        if (evidence.locationMatched()) {
            score += 1000;
        }
        if (evidence.textMatched()) {
            score += 600;
        }
        if (evidence.locationMatched() && evidence.textMatched()) {
            score += 200;
        }
        if (evidence.exactConstraintMatched()) {
            score += 150;
        }
        return score;
    }

    private double buildTextRelevanceScore(HouseRecallEvidence evidence, HouseRankingProfile profile) {
        if (evidence == null) {
            return 0;
        }
        double rankScore = evidence.textRank() == null ? 0 : Math.max(0, 80 - evidence.textRank() * 5.0d);
        double textScore = evidence.textScore() == null ? 0 : Math.max(0, evidence.textScore() * (profile == HouseRankingProfile.AI_RECOMMEND_DEFAULT ? 18.0d : 12.0d));
        return rankScore + textScore;
    }

    private double buildLocationDistanceScore(HouseRecallEvidence evidence, HouseRankingProfile profile) {
        if (evidence == null || evidence.locationDistanceMeters() == null) {
            return 0;
        }
        double distance = evidence.locationDistanceMeters();
        if (profile == HouseRankingProfile.AI_RECOMMEND_DEFAULT) {
            return Math.max(0, 80 - distance / 40.0d);
        }
        return Math.max(0, 120 - distance / 20.0d);
    }

    private double buildBudgetCloseScore(House house, HouseRankQuery query, HouseRankingProfile profile) {
        Integer targetBudgetYuan = resolveTargetBudgetYuan(query);
        if (targetBudgetYuan == null || house.getPrice() == null) {
            return 0;
        }
        int housePriceYuan = Math.max(house.getPrice(), 0) / 100;
        int diff = Math.abs(housePriceYuan - targetBudgetYuan);
        double baseScore = profile == HouseRankingProfile.AI_RECOMMEND_DEFAULT
                ? Math.max(0, 320 - diff / 4.0d)
                : Math.max(0, 180 - diff / 8.0d);
        if (profile == HouseRankingProfile.AI_RECOMMEND_DEFAULT
                && Boolean.TRUE.equals(query == null ? null : query.budgetRelaxable())
                && housePriceYuan > targetBudgetYuan) {
            return baseScore + 25.0d;
        }
        return baseScore;
    }

    private double buildRentModeMatchScore(House house, HouseRankQuery query) {
        if (query == null || query.rentMode() == null || house.getRentType() == null) {
            return 0;
        }
        return query.rentMode().equals(String.valueOf(house.getRentType())) ? 60.0d : 0;
    }

    private double buildFeatureScore(boolean expected, boolean actual, double matchScore) {
        if (!expected) {
            return 0;
        }
        return actual ? matchScore : 0;
    }

    private double buildWeightedPreferenceScore(House house, HouseRankQuery query, int weightValue, double score) {
        if (query == null || query.preferenceWeightMap() == null || query.preferenceWeightMap().isEmpty()) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<String, Integer> entry : query.preferenceWeightMap().entrySet()) {
            if (entry.getValue() == null || entry.getValue() != weightValue) {
                continue;
            }
            if (matchesPreference(house, entry.getKey())) {
                total += score;
            }
        }
        return total;
    }

    private double buildRelaxationAcceptanceScore(HouseRankQuery query, HouseRecallEvidence evidence) {
        if (!Boolean.TRUE.equals(query == null ? null : query.budgetRelaxable())) {
            return 0;
        }
        return evidence != null && evidence.relaxedBudgetApplied() ? 20.0d : 0;
    }

    private double buildRelaxationAdjustment(HouseRecallEvidence evidence) {
        if (evidence == null) {
            return 0;
        }
        double adjustment = 0;
        if (evidence.relaxedBudgetApplied()) {
            adjustment -= 10;
        }
        if (evidence.relaxedRadiusApplied()) {
            adjustment -= 25;
        }
        return adjustment;
    }

    private double buildFreshnessScore(House house) {
        if (house.getCreateTime() == null) {
            return 0;
        }
        long ageDays = Math.max(0, Duration.between(house.getCreateTime(), LocalDateTime.now(clock)).toDays());
        return Math.max(0, 30 - ageDays);
    }

    private List<HouseReasonCode> buildReasonCodes(House house,
                                                   HouseRankQuery query,
                                                   HouseRecallEvidence evidence,
                                                   HouseScoreBreakdown scoreBreakdown) {
        List<HouseReasonCode> reasonCodes = new ArrayList<>();
        if (evidence != null && evidence.locationMatched()) {
            reasonCodes.add(HouseReasonCode.RECALL_LOCATION_MATCH);
        }
        if (evidence != null && evidence.textMatched()) {
            reasonCodes.add(HouseReasonCode.RECALL_TEXT_MATCH);
        }
        if (scoreBreakdown.locationDistanceScore() > 0) {
            reasonCodes.add(HouseReasonCode.LOCATION_DISTANCE_ADVANTAGE);
        }
        if (scoreBreakdown.textRelevanceScore() > 0) {
            reasonCodes.add(HouseReasonCode.TEXT_RELEVANCE_ADVANTAGE);
        }
        if (isBudgetCloseMatch(house, query, scoreBreakdown)) {
            reasonCodes.add(HouseReasonCode.BUDGET_CLOSE_MATCH);
        }
        if (scoreBreakdown.rentModeMatchScore() > 0) {
            reasonCodes.add(HouseReasonCode.RENT_MODE_MATCH);
        }
        if (Boolean.TRUE.equals(query == null ? null : query.nearSubway()) && isEnabled(house.getNearSubway())) {
            reasonCodes.add(HouseReasonCode.NEAR_SUBWAY_MATCH);
        }
        if (Boolean.TRUE.equals(query == null ? null : query.privateBathroom()) && isEnabled(house.getPrivateBathroom())) {
            reasonCodes.add(HouseReasonCode.PRIVATE_BATHROOM_MATCH);
        }
        if (Boolean.TRUE.equals(query == null ? null : query.hasBalcony()) && isEnabled(house.getHasBalcony())) {
            reasonCodes.add(HouseReasonCode.HAS_BALCONY_MATCH);
        }
        if (Boolean.TRUE.equals(query == null ? null : query.civilWaterElectric()) && isEnabled(house.getCivilWaterElectric())) {
            reasonCodes.add(HouseReasonCode.CIVIL_WATER_ELECTRIC_MATCH);
        }
        if (Boolean.TRUE.equals(query == null ? null : query.supportStudentDepositFree()) && isEnabled(house.getSupportStudentDepositFree())) {
            reasonCodes.add(HouseReasonCode.SUPPORT_STUDENT_DEPOSIT_FREE_MATCH);
        }
        if (scoreBreakdown.primaryPreferenceScore() > 0) {
            reasonCodes.add(HouseReasonCode.PRIMARY_PREFERENCE_MATCH);
        }
        if (scoreBreakdown.secondaryPreferenceScore() > 0) {
            reasonCodes.add(HouseReasonCode.SECONDARY_PREFERENCE_MATCH);
        }
        if (scoreBreakdown.relaxationAcceptanceScore() > 0) {
            reasonCodes.add(HouseReasonCode.BUDGET_RELAXED_ACCEPTED);
        }
        if (evidence != null && evidence.relaxedBudgetApplied()) {
            reasonCodes.add(HouseReasonCode.RELAXED_BUDGET_APPLIED);
        }
        if (evidence != null && evidence.relaxedRadiusApplied()) {
            reasonCodes.add(HouseReasonCode.RELAXED_RADIUS_APPLIED);
        }
        if (scoreBreakdown.freshnessScore() >= 20) {
            reasonCodes.add(HouseReasonCode.FRESH_LISTING);
        }
        return reasonCodes;
    }

    private List<HouseRankedItem> slicePage(List<HouseRankedItem> rankedItems, int page, int size) {
        int fromIndex = Math.max((page - 1) * size, 0);
        if (fromIndex >= rankedItems.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + size, rankedItems.size());
        return rankedItems.subList(fromIndex, toIndex);
    }

    private Integer resolveTargetBudgetYuan(HouseRankQuery query) {
        if (query == null) {
            return null;
        }
        if (query.budgetYuan() != null) {
            return query.budgetYuan();
        }
        if (query.minPriceYuan() != null && query.maxPriceYuan() != null) {
            return (query.minPriceYuan() + query.maxPriceYuan()) / 2;
        }
        if (query.maxPriceYuan() != null) {
            return query.maxPriceYuan();
        }
        return query.minPriceYuan();
    }

    private boolean isBudgetCloseMatch(House house, HouseRankQuery query, HouseScoreBreakdown scoreBreakdown) {
        Integer targetBudgetYuan = resolveTargetBudgetYuan(query);
        if (targetBudgetYuan == null || house.getPrice() == null) {
            return false;
        }
        int housePriceYuan = Math.max(house.getPrice(), 0) / 100;
        int diff = Math.abs(housePriceYuan - targetBudgetYuan);
        return diff <= BUDGET_REASON_MAX_DIFF_YUAN && scoreBreakdown.budgetCloseScore() >= BUDGET_REASON_MIN_SCORE;
    }

    private int resolvePage(HouseRankQuery query) {
        return query == null || query.page() == null ? DEFAULT_PAGE : Math.max(query.page(), 1);
    }

    private int resolveSize(HouseRankQuery query) {
        return query == null || query.size() == null ? DEFAULT_SIZE : Math.max(query.size(), 1);
    }

    private boolean shouldKeepCurrentCandidatePage(HouseRankQuery query) {
        return query != null && query.recallAlreadyPaged();
    }

    private boolean isEnabled(Integer value) {
        return value != null && value == 1;
    }

    private boolean matchesPreference(House house, String preferenceKey) {
        if (house == null || preferenceKey == null) {
            return false;
        }
        return switch (preferenceKey) {
            case "nearSubway" -> isEnabled(house.getNearSubway());
            case "privateBathroom" -> isEnabled(house.getPrivateBathroom());
            case "hasBalcony" -> isEnabled(house.getHasBalcony());
            case "civilWaterElectric" -> isEnabled(house.getCivilWaterElectric());
            case "supportStudentDepositFree" -> isEnabled(house.getSupportStudentDepositFree());
            default -> false;
        };
    }
}
