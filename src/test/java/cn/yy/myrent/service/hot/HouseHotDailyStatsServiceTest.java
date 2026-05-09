package cn.yy.myrent.service.hot;

import cn.yy.myrent.mapper.HouseHotDailyStatsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HouseHotDailyStatsServiceTest {

    @Mock
    private HouseHotDailyStatsMapper houseHotDailyStatsMapper;

    @InjectMocks
    private HouseHotDailyStatsService service;

    @Test
    void incrementBrowseShouldUpsertBrowseCount() {
        LocalDate statDate = LocalDate.of(2026, 5, 9);

        service.incrementBrowse(7L, "nanjing", statDate);

        verify(houseHotDailyStatsMapper).upsertDelta(7L, "nanjing", statDate, 1L, 0L, 0L);
    }

    @Test
    void incrementFavoriteShouldUpsertFavoriteCount() {
        LocalDate statDate = LocalDate.of(2026, 5, 9);

        service.incrementFavorite(7L, "nanjing", statDate);

        verify(houseHotDailyStatsMapper).upsertDelta(7L, "nanjing", statDate, 0L, 1L, 0L);
    }

    @Test
    void decrementFavoriteShouldUpsertNegativeFavoriteCount() {
        LocalDate statDate = LocalDate.of(2026, 5, 9);

        service.decrementFavorite(7L, "nanjing", statDate);

        verify(houseHotDailyStatsMapper).upsertDelta(7L, "nanjing", statDate, 0L, -1L, 0L);
    }

    @Test
    void incrementConsultShouldUpsertConsultCount() {
        LocalDate statDate = LocalDate.of(2026, 5, 9);

        service.incrementConsult(7L, "nanjing", statDate);

        verify(houseHotDailyStatsMapper).upsertDelta(7L, "nanjing", statDate, 0L, 0L, 1L);
    }
}
