package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseRankingServiceTest {

    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), TEST_ZONE);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 5, 6, 20, 0);

    private final HouseRankingService houseRankingService = new HouseRankingServiceImpl(FIXED_CLOCK);

    @Test
    void aiRecommendDefaultShouldPreferBudgetCloserCandidateOverSlightlyNearerExpensiveOne() {
        House closerButExpensive = house(1L, 4200, LocalDateTime.of(2026, 4, 20, 10, 0));
        House budgetCloser = house(2L, 3100, LocalDateTime.of(2026, 4, 19, 10, 0));

        HouseRankResult result = houseRankingService.rank(
                List.of(
                        new HouseRecallCandidate(
                                closerButExpensive,
                                HouseRecallMatchTier.EXACT,
                                HouseRecallEvidence.builder()
                                        .locationMatched(true)
                                        .locationDistanceMeters(100.0d)
                                        .build()
                        ),
                        new HouseRecallCandidate(
                                budgetCloser,
                                HouseRecallMatchTier.EXACT,
                                HouseRecallEvidence.builder()
                                        .locationMatched(true)
                                        .locationDistanceMeters(180.0d)
                                        .build()
                        )
                ),
                HouseRankQuery.builder()
                        .budgetYuan(3000)
                        .page(1)
                        .size(10)
                        .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                        .build()
        );

        assertEquals(2L, result.currentPageItems().get(0).house().getId());
        assertTrue(result.currentPageItems().get(0).scoreBreakdown().budgetCloseScore()
                > result.currentPageItems().get(1).scoreBreakdown().budgetCloseScore());
    }

    @Test
    void searchDefaultShouldProduceBalancedOrderingAndEmitReasonCodes() {
        House bothMatched = house(11L, 3000, FIXED_NOW.minusDays(1));
        bothMatched.setNearSubway(1);
        bothMatched.setPrivateBathroom(1);

        House locationOnly = house(12L, 2800, FIXED_NOW.minusDays(3));
        locationOnly.setNearSubway(1);

        HouseRankResult result = houseRankingService.rank(
                List.of(
                        new HouseRecallCandidate(
                                locationOnly,
                                HouseRecallMatchTier.LOCATION_ONLY,
                                HouseRecallEvidence.builder()
                                        .locationMatched(true)
                                        .locationDistanceMeters(80.0d)
                                        .build()
                        ),
                        new HouseRecallCandidate(
                                bothMatched,
                                HouseRecallMatchTier.EXACT,
                                HouseRecallEvidence.builder()
                                        .locationMatched(true)
                                        .textMatched(true)
                                        .locationDistanceMeters(220.0d)
                                        .textRank(0)
                                        .textScore(2.2f)
                                        .build()
                        )
                ),
                HouseRankQuery.builder()
                        .budgetYuan(3000)
                        .nearSubway(true)
                        .privateBathroom(true)
                        .page(1)
                        .size(10)
                        .rankingProfile(HouseRankingProfile.SEARCH_DEFAULT)
                        .build()
        );

        HouseRankedItem first = result.currentPageItems().get(0);
        assertEquals(11L, first.house().getId());
        assertTrue(first.reasonCodes().contains(HouseReasonCode.RECALL_LOCATION_MATCH));
        assertTrue(first.reasonCodes().contains(HouseReasonCode.RECALL_TEXT_MATCH));
        assertTrue(first.reasonCodes().contains(HouseReasonCode.BUDGET_CLOSE_MATCH));
        assertTrue(first.reasonCodes().contains(HouseReasonCode.NEAR_SUBWAY_MATCH));
        assertTrue(first.reasonCodes().contains(HouseReasonCode.PRIVATE_BATHROOM_MATCH));
    }

    @Test
    void searchDefaultShouldNotEmitBudgetReasonWhenBudgetIsNotActuallyClose() {
        House farFromBudget = house(21L, 3600, FIXED_NOW.minusDays(2));

        HouseRankResult result = houseRankingService.rank(
                List.of(new HouseRecallCandidate(
                        farFromBudget,
                        HouseRecallMatchTier.FILTER_ONLY,
                        HouseRecallEvidence.builder()
                                .exactConstraintMatched(true)
                                .build()
                )),
                HouseRankQuery.builder()
                        .budgetYuan(3000)
                        .page(1)
                        .size(10)
                        .rankingProfile(HouseRankingProfile.SEARCH_DEFAULT)
                        .build()
        );

        assertFalse(result.currentPageItems().get(0).reasonCodes().contains(HouseReasonCode.BUDGET_CLOSE_MATCH));
    }

    @Test
    void aiRecommendDefaultShouldPreferHighPrimaryPreferenceOverLowSecondaryMatch() {
        House nearSubwayHouse = house(31L, 3200, FIXED_NOW.minusDays(1));
        nearSubwayHouse.setNearSubway(1);

        House balconyHouse = house(32L, 3200, FIXED_NOW.minusDays(1));
        balconyHouse.setHasBalcony(1);

        HouseRankResult result = houseRankingService.rank(
                List.of(
                        new HouseRecallCandidate(
                                balconyHouse,
                                HouseRecallMatchTier.EXACT,
                                HouseRecallEvidence.builder()
                                        .locationMatched(true)
                                        .relaxedBudgetApplied(true)
                                        .build()
                        ),
                        new HouseRecallCandidate(
                                nearSubwayHouse,
                                HouseRecallMatchTier.EXACT,
                                HouseRecallEvidence.builder()
                                        .locationMatched(true)
                                        .build()
                        )
                ),
                HouseRankQuery.builder()
                        .budgetYuan(3000)
                        .page(1)
                        .size(10)
                        .budgetRelaxable(true)
                        .preferenceWeightMap(Map.of(
                                "nearSubway", 3,
                                "hasBalcony", 1
                        ))
                        .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                        .build()
        );

        HouseRankedItem first = result.currentPageItems().get(0);
        assertEquals(31L, first.house().getId());
        assertTrue(first.reasonCodes().contains(HouseReasonCode.PRIMARY_PREFERENCE_MATCH));
        assertTrue(first.scoreBreakdown().primaryPreferenceScore()
                > result.currentPageItems().get(1).scoreBreakdown().secondaryPreferenceScore());
        assertTrue(result.currentPageItems().get(1).reasonCodes().contains(HouseReasonCode.SECONDARY_PREFERENCE_MATCH));
        assertTrue(result.currentPageItems().get(1).reasonCodes().contains(HouseReasonCode.BUDGET_RELAXED_ACCEPTED));
    }

    @Test
    void aiRecommendDefaultShouldAccumulateMultipleHighPreferenceMatches() {
        House doubleMatchHouse = house(41L, 3200, FIXED_NOW.minusDays(1));
        doubleMatchHouse.setNearSubway(1);
        doubleMatchHouse.setPrivateBathroom(1);

        House singleMatchHouse = house(42L, 3200, FIXED_NOW.minusDays(1));
        singleMatchHouse.setNearSubway(1);

        HouseRankResult result = houseRankingService.rank(
                List.of(
                        new HouseRecallCandidate(
                                singleMatchHouse,
                                HouseRecallMatchTier.EXACT,
                                HouseRecallEvidence.builder().locationMatched(true).build()
                        ),
                        new HouseRecallCandidate(
                                doubleMatchHouse,
                                HouseRecallMatchTier.EXACT,
                                HouseRecallEvidence.builder().locationMatched(true).build()
                        )
                ),
                HouseRankQuery.builder()
                        .budgetYuan(3000)
                        .page(1)
                        .size(10)
                        .preferenceWeightMap(Map.of(
                                "nearSubway", 3,
                                "privateBathroom", 3
                        ))
                        .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                        .build()
        );

        assertEquals(41L, result.currentPageItems().get(0).house().getId());
        assertTrue(result.currentPageItems().get(0).scoreBreakdown().primaryPreferenceScore()
                > result.currentPageItems().get(1).scoreBreakdown().primaryPreferenceScore());
    }

    private House house(Long id, int priceYuan, LocalDateTime createTime) {
        House house = new House();
        house.setId(id);
        house.setTitle("house-" + id);
        house.setPrice(priceYuan * 100);
        house.setStatus(1);
        house.setCreateTime(createTime);
        return house;
    }
}
