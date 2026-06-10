package cn.yy.myrent.controller;

import cn.dev33.satoken.spring.SaTokenContextRegister;
import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.config.AdminInterceptor;
import cn.yy.myrent.config.SaTokenExceptionHandler;
import cn.yy.myrent.config.UserContextInterceptor;
import cn.yy.myrent.config.WebMvcConfig;
import cn.yy.myrent.dto.UserPhoneReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.User;
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
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.service.IPaymentService;
import cn.yy.myrent.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminController.class, UserController.class})
@Import({
        SaTokenContextRegister.class,
        JwtTokenUtil.class,
        UserContextInterceptor.class,
        AdminInterceptor.class,
        WebMvcConfig.class,
        SaTokenExceptionHandler.class
})
class AdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockBean(name = "aiChatMessageMapper")
    private AiChatMessageMapper aiChatMessageMapper;

    @MockBean(name = "aiChatSessionMapper")
    private AiChatSessionMapper aiChatSessionMapper;

    @MockBean(name = "chatMessageMapper")
    private ChatMessageMapper chatMessageMapper;

    @MockBean(name = "chatSessionMapper")
    private ChatSessionMapper chatSessionMapper;

    @MockBean(name = "houseAlertMapper")
    private HouseAlertMapper houseAlertMapper;

    @MockBean(name = "houseFavoriteMapper")
    private HouseFavoriteMapper houseFavoriteMapper;

    @MockBean(name = "houseHistoryMapper")
    private HouseHistoryMapper houseHistoryMapper;

    @MockBean(name = "houseHotDailyStatsMapper")
    private HouseHotDailyStatsMapper houseHotDailyStatsMapper;

    @MockBean(name = "houseMapper")
    private HouseMapper houseMapper;

    @MockBean(name = "localTaskMapper")
    private LocalTaskMapper localTaskMapper;

    @MockBean(name = "locationDictMapper")
    private LocationDictMapper locationDictMapper;

    @MockBean(name = "mockPayTradeMapper")
    private MockPayTradeMapper mockPayTradeMapper;

    @MockBean(name = "notificationMapper")
    private NotificationMapper notificationMapper;

    @MockBean(name = "orderMapper")
    private OrderMapper orderMapper;

    @MockBean(name = "paymentMapper")
    private PaymentMapper paymentMapper;

    @MockBean(name = "paymentRefundMapper")
    private PaymentRefundMapper paymentRefundMapper;

    @MockBean(name = "publisherFollowMapper")
    private PublisherFollowMapper publisherFollowMapper;

    @MockBean(name = "reviewMapper")
    private ReviewMapper reviewMapper;

    @MockBean(name = "studentVerificationMapper")
    private StudentVerificationMapper studentVerificationMapper;

    @MockBean(name = "userMapper")
    private UserMapper userMapper;

    @MockBean
    private IUserService userService;

    @MockBean
    private IHouseService houseService;

    @MockBean
    private IHouseCommandService houseCommandService;

    @MockBean
    private IOrderService orderService;

    @MockBean
    private IPaymentService paymentService;

    @Test
    void dashboardShouldRejectNormalUser() throws Exception {
        User user = new User()
                .setId(2001L)
                .setPhone("13800138000")
                .setName("user")
                .setRole(0);
        when(userService.loginByPhone("13800138000", "123456")).thenReturn(user);
        String token = loginAndExtractToken();

        mockMvc.perform(get("/api/admin/orders/1")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardShouldAllowAdminUser() throws Exception {
        User admin = new User()
                .setId(2002L)
                .setPhone("13800138001")
                .setName("admin")
                .setRole(1);
        when(userService.loginByPhone("13800138001", "123456")).thenReturn(admin);
        String token = loginAndExtractToken("13800138001");

        Order order = new Order()
                .setId(1L)
                .setOrderNo("ORDER-1")
                .setUserId(2002L)
                .setHouseId(3001L)
                .setAmount(1000)
                .setStatus(1);
        User user = new User()
                .setId(2002L)
                .setPhone("13800138000")
                .setName("admin");
        House house = new House()
                .setId(3001L)
                .setTitle("house");

        when(orderService.getById(1L)).thenReturn(order);
        when(userService.getById(2002L)).thenReturn(user);
        when(houseService.getById(3001L)).thenReturn(house);

        mockMvc.perform(get("/api/admin/orders/1")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String loginAndExtractToken() throws Exception {
        return loginAndExtractToken("13800138000");
    }

    private String loginAndExtractToken(String phone) throws Exception {
        UserPhoneReqDTO request = new UserPhoneReqDTO();
        request.setPhone(phone);
        request.setPassword("123456");

        MvcResult result = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
    }
}
