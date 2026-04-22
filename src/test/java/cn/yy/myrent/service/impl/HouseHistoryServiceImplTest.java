package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.HouseHistory;
import cn.yy.myrent.mapper.HouseHistoryMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.vo.HouseHistoryCalendarVO;
import cn.yy.myrent.vo.HouseHistoryItemVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseHistoryServiceImplTest {

    @Mock
    private HouseHistoryMapper houseHistoryMapper;

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private HouseHistoryServiceImpl houseHistoryService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(houseHistoryService, "baseMapper", houseHistoryMapper);
    }

    @Test
    void recordBrowseShouldInsertOneRowAndSetTodayBitWhenNoSameDayRecordExists() {
        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setStatus(1));
        when(houseHistoryMapper.selectByUserHouseAndDate(eq(1001L), eq(7L), any(LocalDate.class))).thenReturn(null);

        houseHistoryService.recordBrowse(7L, 1001L);

        ArgumentCaptor<HouseHistory> captor = ArgumentCaptor.forClass(HouseHistory.class);
        verify(houseHistoryMapper).insert(captor.capture());
        HouseHistory saved = captor.getValue();
        assertEquals(1001L, saved.getUserId());
        assertEquals(7L, saved.getHouseId());
        assertEquals(saved.getBrowseDate(), saved.getLastBrowseTime().toLocalDate());
        verify(valueOperations).setBit(anyString(), eq((long) saved.getBrowseDate().getDayOfMonth() - 1), eq(true));
    }

    @Test
    void recordBrowseShouldOnlyUpdateLastBrowseTimeWhenSameDayRecordAlreadyExists() {
        LocalDate today = LocalDate.now(HouseHistoryServiceImpl.APP_ZONE);
        HouseHistory existing = new HouseHistory()
                .setId(9L)
                .setUserId(1001L)
                .setHouseId(7L)
                .setBrowseDate(today)
                .setLastBrowseTime(LocalDateTime.of(today, java.time.LocalTime.of(8, 0)));

        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setStatus(1));
        when(houseHistoryMapper.selectByUserHouseAndDate(1001L, 7L, today)).thenReturn(existing);

        houseHistoryService.recordBrowse(7L, 1001L);

        verify(houseHistoryMapper, never()).insert(any(HouseHistory.class));
        verify(houseHistoryMapper).updateById(existing);
        verify(valueOperations).setBit(anyString(), eq((long) today.getDayOfMonth() - 1), eq(true));
    }

    @Test
    void getCalendarShouldFallBackToMysqlAndWarmBitmapWhenRedisHasNoKnownBits() {
        YearMonth month = YearMonth.of(2026, 4);
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            when(valueOperations.getBit("house_history:calendar:1001:202604", day - 1)).thenReturn(null);
        }
        when(houseHistoryMapper.selectActiveDays(1001L, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(List.of(6, 11, 22));

        HouseHistoryCalendarVO result = houseHistoryService.getCalendar(1001L, 2026, 4);

        assertEquals(List.of(6, 11, 22), result.getActiveDays());
        verify(valueOperations).setBit("house_history:calendar:1001:202604", 5, true);
        verify(valueOperations).setBit("house_history:calendar:1001:202604", 10, true);
        verify(valueOperations).setBit("house_history:calendar:1001:202604", 21, true);
    }

    @Test
    void pageMineShouldDelegateToMapperWithOptionalBrowseDate() {
        Page<HouseHistoryItemVO> mapperPage = new Page<>(1, 10);
        mapperPage.setRecords(List.of(new HouseHistoryItemVO()));
        when(houseHistoryMapper.selectMyHistoryPage(any(Page.class), eq(1001L), eq(LocalDate.of(2026, 4, 22))))
                .thenReturn(mapperPage);

        Page<HouseHistoryItemVO> result = houseHistoryService.pageMine(1001L, 1, 10, LocalDate.of(2026, 4, 22));

        assertEquals(1, result.getRecords().size());
    }

    @Test
    void getCalendarShouldReadRedisBitsDirectlyWhenBitmapAlreadyExists() {
        for (int day = 1; day <= 30; day++) {
            when(valueOperations.getBit("house_history:calendar:1001:202604", day - 1))
                    .thenReturn(day == 6 || day == 22);
        }

        HouseHistoryCalendarVO result = houseHistoryService.getCalendar(1001L, 2026, 4);

        assertEquals(List.of(6, 22), result.getActiveDays());
        verify(houseHistoryMapper, never()).selectActiveDays(anyLong(), any(LocalDate.class), any(LocalDate.class));
    }
}
