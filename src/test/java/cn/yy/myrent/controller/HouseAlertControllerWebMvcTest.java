package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.entity.HouseAlert;
import cn.yy.myrent.mapper.AiChatMessageMapper;
import cn.yy.myrent.mapper.AiChatSessionMapper;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseAlertMapper;
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
import cn.yy.myrent.service.IHouseAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HouseAlertController.class)
class HouseAlertControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IHouseAlertService houseAlertService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private ChatMessageMapper chatMessageMapper;

    @MockBean
    private ChatSessionMapper chatSessionMapper;

    @MockBean
    private AiChatMessageMapper aiChatMessageMapper;

    @MockBean
    private AiChatSessionMapper aiChatSessionMapper;

    @MockBean
    private HouseAlertMapper houseAlertMapper;

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
    void listMineShouldReturnMyAlerts() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(houseAlertService.listMine(1001L)).willReturn(List.of(
                new HouseAlert()
                        .setId(11L)
                        .setUserId(1001L)
                        .setCity("Nanjing")
                        .setRegion("Gulou")
                        .setMaxPrice(4500)
                        .setRentType(1)
                        .setStatus(1)
                        .setCreateTime(LocalDateTime.now())
                        .setUpdateTime(LocalDateTime.now())
        ));

        mockMvc.perform(get("/house-alert/mine").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("11"))
                .andExpect(jsonPath("$.data[0].city").value("Nanjing"))
                .andExpect(jsonPath("$.data[0].status").value(1));
    }

    @Test
    void createShouldReturnCreatedAlert() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(houseAlertService.createAlert(any(), any())).willReturn(
                new HouseAlert()
                        .setId(12L)
                        .setUserId(1001L)
                        .setCity("Nanjing")
                        .setRegion("Qinhuai")
                        .setMaxPrice(3800)
                        .setRentType(2)
                        .setStatus(1)
                        .setCreateTime(LocalDateTime.now())
                        .setUpdateTime(LocalDateTime.now())
        );

        mockMvc.perform(post("/house-alert")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"Nanjing\",\"region\":\"Qinhuai\",\"maxPrice\":3800,\"rentType\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("12"))
                .andExpect(jsonPath("$.data.region").value("Qinhuai"));
    }
}
