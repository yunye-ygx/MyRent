package cn.yy.myrent.service.ai;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseScoreBreakdown;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRecommendRankingPayloadBuilderTest {

    private final AiRecommendRankingPayloadBuilder builder = new AiRecommendRankingPayloadBuilder();

    @Test
    void shouldBuildGroundedPayloadFromSlotsScoresAndSharedReasons() {
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .city("Shanghai")
                .locationName("Pudong")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .rentMode("WHOLE")
                .priority("COMMUTE")
                .preferences(List.of("nearSubway"))
                .build();
        HouseRankResult rankResult = new HouseRankResult(
                List.of(
                        new HouseRankedItem(house(1L, "One"), 98.2d,
                                HouseScoreBreakdown.builder().budgetCloseScore(180).rentModeMatchScore(120).build(),
                                List.of(HouseReasonCode.BUDGET_CLOSE_MATCH, HouseReasonCode.RENT_MODE_MATCH)),
                        new HouseRankedItem(house(2L, "Two"), 95.7d,
                                HouseScoreBreakdown.builder().budgetCloseScore(170).nearSubwayScore(100).build(),
                                List.of(HouseReasonCode.BUDGET_CLOSE_MATCH, HouseReasonCode.NEAR_SUBWAY_MATCH))
                ),
                List.of(
                        new HouseRankedItem(house(1L, "One"), 98.2d,
                                HouseScoreBreakdown.builder().budgetCloseScore(180).rentModeMatchScore(120).build(),
                                List.of(HouseReasonCode.BUDGET_CLOSE_MATCH, HouseReasonCode.RENT_MODE_MATCH)),
                        new HouseRankedItem(house(2L, "Two"), 95.7d,
                                HouseScoreBreakdown.builder().budgetCloseScore(170).nearSubwayScore(100).build(),
                                List.of(HouseReasonCode.BUDGET_CLOSE_MATCH, HouseReasonCode.NEAR_SUBWAY_MATCH))
                ),
                2
        );

        AiRecommendRankingPayload payload = builder.build(slots, rankResult);

        assertEquals("Pudong", payload.getSlots().getLocationName());
        assertEquals(2, payload.getTopListings().size());
        assertEquals(List.of("BUDGET_CLOSE_MATCH"), payload.getSharedReasonCodes());
        assertEquals(List.of("预算贴近"), payload.getSharedReasonHighlights());
        assertEquals("One", payload.getTopListings().get(0).getTitle());
        assertTrue(payload.getTopListings().get(0).getReasonHighlights().contains("预算贴近"));
        assertNotNull(payload.getTopListings().get(0).getScoreBreakdown());
        assertEquals(180d, payload.getTopListings().get(0).getScoreBreakdown().getBudgetCloseScore());
        assertTrue(payload.getSummary().contains("目标区域是 Pudong".replace(" ", "")) || payload.getSummary().contains("目标区域是Pudong"));
        assertTrue(payload.getSummary().contains("预算贴近"));
    }

    private House house(Long id, String title) {
        House house = new House();
        house.setId(id);
        house.setTitle(title);
        return house;
    }
}
