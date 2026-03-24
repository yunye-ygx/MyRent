package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.vo.ChatSessionSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    long countSessionSummaries(@Param("userId") Long userId);

    List<ChatSessionSummaryVO> selectSessionSummaries(@Param("userId") Long userId,
                                                      @Param("offset") long offset,
                                                      @Param("size") long size);
}
