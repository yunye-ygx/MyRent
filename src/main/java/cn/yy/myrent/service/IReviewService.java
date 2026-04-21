package cn.yy.myrent.service;

import cn.yy.myrent.dto.ReviewCreateReqDTO;
import cn.yy.myrent.dto.ReviewUpdateReqDTO;
import cn.yy.myrent.entity.Review;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IReviewService extends IService<Review> {

    Review createReview(ReviewCreateReqDTO req);

    void updateReview(Long reviewId, ReviewUpdateReqDTO req);

    Review getReviewDetail(Long reviewId);
}
