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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        aiRecommendService = new AiRecommendServiceImpl(decisionClient, stateStore, houseService, 10, "RENT_ONLY");
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
                        .reply("预算和区域再具体一点，我再继续帮你缩小范围。")
                        .slots(AiRecommendSlots.builder()
                                .city("上海")
                                .budgetScope("RENT_ONLY")
                                .priority("COMMUTE")
                                .preferences(List.of("近地铁"))
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("我想在上海租房"));

        assertEquals("ASK", result.getAction());
        assertNull(result.getRecommendation());
        assertEquals("上海", result.getSlots().getCity());
        assertEquals("COMMUTE", result.getSlots().getPriority());
        assertEquals(List.of("近地铁"), result.getSlots().getPreferences());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
        verify(stateStore).save(any(AiRecommendSessionState.class));
    }

    @Test
    void chatShouldHandleAdviseDecisionWithoutTriggeringSearch() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("如果你还没想好区域，可以先在通勤和预算之间做取舍。")
                        .slots(AiRecommendSlots.builder()
                                .city("上海")
                                .budgetScope("RENT_ONLY")
                                .priority("PRICE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("先给我一点建议"));

        assertEquals("ASK", result.getAction());
        assertTrue(result.getAssistantReply().contains("取舍"));
        assertNull(result.getRecommendation());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldTriggerSearchWhenRequiredSlotsAreReady() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("我先按你的条件筛一批真实房源。")
                        .slots(AiRecommendSlots.builder()
                                .city("上海")
                                .locationName("浦东")
                                .budgetYuan(3500)
                                .budgetScope("TOTAL")
                                .rentMode("WHOLE")
                                .priority("COMMUTE")
                                .preferences(List.of("近地铁"))
                                .build())
                        .build());
        SmartGuideResultVO recommendation = new SmartGuideResultVO();
        recommendation.setTipMessage("已找到符合条件的房源。");
        when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(recommendation);

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("预算3500，想在浦东整租"));

        assertEquals("SEARCH", result.getAction());
        assertNotNull(result.getRecommendation());
        assertEquals("已找到符合条件的房源。", result.getRecommendation().getTipMessage());

        ArgumentCaptor<SmartGuideReqDTO> captor = ArgumentCaptor.forClass(SmartGuideReqDTO.class);
        verify(houseService).smartGuide(captor.capture());
        assertEquals(Integer.valueOf(3500), captor.getValue().getBudgetYuan());
        assertEquals("WHOLE", captor.getValue().getRentMode());
        assertEquals("浦东", captor.getValue().getLocationName());
        assertEquals("TOTAL", captor.getValue().getBudgetScope());
    }

    @Test
    void chatShouldDowngradeSearchToAskWhenRequiredSlotsAreMissing() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("我来帮你搜一下。")
                        .slots(AiRecommendSlots.builder()
                                .city("上海")
                                .budgetYuan(3500)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("预算3500整租"));

        assertEquals("ASK", result.getAction());
        assertNull(result.getRecommendation());
        assertEquals("我来帮你搜一下。", result.getAssistantReply());
        assertEquals(List.of("locationName"), result.getMissingSlots());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldRecalculateMissingSlotsWhenModelOutputIsInconsistent() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("我先帮你整理一下条件。")
                        .slots(AiRecommendSlots.builder()
                                .city("上海")
                                .budgetYuan(4200)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("预算4200，整租"));

        assertEquals(List.of("locationName"), result.getMissingSlots());
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
                        .reply("我来帮你查房源。")
                        .slots(AiRecommendSlots.builder()
                                .city("上海")
                                .locationName("浦东")
                                .budgetYuan(100)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("预算100，浦东整租"));

        assertEquals("ASK", result.getAction());
        assertTrue(result.getMissingSlots().contains("budgetYuan"));
        assertEquals("我来帮你查房源。", result.getAssistantReply());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldFallbackGracefullyWhenSmartGuideThrows() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("我先按条件帮你查一批。")
                        .slots(AiRecommendSlots.builder()
                                .city("上海")
                                .locationName("浦东")
                                .budgetYuan(3500)
                                .budgetScope("RENT_ONLY")
                                .rentMode("WHOLE")
                                .build())
                        .build());
        when(houseService.smartGuide(any(SmartGuideReqDTO.class)))
                .thenThrow(new RuntimeException("smart guide unavailable"));

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("预算3500，浦东整租"));

        assertEquals("ADVISE", result.getAction());
        assertNull(result.getRecommendation());
        assertNotNull(result.getAssistantReply());
        verify(stateStore).save(any(AiRecommendSessionState.class));
        verify(houseService, times(1)).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldFallbackToAskWhenDecisionClientThrows() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenThrow(new RuntimeException("ai unavailable"));

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("帮我推荐一个"));

        assertEquals("ASK", result.getAction());
        assertNull(result.getRecommendation());
        assertNotNull(result.getAssistantReply());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
        verify(stateStore).save(any(AiRecommendSessionState.class));
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
