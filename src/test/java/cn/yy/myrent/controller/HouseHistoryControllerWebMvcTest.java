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
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.mapper.PaymentRefundMapper;
import cn.yy.myrent.mapper.ReviewMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IHouseHistoryService;
import cn.yy.myrent.vo.HouseHistoryCalendarVO;
import cn.yy.myrent.vo.HouseHistoryItemVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HouseHistoryController.class)
class HouseHistoryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IHouseHistoryService houseHistoryService;

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
    private OrderMapper orderMapper;

    @MockBean
    private PaymentMapper paymentMapper;

    @MockBean
    private PaymentRefundMapper paymentRefundMapper;

    @MockBean
    private ReviewMapper reviewMapper;

    @MockBean
    private UserMapper userMapper;

    @Test
    void calendarShouldReturnActiveDaysForCurrentUser() throws Exception {
        HouseHistoryCalendarVO calendar = new HouseHistoryCalendarVO();
        calendar.setYear(2026);
        calendar.setMonth(4);
        calendar.setActiveDays(List.of(6, 11, 22));

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(houseHistoryService.getCalendar(1001L, 2026, 4)).willReturn(calendar);

        mockMvc.perform(get("/house-history/calendar")
                        .header("token", "test-token")
                        .param("year", "2026")
                        .param("month", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activeDays[0]").value(6))
                .andExpect(jsonPath("$.data.activeDays[2]").value(22));
    }

    @Test
    void mineShouldReturnFilteredHistoryPage() throws Exception {
        HouseHistoryItemVO item = new HouseHistoryItemVO();
        item.setHistoryId(1L);
        item.setHouseId(7L);
        item.setBrowseDate(LocalDate.of(2026, 4, 22));
        item.setLastBrowseTime(LocalDateTime.of(2026, 4, 22, 18, 30));
        item.setPrice(BigDecimal.valueOf(3200));
        item.setCover("https://picsum.photos/seed/history-7/480/320");

        Page<HouseHistoryItemVO> page = new Page<>(1, 10);
        page.setRecords(List.of(item));
        page.setTotal(1);

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(houseHistoryService.pageMine(1001L, 1, 10, LocalDate.of(2026, 4, 22))).willReturn(page);

        mockMvc.perform(get("/house-history/mine")
                        .header("token", "test-token")
                        .param("current", "1")
                        .param("size", "10")
                        .param("browseDate", "2026-04-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].houseId").value("7"))
                .andExpect(jsonPath("$.data.records[0].price").value(3200));
    }

    @Test
    void calendarShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/house-history/calendar")
                        .param("year", "2026")
                        .param("month", "4"))
                .andExpect(status().isUnauthorized());
    }
}
