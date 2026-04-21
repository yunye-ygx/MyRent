package cn.yy.myrent.mapper;

import cn.yy.myrent.entity.Review;
import cn.yy.myrent.vo.HouseReviewItemVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewMapper extends BaseMapper<Review> {

    Review selectByOrderNo(@Param("orderNo") String orderNo);

    List<Review> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    List<HouseReviewItemVO> selectLatestByHouseId(@Param("houseId") Long houseId,
                                                  @Param("offset") Long offset,
                                                  @Param("size") Long size);

    Long countByHouseId(@Param("houseId") Long houseId);

    Double avgScoreByHouseId(@Param("houseId") Long houseId);

    int updateContentIfEditable(@Param("id") Long id,
                                @Param("userId") Long userId,
                                @Param("score") Integer score,
                                @Param("content") String content,
                                @Param("updateTime") LocalDateTime updateTime);
}
