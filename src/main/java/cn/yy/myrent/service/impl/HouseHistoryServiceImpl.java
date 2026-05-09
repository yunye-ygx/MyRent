package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.HouseHistory;
import cn.yy.myrent.mapper.HouseHistoryMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseHistoryService;
import cn.yy.myrent.service.hot.HouseHotDailyStatsService;
import cn.yy.myrent.vo.HouseHistoryCalendarVO;
import cn.yy.myrent.vo.HouseHistoryItemVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HouseHistoryServiceImpl extends ServiceImpl<HouseHistoryMapper, HouseHistory> implements IHouseHistoryService {

    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final HouseMapper houseMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final HouseHotDailyStatsService houseHotDailyStatsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordBrowse(Long houseId, Long userId) {
        if (houseId == null || userId == null) {
            return;
        }

        House house = houseMapper.selectById(houseId);
        if (house == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        LocalDate browseDate = now.toLocalDate();
        HouseHistory existing = baseMapper.selectByUserHouseAndDate(userId, houseId, browseDate);
        if (existing == null) {
            HouseHistory history = new HouseHistory()
                    .setUserId(userId)
                    .setHouseId(houseId)
                    .setBrowseDate(browseDate)
                    .setLastBrowseTime(now)
                    .setCreateTime(now)
                    .setUpdateTime(now);
            baseMapper.insert(history);
            houseHotDailyStatsService.incrementBrowse(houseId, house.getCity(), browseDate);
        } else {
            existing.setLastBrowseTime(now);
            existing.setUpdateTime(now);
            baseMapper.updateById(existing);
        }

        stringRedisTemplate.opsForValue()
                .setBit(calendarKey(userId, YearMonth.from(browseDate)), browseDate.getDayOfMonth() - 1L, true);
    }

    @Override
    public HouseHistoryCalendarVO getCalendar(Long userId, Integer year, Integer month) {
        YearMonth targetMonth = YearMonth.of(year, month);
        String key = calendarKey(userId, targetMonth);
        List<Integer> activeDays = new ArrayList<>();
        boolean hasRedisValue = false;

        for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
            Boolean bit = stringRedisTemplate.opsForValue().getBit(key, day - 1L);
            if (bit != null) {
                hasRedisValue = true;
                if (Boolean.TRUE.equals(bit)) {
                    activeDays.add(day);
                }
            }
        }

        if (!hasRedisValue) {
            activeDays = baseMapper.selectActiveDays(userId, targetMonth.atDay(1), targetMonth.atEndOfMonth());
            for (Integer day : activeDays) {
                stringRedisTemplate.opsForValue().setBit(key, day - 1L, true);
            }
        }

        HouseHistoryCalendarVO result = new HouseHistoryCalendarVO();
        result.setYear(year);
        result.setMonth(month);
        result.setActiveDays(activeDays);
        return result;
    }

    @Override
    public Page<HouseHistoryItemVO> pageMine(Long userId, long current, long size, LocalDate browseDate) {
        Page<HouseHistoryItemVO> page = new Page<>(Math.max(current, 1L), Math.min(Math.max(size, 1L), 100L));
        return baseMapper.selectMyHistoryPage(page, userId, browseDate);
    }

    private String calendarKey(Long userId, YearMonth yearMonth) {
        return "house_history:calendar:" + userId + ":" + yearMonth.format(MONTH_KEY_FORMATTER);
    }
}
