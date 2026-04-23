package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.dto.HouseSuggestReqDTO;
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
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.IHouseHistoryService;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.service.IReviewService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import cn.yy.myrent.vo.HouseSuggestItemVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HouseController.class)
class HouseControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IHouseService houseService;

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
    private JwtTokenUtil jwtTokenUtil;

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
    private UserMapper userMapper;

    @Test
    void rebuildHotCacheShouldInvokeHotService() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);

        mockMvc.perform(post("/house/hot/rebuild")
                        .header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(houseHotService).rebuildHotRanking();
    }

    @Test
    void rebuildHotCacheShouldBeAccessibleWithoutToken() throws Exception {
        mockMvc.perform(post("/house/hot/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void suggestShouldDefaultSizeTo5AndReturnItems() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(houseService.suggest(any(HouseSuggestReqDTO.class))).willReturn(List.of(
                new HouseSuggestItemVO(1L, "整租 1 室 1 厅", 3000),
                new HouseSuggestItemVO(2L, "合租 次卧", 1800)
        ));

        mockMvc.perform(post("/house/suggest")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"1室\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("整租 1 室 1 厅"))
                .andExpect(jsonPath("$.data[0].price").value(3000));

        ArgumentCaptor<HouseSuggestReqDTO> captor = ArgumentCaptor.forClass(HouseSuggestReqDTO.class);
        verify(houseService).suggest(captor.capture());
        assertEquals("1室", captor.getValue().getKeyword());
        assertEquals(5, captor.getValue().getSize());
    }

    @Test
    void suggestShouldCapSizeAt5() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(houseService.suggest(any(HouseSuggestReqDTO.class))).willReturn(List.of());

        mockMvc.perform(post("/house/suggest")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"abc\",\"size\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<HouseSuggestReqDTO> captor = ArgumentCaptor.forClass(HouseSuggestReqDTO.class);
        verify(houseService).suggest(captor.capture());
        assertEquals(5, captor.getValue().getSize());
    }

    @Test
    void suggestShouldRequireKeyword() throws Exception {
        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        mockMvc.perform(post("/house/suggest")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"size\":5}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(houseService);
    }

    @Test
    void getByIdShouldRecordBrowseAfterSuccessfulAuthenticatedFetch() throws Exception {
        House house = new House();
        house.setId(7L);
        house.setTitle("History House");

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(houseService.getById(7L)).willReturn(house);

        mockMvc.perform(get("/house/7")
                        .header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7));

        verify(houseHistoryService).recordBrowse(7L, 1001L);
    }

    @Test
    void getByIdShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/house/7"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(houseService);
        verifyNoInteractions(houseHistoryService);
    }
}
