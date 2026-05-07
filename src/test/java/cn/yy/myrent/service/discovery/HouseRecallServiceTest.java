package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseRecallServiceTest {

    @Test
    void recallResultShouldRetainEvidenceAndExposeImmutableCopiedCandidates() {
        House house = new House();
        house.setId(101L);
        house.setTitle("contract-house");

        HouseRecallEvidence evidence = HouseRecallEvidence.builder()
                .locationMatched(true)
                .textMatched(true)
                .locationDistanceMeters(620.0d)
                .locationRank(2)
                .textRank(5)
                .textScore(8.4f)
                .exactConstraintMatched(true)
                .relaxedBudgetApplied(false)
                .relaxedRadiusApplied(true)
                .nearSubwayMatched(true)
                .privateBathroomMatched(true)
                .hasBalconyMatched(false)
                .civilWaterElectricMatched(true)
                .supportStudentDepositFreeMatched(false)
                .build();

        HouseRecallCandidate candidate = new HouseRecallCandidate(
                house,
                HouseRecallMatchTier.RELAXED_RADIUS,
                evidence
        );

        List<HouseRecallCandidate> mutableCandidates = new ArrayList<>();
        mutableCandidates.add(candidate);

        HouseRecallResult result = new HouseRecallResult(mutableCandidates, true, false);
        mutableCandidates.clear();

        assertEquals(1, result.candidates().size());
        assertEquals(101L, result.candidates().get(0).house().getId());
        assertEquals("contract-house", result.candidates().get(0).house().getTitle());
        assertEquals(HouseRecallMatchTier.RELAXED_RADIUS, result.candidates().get(0).matchTier());
        assertTrue(result.candidates().get(0).recallEvidence().locationMatched());
        assertTrue(result.candidates().get(0).recallEvidence().textMatched());
        assertTrue(result.candidates().get(0).recallEvidence().privateBathroomMatched());
        assertTrue(result.candidates().get(0).recallEvidence().relaxedRadiusApplied());
        assertEquals(620.0d, result.candidates().get(0).recallEvidence().locationDistanceMeters());
        assertEquals(2, result.candidates().get(0).recallEvidence().locationRank());
        assertEquals(5, result.candidates().get(0).recallEvidence().textRank());
        assertEquals(8.4f, result.candidates().get(0).recallEvidence().textScore());
        assertThrows(UnsupportedOperationException.class, () -> result.candidates().add(candidate));
    }

    @Test
    void recallQueryShouldSupportKeywordFilterRecommendationAndNullableOptionals() {
        HouseRecallQuery recommendationQuery = HouseRecallQuery.builder()
                .keyword("garden whole rent")
                .locationName("garden")
                .city("shanghai")
                .region("huangpu")
                .minPriceYuan(3000)
                .maxPriceYuan(4000)
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .rentMode("WHOLE")
                .nearSubway(true)
                .privateBathroom(true)
                .page(1)
                .size(10)
                .recallProfile(HouseRecallProfile.AI_RECOMMEND)
                .build();

        HouseRecallQuery filterOnlyQuery = HouseRecallQuery.builder()
                .city("guangzhou")
                .region("tianhe")
                .minPriceYuan(1500)
                .maxPriceYuan(3500)
                .page(2)
                .size(20)
                .recallProfile(HouseRecallProfile.LIST_FILTER)
                .build();

        assertEquals("garden whole rent", recommendationQuery.keyword());
        assertEquals("garden", recommendationQuery.locationName());
        assertEquals(HouseRecallProfile.AI_RECOMMEND, recommendationQuery.recallProfile());
        assertEquals(3000, recommendationQuery.minPriceYuan());
        assertEquals(4000, recommendationQuery.maxPriceYuan());
        assertTrue(recommendationQuery.nearSubway());
        assertTrue(recommendationQuery.privateBathroom());
        assertNull(filterOnlyQuery.keyword());
        assertNull(filterOnlyQuery.locationName());
        assertNull(filterOnlyQuery.budgetYuan());
        assertNull(filterOnlyQuery.budgetScope());
        assertNull(filterOnlyQuery.rentMode());
        assertNull(filterOnlyQuery.rentType());
        assertNull(filterOnlyQuery.nearSubway());
        assertNull(filterOnlyQuery.privateBathroom());
        assertNull(filterOnlyQuery.hasBalcony());
        assertNull(filterOnlyQuery.civilWaterElectric());
        assertNull(filterOnlyQuery.supportStudentDepositFree());
        assertEquals(1500, filterOnlyQuery.minPriceYuan());
        assertEquals(3500, filterOnlyQuery.maxPriceYuan());
        assertEquals(2, filterOnlyQuery.page());
        assertEquals(20, filterOnlyQuery.size());
        assertEquals(HouseRecallProfile.LIST_FILTER, filterOnlyQuery.recallProfile());
    }

    @Test
    void recallEvidenceShouldPreserveExplicitValuesAndExposeBuilderDefaults() {
        HouseRecallEvidence explicitEvidence = HouseRecallEvidence.builder()
                .textMatched(true)
                .textRank(1)
                .textScore(3.5f)
                .build();

        HouseRecallEvidence defaultEvidence = HouseRecallEvidence.builder().build();

        assertTrue(explicitEvidence.textMatched());
        assertEquals(1, explicitEvidence.textRank());
        assertEquals(3.5f, explicitEvidence.textScore());
        assertFalse(defaultEvidence.locationMatched());
        assertFalse(defaultEvidence.textMatched());
        assertFalse(defaultEvidence.exactConstraintMatched());
        assertFalse(defaultEvidence.relaxedBudgetApplied());
        assertFalse(defaultEvidence.relaxedRadiusApplied());
        assertFalse(defaultEvidence.nearSubwayMatched());
        assertFalse(defaultEvidence.privateBathroomMatched());
        assertFalse(defaultEvidence.hasBalconyMatched());
        assertFalse(defaultEvidence.civilWaterElectricMatched());
        assertFalse(defaultEvidence.supportStudentDepositFreeMatched());
        assertNull(defaultEvidence.locationDistanceMeters());
        assertNull(defaultEvidence.locationRank());
        assertNull(defaultEvidence.textRank());
        assertNull(defaultEvidence.textScore());
    }

    @Test
    void recallResultShouldNormalizeNullCandidatesToEmptyList() {
        HouseRecallResult result = new HouseRecallResult(null, false, true);

        assertTrue(result.candidates().isEmpty());
        assertFalse(result.esAvailable());
        assertTrue(result.degraded());
        assertThrows(UnsupportedOperationException.class, () -> result.candidates().add(null));
    }
}
