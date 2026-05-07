package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.discovery.HouseRankQuery;
import cn.yy.myrent.service.discovery.HouseRankResult;
import cn.yy.myrent.service.discovery.HouseRankedItem;
import cn.yy.myrent.service.discovery.HouseRankingProfile;
import cn.yy.myrent.service.discovery.HouseRankingService;
import cn.yy.myrent.service.discovery.HouseReasonCode;
import cn.yy.myrent.service.discovery.HouseRecallCandidate;
import cn.yy.myrent.service.discovery.HouseRecallEvidence;
import cn.yy.myrent.service.discovery.HouseRecallMatchTier;
import cn.yy.myrent.service.discovery.HouseRecallProfile;
import cn.yy.myrent.service.discovery.HouseRecallQuery;
import cn.yy.myrent.service.discovery.HouseRecallResult;
import cn.yy.myrent.service.discovery.HouseRecallService;
import cn.yy.myrent.vo.AiPreviewGroupVO;
import cn.yy.myrent.vo.AiPreviewVO;
import cn.yy.myrent.vo.AiRecommendChatVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRecommendServiceTest {

    @Mock
    private AiRecommendDecisionClient decisionClient;
    @Mock
    private AiRecommendStateStore stateStore;
    @Mock
    private AiPreviewService previewService;
    @Mock
    private HouseRecallService houseRecallService;
    @Mock
    private HouseRankingService houseRankingService;
    @Mock
    private AiRecommendRankingPayloadBuilder rankingPayloadBuilder;

    private AiRecommendServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiRecommendServiceImpl(
                decisionClient,
                stateStore,
                new AiRecommendSummaryBuilder(),
                previewService,
                houseRecallService,
                houseRankingService,
                rankingPayloadBuilder,
                30,
                6,
                "RENT_ONLY"
        );
    }

    @Test
    void searchReadyPathShouldUseSharedRecallAndRankingInsteadOfSmartGuide() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("search now")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(3500)
                                .budgetScope("TOTAL")
                                .rentMode("WHOLE")
                                .preferences(List.of("nearSubway"))
                                .build())
                        .build());

        House house = house(101L, "Pudong whole rent");
        HouseRecallCandidate candidate = new HouseRecallCandidate(
                house,
                HouseRecallMatchTier.EXACT,
                HouseRecallEvidence.builder()
                        .exactConstraintMatched(true)
                        .nearSubwayMatched(true)
                        .locationDistanceMeters(1200d)
                        .build()
        );
        HouseRankResult rankResult = new HouseRankResult(
                List.of(new HouseRankedItem(house, 98.5d, null, List.of(HouseReasonCode.BUDGET_CLOSE_MATCH, HouseReasonCode.RENT_MODE_MATCH))),
                List.of(new HouseRankedItem(house, 98.5d, null, List.of(HouseReasonCode.BUDGET_CLOSE_MATCH, HouseReasonCode.RENT_MODE_MATCH))),
                1
        );
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(candidate), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class))).thenReturn(rankResult);
        when(rankingPayloadBuilder.build(any(AiRecommendSlots.class), any(HouseRankResult.class)))
                .thenReturn(AiRecommendRankingPayload.builder()
                        .summary("grounded-summary")
                        .topListings(List.of(AiRecommendRankingPayload.ListingPayload.builder()
                                .title("Pudong whole rent")
                                .build()))
                        .build());

        AiRecommendChatVO result = service.chat(1001L, req("budget 3500, whole rent in Pudong"));

        assertEquals("SEARCH", result.getStage());
        assertNotNull(result.getRecommendation());
        assertNotNull(result.getRecommendation().getRecommendations());
        assertEquals(1, result.getRecommendation().getRecommendations().size());

        ArgumentCaptor<HouseRecallQuery> recallCaptor = ArgumentCaptor.forClass(HouseRecallQuery.class);
        verify(houseRecallService).recall(recallCaptor.capture());
        assertEquals(HouseRecallProfile.AI_RECOMMEND, recallCaptor.getValue().recallProfile());
        assertEquals("Pudong", recallCaptor.getValue().locationName());
        assertEquals(Integer.valueOf(3500), recallCaptor.getValue().budgetYuan());
        assertEquals("TOTAL", recallCaptor.getValue().budgetScope());
        assertEquals("WHOLE", recallCaptor.getValue().rentMode());
        assertTrue(Boolean.TRUE.equals(recallCaptor.getValue().nearSubway()));

        ArgumentCaptor<HouseRankQuery> rankCaptor = ArgumentCaptor.forClass(HouseRankQuery.class);
        verify(houseRankingService).rank(any(), rankCaptor.capture());
        assertEquals(HouseRankingProfile.AI_RECOMMEND_DEFAULT, rankCaptor.getValue().rankingProfile());
        assertEquals("1", rankCaptor.getValue().rentMode());
        verify(rankingPayloadBuilder).build(any(AiRecommendSlots.class), any(HouseRankResult.class));
        assertTrue(result.getAssistantReply().contains("grounded-summary"));
        assertEquals("1.200", result.getRecommendation().getRecommendations().get(0).getDistanceToMetroKm().toPlainString());
        assertEquals(11, result.getRecommendation().getRecommendations().get(0).getEstimatedCommuteMinutes());
    }

    @Test
    void recommendationShouldStillBeReturnedWhenPayloadBuilderFails() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("search now")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(3600)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());
        House house = house(201L, "fallback listing");
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(new HouseRecallCandidate(
                        house, HouseRecallMatchTier.EXACT, HouseRecallEvidence.builder().exactConstraintMatched(true).build()
                )), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(new HouseRankedItem(house, 88.0d, null, List.of(HouseReasonCode.RENT_MODE_MATCH))),
                        List.of(new HouseRankedItem(house, 88.0d, null, List.of(HouseReasonCode.RENT_MODE_MATCH))),
                        1
                ));
        when(rankingPayloadBuilder.build(any(AiRecommendSlots.class), any(HouseRankResult.class)))
                .thenThrow(new RuntimeException("payload failed"));

        AiRecommendChatVO result = service.chat(1001L, req("whole rent in Pudong, budget 3600"));

        assertEquals("SEARCH", result.getStage());
        assertNotNull(result.getRecommendation());
        assertEquals(1, result.getRecommendation().getRecommendations().size());
        assertTrue(result.getAssistantReply().contains("fallback listing"));
    }

    @Test
    void searchWithNoListingsShouldUseBackendEmptyResultReply() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("已经为你筛出三套合适房源")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(3600)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(List.of(), List.of(), 0));
        when(rankingPayloadBuilder.build(any(AiRecommendSlots.class), any(HouseRankResult.class)))
                .thenReturn(AiRecommendRankingPayload.builder()
                        .summary("已完成排序")
                        .topListings(List.of())
                        .sharedReasonHighlights(List.of())
                        .build());

        AiRecommendChatVO result = service.chat(1001L, req("whole rent in Pudong, budget 3600"));

        assertEquals("SEARCH", result.getStage());
        assertNotNull(result.getRecommendation());
        assertTrue(result.getRecommendation().getRecommendations().isEmpty());
        assertTrue(result.getAssistantReply().contains("当前没有符合条件的真实房源"));
        assertTrue(result.getAssistantReply().contains("放宽预算"));
    }

    @Test
    void rankingFailureShouldFallbackSafely() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("search now")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(3500)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(new HouseRecallCandidate(
                        house(301L, "candidate"), HouseRecallMatchTier.EXACT, HouseRecallEvidence.builder().build()
                )), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenThrow(new RuntimeException("ranking unavailable"));
        AiPreviewVO preview = new AiPreviewVO();
        preview.setLocationName("Pudong");
        preview.setGroups(List.of(new AiPreviewGroupVO()));
        when(previewService.build("Pudong", 3500, "RENT_ONLY", "WHOLE")).thenReturn(preview);

        AiRecommendChatVO result = service.chat(1001L, req("whole rent in Pudong, budget 3500"));

        assertEquals("REFINE", result.getStage());
        assertNotNull(result.getPreview());
        assertNull(result.getRecommendation());
        assertNotNull(result.getAssistantReply());
    }

    @Test
    void getOrCreateSessionShouldStillReturnOpeningShape() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));

        AiRecommendChatVO result = service.getOrCreateSession(1001L);

        assertEquals("ai-u1001", result.getSessionId());
        assertEquals("ASK", result.getStage());
        assertNotNull(result.getAssistantReply());
        assertEquals("RENT_ONLY", result.getSlots().getBudgetScope());
    }

    @Test
    void chatShouldKeepPlainSecondaryPreferenceMediumWhenFallbackNormalizationHasNoSofterSignal() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("先按这些条件继续找")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(4500)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .priority("COMMUTE")
                                .preferences(List.of("nearSubway", "hasBalcony"))
                                .budgetRelaxable(true)
                                .build())
                        .build());
        House house = house(401L, "weighted listing");
        house.setNearSubway(1);
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(new HouseRecallCandidate(
                        house,
                        HouseRecallMatchTier.EXACT,
                        HouseRecallEvidence.builder()
                                .exactConstraintMatched(true)
                                .nearSubwayMatched(true)
                                .relaxedBudgetApplied(true)
                                .build()
                )), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(new HouseRankedItem(
                                house,
                                99.0d,
                                null,
                                List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH, HouseReasonCode.BUDGET_RELAXED_ACCEPTED)
                        )),
                        List.of(new HouseRankedItem(
                                house,
                                99.0d,
                                null,
                                List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH, HouseReasonCode.BUDGET_RELAXED_ACCEPTED)
                        )),
                        1
                ));
        when(rankingPayloadBuilder.build(any(AiRecommendSlots.class), any(HouseRankResult.class)))
                .thenReturn(AiRecommendRankingPayload.builder()
                        .summary("weighted-summary")
                        .build());

        service.chat(1001L, req("我更在意通勤，预算可以稍微放一点，阳台最好有"));

        ArgumentCaptor<AiRecommendSessionState> stateCaptor = ArgumentCaptor.forClass(AiRecommendSessionState.class);
        verify(stateStore).save(stateCaptor.capture());
        AiRecommendSlots savedSlots = stateCaptor.getValue().getSlots();
        assertTrue(Boolean.TRUE.equals(savedSlots.getBudgetRelaxable()));
        assertNotNull(savedSlots.getWeightedPreferences());
        assertEquals(2, savedSlots.getWeightedPreferences().size());
        assertEquals("nearSubway", savedSlots.getWeightedPreferences().get(0).getPreferenceKey());
        assertEquals(AiPreferenceWeightLevel.HIGH, savedSlots.getWeightedPreferences().get(0).getWeightLevel());
        assertEquals("hasBalcony", savedSlots.getWeightedPreferences().get(1).getPreferenceKey());
        assertEquals(AiPreferenceWeightLevel.MEDIUM, savedSlots.getWeightedPreferences().get(1).getWeightLevel());
        assertTrue(stateCaptor.getValue().getSummary().contains("budgetRelaxable=true"));
        assertTrue(stateCaptor.getValue().getSummary().contains("weightedPreferences=["));
    }

    @Test
    void chatShouldIgnoreFutureSchemeThreePlaceholderFieldsInPersistedState() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("search now")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(4500)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .priority("COMMUTE")
                                .preferences(List.of("nearSubway"))
                                .budgetRelaxable(true)
                                .budgetRelaxLimitYuan(5200)
                                .tradeoffReason("accept longer commute for balcony")
                                .build())
                        .build());
        House house = house(402L, "placeholder listing");
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(new HouseRecallCandidate(
                        house,
                        HouseRecallMatchTier.EXACT,
                        HouseRecallEvidence.builder().exactConstraintMatched(true).build()
                )), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(new HouseRankedItem(house, 90.0d, null, List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH))),
                        List.of(new HouseRankedItem(house, 90.0d, null, List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH))),
                        1
                ));
        when(rankingPayloadBuilder.build(any(AiRecommendSlots.class), any(HouseRankResult.class)))
                .thenReturn(AiRecommendRankingPayload.builder().summary("summary").build());

        service.chat(1001L, req("commute first, whole rent in Pudong"));

        ArgumentCaptor<AiRecommendSessionState> stateCaptor = ArgumentCaptor.forClass(AiRecommendSessionState.class);
        verify(stateStore).save(stateCaptor.capture());
        AiRecommendSlots savedSlots = stateCaptor.getValue().getSlots();
        assertNull(savedSlots.getBudgetRelaxLimitYuan());
        assertNull(savedSlots.getTradeoffReason());
        assertFalse(stateCaptor.getValue().getSummary().contains("budgetRelaxLimitYuan="));
        assertFalse(stateCaptor.getValue().getSummary().contains("tradeoffReason="));
    }

    @Test
    void chatShouldNotUseLowWeightedPreferenceAsRecallConstraint() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("search now")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(4300)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .weightedPreferences(List.of(
                                        AiWeightedPreference.builder()
                                                .preferenceKey("nearSubway")
                                                .weightLevel(AiPreferenceWeightLevel.HIGH)
                                                .build(),
                                        AiWeightedPreference.builder()
                                                .preferenceKey("hasBalcony")
                                                .weightLevel(AiPreferenceWeightLevel.LOW)
                                                .relaxable(true)
                                                .build()
                                ))
                                .build())
                        .build());
        House house = house(403L, "recall listing");
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(new HouseRecallCandidate(
                        house,
                        HouseRecallMatchTier.EXACT,
                        HouseRecallEvidence.builder().exactConstraintMatched(true).build()
                )), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(new HouseRankedItem(house, 91.0d, null, List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH))),
                        List.of(new HouseRankedItem(house, 91.0d, null, List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH))),
                        1
                ));
        when(rankingPayloadBuilder.build(any(AiRecommendSlots.class), any(HouseRankResult.class)))
                .thenReturn(AiRecommendRankingPayload.builder().summary("summary").build());

        service.chat(1001L, req("near subway matters most, balcony is only a bonus"));

        ArgumentCaptor<HouseRecallQuery> recallCaptor = ArgumentCaptor.forClass(HouseRecallQuery.class);
        verify(houseRecallService).recall(recallCaptor.capture());
        assertTrue(Boolean.TRUE.equals(recallCaptor.getValue().nearSubway()));
        assertFalse(Boolean.TRUE.equals(recallCaptor.getValue().hasBalcony()));

        ArgumentCaptor<HouseRankQuery> rankCaptor = ArgumentCaptor.forClass(HouseRankQuery.class);
        verify(houseRankingService).rank(any(), rankCaptor.capture());
        assertTrue(Boolean.TRUE.equals(rankCaptor.getValue().nearSubway()));
        assertFalse(Boolean.TRUE.equals(rankCaptor.getValue().hasBalcony()));
    }

    @Test
    void chatShouldMentionTopListingPrimaryReasonEvenWhenReasonsAreNotShared() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("search now")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(4300)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .preferences(List.of("nearSubway"))
                                .build())
                        .build());
        House house = house(404L, "reason listing");
        when(houseRecallService.recall(any(HouseRecallQuery.class)))
                .thenReturn(new HouseRecallResult(List.of(new HouseRecallCandidate(
                        house,
                        HouseRecallMatchTier.EXACT,
                        HouseRecallEvidence.builder().exactConstraintMatched(true).build()
                )), true, false));
        when(houseRankingService.rank(any(), any(HouseRankQuery.class)))
                .thenReturn(new HouseRankResult(
                        List.of(new HouseRankedItem(house, 95.0d, null, List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH))),
                        List.of(new HouseRankedItem(house, 95.0d, null, List.of(HouseReasonCode.PRIMARY_PREFERENCE_MATCH))),
                        1
                ));
        when(rankingPayloadBuilder.build(any(AiRecommendSlots.class), any(HouseRankResult.class)))
                .thenReturn(AiRecommendRankingPayload.builder()
                        .summary("排序完成")
                        .topListings(List.of(AiRecommendRankingPayload.ListingPayload.builder()
                                .title("reason listing")
                                .reasonHighlights(List.of("核心偏好优先满足"))
                                .build()))
                        .sharedReasonHighlights(List.of())
                        .build());

        AiRecommendChatVO result = service.chat(1001L, req("whole rent in Pudong"));

        assertTrue(result.getAssistantReply().contains("核心偏好优先满足"));
    }

    @Test
    void rankingPayloadBuilderShouldIgnoreFutureSchemeThreeTradeoffSummary() {
        AiRecommendRankingPayloadBuilder payloadBuilder = new AiRecommendRankingPayloadBuilder();

        AiRecommendRankingPayload payload = payloadBuilder.build(
                AiRecommendSlots.builder()
                        .city("Shanghai")
                        .locationName("Pudong")
                        .tradeoffReason("accept longer commute for balcony")
                        .build(),
                new HouseRankResult(List.of(), List.of(), 0)
        );

        assertNull(payload.getTradeoffSummary());
        assertFalse(payload.getSummary().contains("accept longer commute for balcony"));
    }

    private AiRecommendChatReqDTO req(String message) {
        AiRecommendChatReqDTO req = new AiRecommendChatReqDTO();
        req.setMessage(message);
        return req;
    }

    private House house(Long id, String title) {
        House house = new House();
        house.setId(id);
        house.setPublisherUserId(900L + id);
        house.setTitle(title);
        house.setStatus(1);
        house.setPrice(320000);
        house.setDepositAmount(0);
        house.setTotalCost(320000);
        return house;
    }
}
