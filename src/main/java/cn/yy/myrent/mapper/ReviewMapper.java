package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.Review;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface ReviewMapper extends BaseMapper<Review> {

    Review selectByOrderNo(@Param("orderNo") String orderNo);

    int updateContentIfEditable(@Param("id") Long id,
                                @Param("userId") Long userId,
                                @Param("score") Integer score,
                                @Param("content") String content,
                                @Param("updateTime") LocalDateTime updateTime);
}
