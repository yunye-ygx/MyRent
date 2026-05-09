package cn.yy.myrent.service.hot;

import cn.yy.myrent.mapper.HouseHotDailyStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class HouseHotDailyStatsService {

    private final HouseHotDailyStatsMapper houseHotDailyStatsMapper;

    public void incrementBrowse(Long houseId, String city, LocalDate statDate) {
        upsertDelta(houseId, city, statDate, 1L, 0L, 0L);
    }

    public void incrementFavorite(Long houseId, String city, LocalDate statDate) {
        upsertDelta(houseId, city, statDate, 0L, 1L, 0L);
    }

    public void decrementFavorite(Long houseId, String city, LocalDate statDate) {
        upsertDelta(houseId, city, statDate, 0L, -1L, 0L);
    }

    public void incrementConsult(Long houseId, String city, LocalDate statDate) {
        upsertDelta(houseId, city, statDate, 0L, 0L, 1L);
    }

    private void upsertDelta(Long houseId,
                             String city,
                             LocalDate statDate,
                             Long browseDelta,
                             Long favoriteDelta,
                             Long consultDelta) {
        if (houseId == null || !StringUtils.hasText(city) || statDate == null) {
            return;
        }
        houseHotDailyStatsMapper.upsertDelta(houseId, city, statDate, browseDelta, favoriteDelta, consultDelta);
    }
}
