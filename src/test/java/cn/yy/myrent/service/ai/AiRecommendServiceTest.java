package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.AiRecommendChatVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRecommendServiceTest {

    @Mock
    private AiRecommendDecisionClient decisionClient;

    @Mock
    private AiRecommendStateStore stateStore;

    @Mock
    private IHouseService houseService;

    private AiRecommendServiceImpl aiRecommendService;

    @BeforeEach
    void setUp() {
        aiRecommendService = new AiRecommendServiceImpl(
                decisionClient,
                stateStore,
                houseService,
                new AiRecommendSummaryBuilder(),
                30,
                6,
                "RENT_ONLY"
        );
    }

    @Test
    void getOrCreateSessionShouldReturnOpeningMessageForEmptySession() {
        when(stateStore.loadOrCreate(1001L)).thenReturn(AiRecommendSessionState.empty(1001L));

        AiRecommendChatVO result = aiRecommendService.getOrCreateSession(1001L);

        assertEquals("ai-u1001", result.getSessionId());
        assertEquals("ASK", result.getAction());
        assertNotNull(result.getAssistantReply());
        assertEquals("RENT_ONLY", result.getSlots().getBudgetScope());
        verify(stateStore).save(any(AiRecommendSessionState.class));
    }

    @Test
    void chatShouldHandleAskDecisionWithoutTriggeringSearch() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("Please share your budget and preferred area.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .budgetScope("RENT_ONLY")
                                .priority("COMMUTE")
                                .preferences(List.of("near subway"))
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("I want to rent in Shanghai"));

        assertEquals("ASK", result.getAction());
        assertNull(result.getRecommendation());
        assertEquals("Shanghai", result.getSlots().getCity());
        assertEquals("COMMUTE", result.getSlots().getPriority());
        assertEquals(List.of("near subway"), result.getSlots().getPreferences());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
        verify(stateStore).save(any(AiRecommendSessionState.class));
    }

    @Test
    void chatShouldHandleAdviseDecisionWithoutTriggeringSearch() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I can first compare commute and budget tradeoffs for you.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .budgetScope("RENT_ONLY")
                                .priority("PRICE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("need some advice first"));

        assertEquals("ADVISE", result.getAction());
        assertTrue(result.getAssistantReply().contains("tradeoffs"));
        assertNull(result.getRecommendation());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldTriggerSearchWhenRequiredSlotsAreReady() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I will search based on these constraints.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(3500)
                                .budgetScope("TOTAL")
                                .rentMode("WHOLE")
                                .priority("COMMUTE")
                                .preferences(List.of("near subway"))
                                .build())
                        .build());
        SmartGuideResultVO recommendation = new SmartGuideResultVO();
        recommendation.setTipMessage("matched");
        when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(recommendation);

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 3500, whole rent in Pudong"));

        assertEquals("SEARCH", result.getAction());
        assertNotNull(result.getRecommendation());
        assertEquals("matched", result.getRecommendation().getTipMessage());

        ArgumentCaptor<SmartGuideReqDTO> captor = ArgumentCaptor.forClass(SmartGuideReqDTO.class);
        verify(houseService).smartGuide(captor.capture());
        assertEquals(Integer.valueOf(3500), captor.getValue().getBudgetYuan());
        assertEquals("WHOLE", captor.getValue().getRentMode());
        assertEquals("Pudong", captor.getValue().getLocationName());
        assertEquals("TOTAL", captor.getValue().getBudgetScope());
    }

    @Test
    void chatShouldDowngradeSearchToAskWhenRequiredSlotsAreMissing() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I can search now.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .budgetYuan(3500)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 3500 whole rent"));

        assertEquals("ASK", result.getAction());
        assertNull(result.getRecommendation());
        assertNotEquals("I can search now.", result.getAssistantReply());
        assertEquals(List.of("locationName"), result.getMissingSlots());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldRecalculateMissingSlotsWhenModelOutputIsInconsistent() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("Let me整理 the constraints first.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .budgetYuan(4200)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 4200, whole rent"));

        assertEquals(List.of("locationName"), result.getMissingSlots());
    }

    @Test
    void chatShouldSendOnlyRecentSixTurnsToModelContext() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        session.setHistory(IntStream.range(0, 10)
                .mapToObj(i -> new AiRecommendTurn(i % 2 == 0 ? "user" : "assistant", "turn-" + i))
                .toList());
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenAnswer(invocation -> {
                    AiRecommendSessionState forwarded = invocation.getArgument(0);
                    assertEquals(6, forwarded.getHistory().size());
                    assertEquals("turn-5", forwarded.getHistory().get(0).getContent());
                    assertEquals("hello", forwarded.getHistory().get(5).getContent());
                    return AiRecommendDecision.builder()
                            .reply("ok")
                            .slots(AiRecommendSlots.builder().build())
                            .build();
                });

        aiRecommendService.chat(1001L, req("hello"));
    }

    @Test
    void chatShouldInferWholeRentFromUserMessageWhenModelMissesRentMode() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        session.setSlots(AiRecommendSlots.builder()
                .city("Shanghai")
                .locationName("Yuyuan")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .build());
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("Searching now.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Yuyuan")
                                .budgetYuan(3500)
                                .budgetScope("RENT_ONLY")
                                .build())
                        .build());
        SmartGuideResultVO recommendation = new SmartGuideResultVO();
        recommendation.setTipMessage("ok");
        when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(recommendation);

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("whole rent please"));

        assertEquals("SEARCH", result.getAction());
        assertNotNull(result.getRecommendation());

        ArgumentCaptor<SmartGuideReqDTO> captor = ArgumentCaptor.forClass(SmartGuideReqDTO.class);
        verify(houseService).smartGuide(captor.capture());
        assertEquals("WHOLE", captor.getValue().getRentMode());
    }

    @Test
    void chatShouldIgnoreModelActionAndUseReplyPlusSlotsOnly() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I found enough constraints. Let me search now.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(4500)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());
        SmartGuideResultVO recommendation = new SmartGuideResultVO();
        recommendation.setTipMessage("matched");
        when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(recommendation);

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("Budget 4500, whole rent in Pudong"));

        assertEquals("SEARCH", result.getAction());
        assertEquals("I found enough constraints. Let me search now.", result.getAssistantReply());
        assertNotNull(result.getRecommendation());
        verify(houseService).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldDowngradeSearchWhenBudgetIsOutOfRange() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I can search listings now.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(100)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 100, whole rent in Pudong"));

        assertEquals("ASK", result.getAction());
        assertTrue(result.getMissingSlots().contains("budgetYuan"));
        assertNotEquals("I can search listings now.", result.getAssistantReply());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldFallbackGracefullyWhenSmartGuideThrows() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I will search with these constraints.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(3500)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());
        when(houseService.smartGuide(any(SmartGuideReqDTO.class)))
                .thenThrow(new RuntimeException("smart guide unavailable"));

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 3500, whole rent in Pudong"));

        assertEquals("ADVISE", result.getAction());
        assertNull(result.getRecommendation());
        assertNotNull(result.getAssistantReply());
        verify(stateStore).save(any(AiRecommendSessionState.class));
        verify(houseService, times(1)).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldPersistSummaryAndReturnBackendGeneratedSearchAction() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I have enough information now.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Pudong")
                                .budgetYuan(4200)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .priority("COMMUTE")
                                .preferences(List.of("near subway"))
                                .build())
                        .build());
        when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(new SmartGuideResultVO());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 4200, whole rent in Pudong"));

        assertEquals("SEARCH", result.getAction());

        ArgumentCaptor<AiRecommendSessionState> stateCaptor = ArgumentCaptor.forClass(AiRecommendSessionState.class);
        verify(stateStore).save(stateCaptor.capture());
        assertTrue(stateCaptor.getValue().getSummary().contains("Shanghai"));
        assertTrue(stateCaptor.getValue().getSummary().contains("Pudong"));
        assertTrue(stateCaptor.getValue().getSummary().contains("4200"));
    }

    @Test
    void chatShouldOverrideMisleadingModelReplyWhenSearchIsNotReady() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        session.setHistory(new ArrayList<>());
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I can search listings now.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .budgetYuan(3600)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 3600 whole rent"));

        assertEquals("ASK", result.getAction());
        assertNotEquals("I can search listings now.", result.getAssistantReply());
        assertEquals(List.of("locationName"), result.getMissingSlots());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldFallbackToAskWhenDecisionClientThrows() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenThrow(new RuntimeException("ai unavailable"));

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("help me choose"));

        assertEquals("ASK", result.getAction());
        assertNull(result.getRecommendation());
        assertNotNull(result.getAssistantReply());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
        verify(stateStore).save(any(AiRecommendSessionState.class));
    }

    @Test
    void chatShouldNormalizeChineseSharedRentModeFromModelOutput() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("我已经记下来了。")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Minhang")
                                .budgetYuan(2000)
                                .budgetScope("RENT_ONLY")
                                .rentMode("合租")
                                .build())
                        .build());
        when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(new SmartGuideResultVO());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("闵行，预算2000，合租"));

        assertEquals("SEARCH", result.getAction());
        assertEquals("SHARED", result.getSlots().getRentMode());

        ArgumentCaptor<SmartGuideReqDTO> captor = ArgumentCaptor.forClass(SmartGuideReqDTO.class);
        verify(houseService).smartGuide(captor.capture());
        assertEquals("SHARED", captor.getValue().getRentMode());
    }

    @Test
    void chatShouldReturnReadableMissingSlotLabelsWhenRentModeIsMissing() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I can search now.")
                        .slots(AiRecommendSlots.builder()
                                .city("Shanghai")
                                .locationName("Minhang")
                                .budgetYuan(2200)
                                .budgetScope("RENT_ONLY")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("闵行，预算2200"));

        assertEquals("ASK", result.getAction());
        assertEquals(List.of("rentMode"), result.getMissingSlots());
        assertTrue(result.getAssistantReply().contains("整租/合租"));
    }

    @Test
    void resetShouldReturnFreshSessionAndClearHistory() {
        AiRecommendChatVO result = aiRecommendService.reset(1001L);

        assertEquals("ai-u1001", result.getSessionId());
        assertEquals("ASK", result.getAction());
        assertNotNull(result.getAssistantReply());
        verify(stateStore).reset(1001L);
        verify(stateStore).save(any(AiRecommendSessionState.class));
    }

    private AiRecommendChatReqDTO req(String message) {
        AiRecommendChatReqDTO reqDTO = new AiRecommendChatReqDTO();
        reqDTO.setMessage(message);
        return reqDTO;
    }
}
