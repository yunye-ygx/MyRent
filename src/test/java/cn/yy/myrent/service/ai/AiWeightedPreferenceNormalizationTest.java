package cn.yy.myrent.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiWeightedPreferenceNormalizationTest {

    private AiRecommendServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiRecommendServiceImpl(
                null,
                null,
                new AiRecommendSummaryBuilder(),
                null,
                null,
                null,
                null,
                30,
                6,
                "RENT_ONLY"
        );
    }

    @Test
    void normalizeWeightedPreferencesShouldPromoteCommuteWithoutDowngradingPlainSecondaryPreference() {
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .priority("COMMUTE")
                .preferences(List.of("nearSubway", "hasBalcony"))
                .budgetRelaxable(true)
                .build();

        List<AiWeightedPreference> normalized = service.normalizeWeightedPreferences(slots);

        assertNotNull(normalized);
        assertEquals(2, normalized.size());
        assertEquals("nearSubway", normalized.get(0).getPreferenceKey());
        assertEquals(AiPreferenceWeightLevel.HIGH, normalized.get(0).getWeightLevel());
        assertFalse(normalized.get(0).isRelaxable());
        assertEquals("hasBalcony", normalized.get(1).getPreferenceKey());
        assertEquals(AiPreferenceWeightLevel.MEDIUM, normalized.get(1).getWeightLevel());
        assertFalse(normalized.get(1).isRelaxable());
        assertTrue(Boolean.TRUE.equals(slots.getBudgetRelaxable()));
    }

    @Test
    void normalizeWeightedPreferencesShouldDefaultExistingPreferencesToMedium() {
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .priority("SPACE")
                .preferences(List.of("privateBathroom"))
                .build();

        List<AiWeightedPreference> normalized = service.normalizeWeightedPreferences(slots);

        assertEquals(1, normalized.size());
        assertEquals("privateBathroom", normalized.get(0).getPreferenceKey());
        assertEquals(AiPreferenceWeightLevel.MEDIUM, normalized.get(0).getWeightLevel());
        assertFalse(normalized.get(0).isRelaxable());
    }

    @Test
    void normalizeWeightedPreferencesShouldKeepExplicitNiceToHaveSignalLow() {
        AiRecommendSlots slots = AiRecommendSlots.builder()
                .priority("COMMUTE")
                .weightedPreferences(List.of(AiWeightedPreference.builder()
                        .preferenceKey("hasBalcony")
                        .weightLevel(AiPreferenceWeightLevel.LOW)
                        .relaxable(true)
                        .build()))
                .build();

        List<AiWeightedPreference> normalized = service.normalizeWeightedPreferences(slots);

        assertEquals(1, normalized.size());
        assertEquals("hasBalcony", normalized.get(0).getPreferenceKey());
        assertEquals(AiPreferenceWeightLevel.LOW, normalized.get(0).getWeightLevel());
        assertTrue(normalized.get(0).isRelaxable());
    }
}
