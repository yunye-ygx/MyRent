package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.entity.Notification;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
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
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.vo.UnreadTotalVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private INotificationService notificationService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private ChatMessageMapper chatMessageMapper;

    @MockBean
    private ChatSessionMapper chatSessionMapper;

    @MockBean
    private HouseFavoriteMapper houseFavoriteMapper;

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
    private UserMapper userMapper;

    @Test
    void unreadTotalShouldReturnCurrentUserInboxCount() throws Exception {
        UnreadTotalVO vo = new UnreadTotalVO();
        vo.setTotal(3L);
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(notificationService.buildUnreadTotal(1001L)).willReturn(vo);

        mockMvc.perform(get("/notification/unread-total").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void pageShouldReturnLatestNotificationRows() throws Exception {
        Notification item = new Notification()
                .setId(8L)
                .setUserId(1001L)
                .setType("HOUSE_PRICE_CHANGED")
                .setTitle("Price changed")
                .setContent("The monthly price changed from 5200 to 5000.")
                .setRedirectType("house_detail")
                .setRedirectTargetId(7L)
                .setIsRead(0)
                .setCreateTime(LocalDateTime.of(2026, 4, 22, 10, 0));
        Page<Notification> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(item));

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(notificationService.pageMine(1001L, 1L, 10L)).willReturn(page);

        mockMvc.perform(get("/notification/page").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].type").value("HOUSE_PRICE_CHANGED"))
                .andExpect(jsonPath("$.data.records[0].redirectTargetId").value(7));
    }
}
