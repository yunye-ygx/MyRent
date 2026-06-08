package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.entity.AiChatMessage;
import cn.yy.myrent.entity.AiChatSession;
import cn.yy.myrent.mapper.AiChatMessageMapper;
import cn.yy.myrent.mapper.AiChatSessionMapper;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseHistoryMapper;
import cn.yy.myrent.mapper.HouseHotDailyStatsMapper;
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
import cn.yy.myrent.service.ai.chat.AiChatHistoryService;
import cn.yy.myrent.service.ai.chat.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
class AiChatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiChatService aiChatService;

    @MockBean
    private AiChatHistoryService aiChatHistoryService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private AiChatMessageMapper aiChatMessageMapper;

    @MockBean
    private AiChatSessionMapper aiChatSessionMapper;

    @MockBean
    private ChatMessageMapper chatMessageMapper;

    @MockBean
    private ChatSessionMapper chatSessionMapper;

    @MockBean
    private HouseFavoriteMapper houseFavoriteMapper;

    @MockBean
    private HouseHistoryMapper houseHistoryMapper;

    @MockBean
    private HouseHotDailyStatsMapper houseHotDailyStatsMapper;

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
    void sessionsShouldReturnCurrentUserHistory() throws Exception {
        AiChatSession session = new AiChatSession();
        session.setId(11L);
        session.setUserId(1001L);
        session.setTitle("浦东整租");
        session.setUpdateTime(LocalDateTime.of(2026, 6, 6, 10, 0));

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(aiChatHistoryService.listSessions(1001L)).willReturn(List.of(session));

        mockMvc.perform(get("/ai/sessions").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].title").value("浦东整租"));
    }

    @Test
    void createSessionShouldReturnFreshConversation() throws Exception {
        AiChatSession session = new AiChatSession();
        session.setId(22L);
        session.setUserId(1001L);
        session.setTitle("新会话");

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(aiChatHistoryService.createSession(1001L)).willReturn(session);

        mockMvc.perform(post("/ai/sessions").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(22))
                .andExpect(jsonPath("$.data.title").value("新会话"));
    }

    @Test
    void messagesShouldReturnSessionHistory() throws Exception {
        AiChatMessage message = new AiChatMessage();
        message.setId(101L);
        message.setSessionId(11L);
        message.setRole("assistant");
        message.setContent("这里是历史会话。");

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(aiChatHistoryService.loadVisibleMessages(1001L, 11L, 100)).willReturn(List.of(message));

        mockMvc.perform(get("/ai/sessions/11/messages").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").value(11))
                .andExpect(jsonPath("$.data[0].content").value("这里是历史会话。"));
    }
}
