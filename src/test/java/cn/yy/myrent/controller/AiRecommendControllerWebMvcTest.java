package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseHistoryMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.LocalTaskMapper;
import cn.yy.myrent.mapper.LocationDictMapper;
import cn.yy.myrent.mapper.MockPayTradeMapper;
import cn.yy.myrent.mapper.NotificationMapper;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.mapper.PaymentRefundMapper;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.mapper.ReviewMapper;
import cn.yy.myrent.mapper.StudentVerificationMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.ai.AiRecommendService;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseHistoryService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IReviewService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.search.HouseKeywordSearchService;
import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.vo.AiPreviewGroupVO;
import cn.yy.myrent.vo.AiPreviewSlotPatchVO;
import cn.yy.myrent.vo.AiPreviewVO;
import cn.yy.myrent.vo.AiRecommendChatVO;
import cn.yy.myrent.vo.AiRecommendSlotsVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiRecommendController.class)
class AiRecommendControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiRecommendService aiRecommendService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private IHouseService houseService;

    @MockBean
    private HouseKeywordSearchService houseKeywordSearchService;

    @MockBean
    private IHouseCommandService houseCommandService;

    @MockBean
    private IHouseHistoryService houseHistoryService;

    @MockBean
    private IReviewService reviewService;

    @MockBean
    private HouseEsSyncService houseEsSyncService;

    @MockBean
    private HouseHotService houseHotService;

    @MockBean
    private ChatMessageMapper chatMessageMapper;

    @MockBean
    private ChatSessionMapper chatSessionMapper;

    @MockBean
    private HouseFavoriteMapper houseFavoriteMapper;

    @MockBean
    private HouseHistoryMapper houseHistoryMapper;

    @MockBean
    private HouseMapper houseMapper;

    @MockBean
    private LocalTaskMapper localTaskMapper;

    @MockBean
    private LocationDictMapper locationDictMapper;

    @MockBean
    private MockPayTradeMapper mockPayTradeMapper;

    @MockBean
    private NotificationMapper notificationMapper;

    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private PaymentMapper paymentMapper;

    @MockBean
    private PaymentRefundMapper paymentRefundMapper;

    @MockBean
    private PublisherFollowMapper publisherFollowMapper;

    @MockBean
    private ReviewMapper reviewMapper;

    @MockBean
    private StudentVerificationMapper studentVerificationMapper;

    @MockBean
    private UserMapper userMapper;

    @Test
    void sessionShouldReturnCurrentUserState() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId("ai-u1001");
        vo.setStage("ASK");
        vo.setAssistantReply("Tell me your budget first.");
        AiRecommendSlotsVO slots = new AiRecommendSlotsVO();
        slots.setCity("Shanghai");
        slots.setBudgetScope("STRICT");
        slots.setPriority("COMMUTE");
        slots.setPreferences(java.util.List.of("near subway", "private bathroom"));
        vo.setSlots(slots);
        vo.setMissingSlots(java.util.List.of("locationName"));

        given(aiRecommendService.getOrCreateSession(1001L)).willReturn(vo);

        mockMvc.perform(get("/ai-recommend/session").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("ai-u1001"))
                .andExpect(jsonPath("$.data.stage").value("ASK"))
                .andExpect(jsonPath("$.data.assistantReply").value("Tell me your budget first."))
                .andExpect(jsonPath("$.data.slots.city").value("Shanghai"))
                .andExpect(jsonPath("$.data.slots.budgetScope").value("STRICT"))
                .andExpect(jsonPath("$.data.slots.priority").value("COMMUTE"))
                .andExpect(jsonPath("$.data.slots.preferences[0]").value("near subway"))
                .andExpect(jsonPath("$.data.missingSlots[0]").value("locationName"));
    }

    @Test
    void sessionShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/ai-recommend/session"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(aiRecommendService);
    }

    @Test
    void chatShouldRejectBlankMessage() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("message cannot be blank"));

        verifyNoInteractions(aiRecommendService);
    }

    @Test
    void chatShouldRejectRequestWithoutMessageOrInteraction() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("message or interaction must be provided"));

        verifyNoInteractions(aiRecommendService);
    }

    @Test
    void chatShouldForwardPayloadAndReturnRecommendation() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        SmartGuideResultVO recommendation = new SmartGuideResultVO();
        recommendation.setTipMessage("Found matching houses.");

        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId("ai-u1001");
        vo.setStage("SEARCH");
        vo.setAssistantReply("I will search based on those filters.");
        vo.setRecommendation(recommendation);

        given(aiRecommendService.chat(any(Long.class), any())).willReturn(vo);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "budget 3500, pudong entire rent"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stage").value("SEARCH"))
                .andExpect(jsonPath("$.data.recommendation.tipMessage").value("Found matching houses."));

        ArgumentCaptor<cn.yy.myrent.dto.AiRecommendChatReqDTO> captor =
                ArgumentCaptor.forClass(cn.yy.myrent.dto.AiRecommendChatReqDTO.class);
        verify(aiRecommendService).chat(org.mockito.ArgumentMatchers.eq(1001L), captor.capture());
        assertEquals("budget 3500, pudong entire rent", captor.getValue().getMessage());
    }

    @Test
    void chatShouldAcceptPreviewSelectionPayload() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        AiPreviewSlotPatchVO slotPatch = new AiPreviewSlotPatchVO();
        slotPatch.setPriority("COMMUTE");
        slotPatch.setPreferences(java.util.List.of("nearSubway"));

        AiPreviewGroupVO group = new AiPreviewGroupVO();
        group.setGroupKey("near_metro");
        group.setTitle("Closer to metro");
        group.setSummary("Shorter commute with somewhat higher first-month cost.");
        group.setHighlights(java.util.List.of("Near subway", "Shorter commute"));
        group.setSampleCount(6);
        group.setSlotPatch(slotPatch);

        AiPreviewVO preview = new AiPreviewVO();
        preview.setLocationName("Pudong");
        preview.setCandidateCount(18);
        preview.setGroups(java.util.List.of(group));

        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId("ai-u1001");
        vo.setStage("REFINE");
        vo.setAssistantReply("I will continue with the near-metro direction.");
        vo.setPreview(preview);

        given(aiRecommendService.chat(any(Long.class), any())).willReturn(vo);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "interaction": {
                                    "type": "PREVIEW_SELECTION",
                                    "groupKey": "near_metro",
                                    "label": "Start with near metro",
                                    "slotPatch": {
                                      "priority": "COMMUTE",
                                      "preferences": ["nearSubway"]
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stage").value("REFINE"))
                .andExpect(jsonPath("$.data.preview.groups[0].groupKey").value("near_metro"));
    }

    @Test
    void chatShouldRejectUnsupportedInteractionType() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        mockMvc.perform(post("/ai-recommend/chat")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "interaction": {
                                    "type": "UNKNOWN",
                                    "groupKey": "near_metro",
                                    "label": "Start with near metro"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("interaction.type must be PREVIEW_SELECTION"));

        verifyNoInteractions(aiRecommendService);
    }

    @Test
    void resetShouldReturnFreshState() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        AiRecommendChatVO vo = new AiRecommendChatVO();
        vo.setSessionId("ai-u1001");
        vo.setStage("ASK");
        vo.setAssistantReply("Let's start over.");

        given(aiRecommendService.reset(1001L)).willReturn(vo);

        mockMvc.perform(post("/ai-recommend/reset").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.assistantReply").value("Let's start over."));

        verify(aiRecommendService).reset(1001L);
    }

    @Test
    void sessionShouldReturnUnavailableWhenServiceBeanMissing() throws Exception {
        MockMvc standaloneMockMvc = MockMvcBuilders.standaloneSetup(
                new AiRecommendController(java.util.Optional.empty()))
                .build();

        UserContext.setCurrentUserId(1001L);
        try {
            standaloneMockMvc.perform(get("/ai-recommend/session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(503))
                    .andExpect(jsonPath("$.message").value("ai recommend service unavailable"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void chatShouldReturnUnavailableWhenServiceBeanMissing() throws Exception {
        MockMvc standaloneMockMvc = MockMvcBuilders.standaloneSetup(
                new AiRecommendController(java.util.Optional.empty()))
                .build();

        UserContext.setCurrentUserId(1001L);
        try {
            standaloneMockMvc.perform(post("/ai-recommend/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "message": "budget 3500"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(503))
                    .andExpect(jsonPath("$.message").value("ai recommend service unavailable"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void resetShouldReturnUnavailableWhenServiceBeanMissing() throws Exception {
        MockMvc standaloneMockMvc = MockMvcBuilders.standaloneSetup(
                new AiRecommendController(java.util.Optional.empty()))
                .build();

        UserContext.setCurrentUserId(1001L);
        try {
            standaloneMockMvc.perform(post("/ai-recommend/reset"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(503))
                    .andExpect(jsonPath("$.message").value("ai recommend service unavailable"));
        } finally {
            UserContext.clear();
        }
    }
}
