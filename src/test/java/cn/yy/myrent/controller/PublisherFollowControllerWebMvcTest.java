package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
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
import cn.yy.myrent.service.IPublisherFollowService;
import cn.yy.myrent.vo.PublisherFollowStatusVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublisherFollowController.class)
class PublisherFollowControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IPublisherFollowService publisherFollowService;

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
    void statusShouldReturnCurrentFollowState() throws Exception {
        PublisherFollowStatusVO vo = new PublisherFollowStatusVO();
        vo.setPublisherUserId(9L);
        vo.setFollowing(true);

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(publisherFollowService.getStatus(9L, 1001L)).willReturn(vo);

        mockMvc.perform(get("/publisher-follow/9/status").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true));
    }

    @Test
    void followShouldReturnUpdatedFollowState() throws Exception {
        PublisherFollowStatusVO vo = new PublisherFollowStatusVO();
        vo.setPublisherUserId(9L);
        vo.setFollowing(true);

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(publisherFollowService.follow(9L, 1001L)).willReturn(vo);

        mockMvc.perform(post("/publisher-follow/9").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publisherUserId").value("9"))
                .andExpect(jsonPath("$.data.following").value(true));
    }
}
