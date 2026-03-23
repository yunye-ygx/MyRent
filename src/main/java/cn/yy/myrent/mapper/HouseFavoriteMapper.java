package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.service.hot.HouseFavoriteAggRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HouseFavoriteMapper extends BaseMapper<HouseFavorite> {

    List<HouseFavoriteAggRow> selectFavoriteAggRows(@Param("recentSince") LocalDateTime recentSince);
}
