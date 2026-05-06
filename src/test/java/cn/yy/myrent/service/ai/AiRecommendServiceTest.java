package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.dto.AiRecommendInteractionDTO;
import cn.yy.myrent.dto.AiRecommendInteractionSlotPatchDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.AiPreviewGroupVO;
import cn.yy.myrent.vo.AiPreviewVO;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Mock
    private AiPreviewService previewService;

    private AiRecommendServiceImpl aiRecommendService;

    @BeforeEach
    void setUp() {
        aiRecommendService = new AiRecommendServiceImpl(
                decisionClient,
                stateStore,
                houseService,
                new AiRecommendSummaryBuilder(),
                previewService,
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
        assertEquals("ASK", result.getStage());
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

        assertEquals("ASK", result.getStage());
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

        assertEquals("ASK", result.getStage());
        assertTrue(result.getAssistantReply().contains("tradeoffs"));
        assertNull(result.getRecommendation());
        verify(houseService, never()).smartGuide(any(SmartGuideReqDTO.class));
    }

    @Test
    void chatShouldHandleInteractionOnlyRequestsWithoutFailing() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("Tell me a bit more about your preferred area.")
                        .slots(AiRecommendSlots.builder()
                                .priority("COMMUTE")
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, interactionReq("Start with near metro", null));

        assertEquals("ASK", result.getStage());
        assertEquals("COMMUTE", result.getSlots().getPriority());

        ArgumentCaptor<AiRecommendSessionState> stateCaptor = ArgumentCaptor.forClass(AiRecommendSessionState.class);
        verify(stateStore).save(stateCaptor.capture());
        assertEquals("Start with near metro", stateCaptor.getValue().getHistory().get(0).getContent());
        verify(decisionClient).decide(any(AiRecommendSessionState.class), org.mockito.ArgumentMatchers.eq("Start with near metro"));
    }

    @Test
    void chatShouldMergeInteractionSlotPatchIntoCurrentSlots() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        session.setSlots(AiRecommendSlots.builder()
                .city("Shanghai")
                .locationName("Pudong")
                .budgetScope("RENT_ONLY")
                .build());
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("I noted this direction.")
                        .slots(AiRecommendSlots.builder().build())
                        .build());

        AiRecommendInteractionSlotPatchDTO slotPatch = new AiRecommendInteractionSlotPatchDTO();
        slotPatch.setBudgetYuan(4200);
        slotPatch.setRentMode("WHOLE");
        slotPatch.setPriority("COMMUTE");
        slotPatch.setPreferences(List.of("nearSubway"));

        AiRecommendChatVO result = aiRecommendService.chat(1001L, interactionReq("Start with near metro", slotPatch));

        assertEquals("SEARCH", result.getStage());
        assertEquals(Integer.valueOf(4200), result.getSlots().getBudgetYuan());
        assertEquals("WHOLE", result.getSlots().getRentMode());
        assertEquals("COMMUTE", result.getSlots().getPriority());
        assertEquals(List.of("nearSubway"), result.getSlots().getPreferences());
    }

    @Test
    void chatShouldEnterPreviewWhenLocationExistsButSearchIsNotReady() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("我先看看豫园附近真实房源。")
                        .slots(AiRecommendSlots.builder()
                                .city("上海")
                                .locationName("豫园")
                                .build())
                        .build());

        AiPreviewVO preview = new AiPreviewVO();
        preview.setLocationName("豫园");
        preview.setCandidateCount(12);
        preview.setGroups(List.of(new AiPreviewGroupVO()));
        when(previewService.build("豫园", null, "RENT_ONLY", null)).thenReturn(preview);

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("我想在豫园租房"));

        assertEquals("PREVIEW", result.getStage());
        assertNotNull(result.getPreview());
        assertEquals("豫园", result.getPreview().getLocationName());
    }

    @Test
    void chatShouldTurnPreviewSelectionIntoSearchWhenHardSlotsBecomeReady() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        session.setSlots(AiRecommendSlots.builder()
                .city("上海")
                .locationName("豫园")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .build());
        session.setStage("PREVIEW");
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("我按近地铁方向继续找。")
                        .slots(AiRecommendSlots.builder().build())
                        .build());
        when(houseService.smartGuide(any(SmartGuideReqDTO.class))).thenReturn(new SmartGuideResultVO());

        AiRecommendChatVO result = aiRecommendService.chat(
                1001L,
                interactionReq("near_metro", "先看近地铁的", "COMMUTE", "WHOLE", List.of("nearSubway"))
        );

        assertEquals("SEARCH", result.getStage());
        assertNotNull(result.getRecommendation());
    }

    @Test
    void previewSelectionShouldStayInRefineWhenRentModeIsStillMissing() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        session.setSlots(AiRecommendSlots.builder()
                .city("上海")
                .locationName("豫园")
                .budgetYuan(3500)
                .budgetScope("RENT_ONLY")
                .build());
        session.setStage("PREVIEW");
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("我先按近地铁方向继续收窄。")
                        .slots(AiRecommendSlots.builder().build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(
                1001L,
                interactionReq("near_metro", "先看近地铁的", "COMMUTE", null, List.of("nearSubway"))
        );

        assertEquals("REFINE", result.getStage());
        assertNull(result.getPreview());
        assertEquals("COMMUTE", result.getSlots().getPriority());
        assertEquals(List.of("nearSubway"), result.getSlots().getPreferences());
    }

    @Test
    void refineShouldNotRebuildPreviewAfterDirectionHasBeenSelected() {
        AiRecommendSessionState session = AiRecommendSessionState.empty(1001L);
        session.setSlots(AiRecommendSlots.builder()
                .city("Shanghai")
                .locationName("Yuyuan")
                .budgetScope("RENT_ONLY")
                .priority("COMMUTE")
                .preferences(List.of("nearSubway"))
                .build());
        session.setStage("REFINE");
        session.setSelectedPreviewGroupKey("near_metro");
        when(stateStore.loadOrCreate(1001L)).thenReturn(session);
        when(decisionClient.decide(any(AiRecommendSessionState.class), any(String.class)))
                .thenReturn(AiRecommendDecision.builder()
                        .reply("Tell me the budget first.")
                        .slots(AiRecommendSlots.builder()
                                .budgetYuan(3800)
                                .build())
                        .build());

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 3800"));

        assertEquals("REFINE", result.getStage());
        assertNull(result.getPreview());
        assertEquals(Integer.valueOf(3800), result.getSlots().getBudgetYuan());
        assertEquals("COMMUTE", result.getSlots().getPriority());
        assertEquals(List.of("nearSubway"), result.getSlots().getPreferences());
        verify(previewService, never()).build(any(), any(), any(), any());
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

        assertEquals("SEARCH", result.getStage());
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

        assertEquals("ASK", result.getStage());
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

        assertEquals("SEARCH", result.getStage());
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

        assertEquals("SEARCH", result.getStage());
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

        assertEquals("ASK", result.getStage());
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
        AiPreviewVO preview = new AiPreviewVO();
        preview.setLocationName("Pudong");
        preview.setCandidateCount(6);
        preview.setGroups(List.of(new AiPreviewGroupVO()));
        when(previewService.build("Pudong", 3500, "RENT_ONLY", "WHOLE")).thenReturn(preview);

        AiRecommendChatVO result = aiRecommendService.chat(1001L, req("budget 3500, whole rent in Pudong"));

        assertEquals("REFINE", result.getStage());
        assertNotNull(result.getPreview());
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

        assertEquals("SEARCH", result.getStage());

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

        assertEquals("ASK", result.getStage());
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

        assertEquals("ASK", result.getStage());
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

        assertEquals("SEARCH", result.getStage());
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

        assertEquals("ASK", result.getStage());
        assertEquals(List.of("rentMode"), result.getMissingSlots());
        assertTrue(result.getAssistantReply().contains("整租/合租"));
    }

    @Test
    void resetShouldReturnFreshSessionAndClearHistory() {
        AiRecommendChatVO result = aiRecommendService.reset(1001L);

        assertEquals("ai-u1001", result.getSessionId());
        assertEquals("ASK", result.getStage());
        assertNotNull(result.getAssistantReply());
        verify(stateStore).reset(1001L);
        verify(stateStore).save(any(AiRecommendSessionState.class));
    }

    private AiRecommendChatReqDTO req(String message) {
        AiRecommendChatReqDTO reqDTO = new AiRecommendChatReqDTO();
        reqDTO.setMessage(message);
        return reqDTO;
    }

    private AiRecommendChatReqDTO interactionReq(String label, AiRecommendInteractionSlotPatchDTO slotPatch) {
        AiRecommendInteractionDTO interactionDTO = new AiRecommendInteractionDTO();
        interactionDTO.setType("PREVIEW_SELECTION");
        interactionDTO.setGroupKey("near_metro");
        interactionDTO.setLabel(label);
        interactionDTO.setSlotPatch(slotPatch);

        AiRecommendChatReqDTO reqDTO = new AiRecommendChatReqDTO();
        reqDTO.setInteraction(interactionDTO);
        return reqDTO;
    }

    private AiRecommendChatReqDTO interactionReq(String groupKey,
                                                 String label,
                                                 String priority,
                                                 String rentMode,
                                                 List<String> preferences) {
        AiRecommendInteractionSlotPatchDTO slotPatch = new AiRecommendInteractionSlotPatchDTO();
        slotPatch.setPriority(priority);
        slotPatch.setRentMode(rentMode);
        slotPatch.setPreferences(preferences);

        AiRecommendInteractionDTO interactionDTO = new AiRecommendInteractionDTO();
        interactionDTO.setType("PREVIEW_SELECTION");
        interactionDTO.setGroupKey(groupKey);
        interactionDTO.setLabel(label);
        interactionDTO.setSlotPatch(slotPatch);

        AiRecommendChatReqDTO reqDTO = new AiRecommendChatReqDTO();
        reqDTO.setInteraction(interactionDTO);
        return reqDTO;
    }
}
