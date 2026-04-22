package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.HouseHistory;
import cn.yy.myrent.vo.HouseHistoryItemVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface HouseHistoryMapper extends BaseMapper<HouseHistory> {

    HouseHistory selectByUserHouseAndDate(@Param("userId") Long userId,
                                          @Param("houseId") Long houseId,
                                          @Param("browseDate") LocalDate browseDate);

    List<Integer> selectActiveDays(@Param("userId") Long userId,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    Page<HouseHistoryItemVO> selectMyHistoryPage(Page<HouseHistoryItemVO> page,
                                                 @Param("userId") Long userId,
                                                 @Param("browseDate") LocalDate browseDate);
}
