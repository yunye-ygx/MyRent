package cn.yy.myrent.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRecommendSummaryBuilderTest {

    private final AiRecommendSummaryBuilder builder = new AiRecommendSummaryBuilder();

    @Test
    void shouldBuildCompactSummaryFromSlotsAndMissingFields() {
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .city("Shanghai")
                .locationName("Yuyuan")
                .budgetYuan(3500)
                .rentMode("WHOLE")
                .priority("COMMUTE")
                .preferences(List.of("near subway"))
                .build();

        String summary = builder.build(slots, List.of());

        assertTrue(summary.contains("Shanghai"));
        assertTrue(summary.contains("3500"));
        assertTrue(summary.contains("WHOLE"));
        assertTrue(summary.contains("near subway"));
    }

    @Test
    void shouldMentionMissingRequiredSlotsWhenSearchIsNotReady() {
        String summary = builder.build(
                AiRecommendSlots.builder().city("Shanghai").build(),
                List.of("budgetYuan", "rentMode", "locationName")
        );

        assertTrue(summary.contains("budgetYuan"));
        assertTrue(summary.contains("rentMode"));
        assertTrue(summary.contains("locationName"));
    }
}
