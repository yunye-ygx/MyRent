package cn.yy.myrent.service;

import cn.yy.myrent.dto.ReviewCreateReqDTO;
import cn.yy.myrent.dto.ReviewUpdateReqDTO;
import cn.yy.myrent.entity.Review;
import cn.yy.myrent.vo.HouseReviewPageVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface IReviewService extends IService<Review> {

    Review createReview(ReviewCreateReqDTO req);

    void updateReview(Long reviewId, ReviewUpdateReqDTO req);

    HouseReviewPageVO pageHouseReviews(Long houseId, long current, long size);

    Map<String, Review> mapByOrderNos(List<String> orderNos);

    Review getReviewDetail(Long reviewId);
}
