package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.HouseHotDailyStats;
import cn.yy.myrent.service.hot.HouseHotDailyStatsAggRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface HouseHotDailyStatsMapper extends BaseMapper<HouseHotDailyStats> {

    int upsertDelta(@Param("houseId") Long houseId,
                    @Param("city") String city,
                    @Param("statDate") LocalDate statDate,
                    @Param("browseDelta") Long browseDelta,
                    @Param("favoriteDelta") Long favoriteDelta,
                    @Param("consultDelta") Long consultDelta);

    List<HouseHotDailyStatsAggRow> selectRecentAggRowsByCity(@Param("city") String city,
                                                             @Param("startDate") LocalDate startDate);
}
