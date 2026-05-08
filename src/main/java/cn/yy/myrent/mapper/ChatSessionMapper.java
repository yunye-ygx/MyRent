package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.service.hot.HouseSignalCountRow;
import cn.yy.myrent.vo.ChatSessionSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    long countUnreadMessages(@Param("userId") Long userId);

    long countSessionSummaries(@Param("userId") Long userId);

    List<HouseSignalCountRow> selectConsultCountsSince(@Param("recentSince") LocalDateTime recentSince);

    List<HouseSignalCountRow> selectConsultCountsSinceByHouseIds(@Param("recentSince") LocalDateTime recentSince,
                                                                 @Param("houseIds") List<Long> houseIds);

    List<ChatSessionSummaryVO> selectSessionSummaries(@Param("userId") Long userId,
                                                      @Param("offset") long offset,
                                                      @Param("size") long size);
}
