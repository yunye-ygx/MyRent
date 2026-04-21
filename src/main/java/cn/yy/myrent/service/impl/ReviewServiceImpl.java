package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.ReviewCreateReqDTO;
import cn.yy.myrent.dto.ReviewUpdateReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Review;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.ReviewMapper;
import cn.yy.myrent.service.IReviewService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Review createReview(ReviewCreateReqDTO req) {
        Long currentUserId = UserContext.requireCurrentUserId();
        validateReviewPayload(req == null ? null : req.getScore(), req == null ? null : req.getContent());

        String orderNo = req.getOrderNo().trim();
        Order order = orderMapper.selectOrderNo(orderNo);
        if (order == null || !currentUserId.equals(order.getUserId())) {
            throw new RuntimeException("order not found");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.COMPLETED) {
            throw new RuntimeException("order is not reviewable");
        }
        if (reviewMapper.selectByOrderNo(orderNo) != null) {
            throw new RuntimeException("review already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        Review review = new Review();
        review.setOrderId(order.getId());
        review.setOrderNo(orderNo);
        review.setHouseId(order.getHouseId());
        review.setUserId(currentUserId);
        review.setScore(req.getScore());
        review.setContent(req.getContent().trim());
        review.setEditCount(0);
        review.setCreateTime(now);
        review.setUpdateTime(now);

        if (reviewMapper.insert(review) <= 0) {
            throw new RuntimeException("review create failed");
        }
        int updated = orderMapper.markReviewedIfCompleted(
                orderNo,
                currentUserId,
                OrderStatus.COMPLETED,
                OrderStatus.REVIEWED,
                now);
        if (updated <= 0) {
            throw new RuntimeException("review create failed");
        }
        return review;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReview(Long reviewId, ReviewUpdateReqDTO req) {
        Long currentUserId = UserContext.requireCurrentUserId();
        validateReviewPayload(req == null ? null : req.getScore(), req == null ? null : req.getContent());

        Review review = reviewMapper.selectById(reviewId);
        if (review == null || !currentUserId.equals(review.getUserId())) {
            throw new RuntimeException("review not found");
        }
        if (review.getEditCount() != null && review.getEditCount() > 0) {
            throw new RuntimeException("review cannot be edited anymore");
        }

        int updated = reviewMapper.updateContentIfEditable(
                reviewId,
                currentUserId,
                req.getScore(),
                req.getContent().trim(),
                LocalDateTime.now());
        if (updated <= 0) {
            throw new RuntimeException("review update failed");
        }
    }

    @Override
    public Review getReviewDetail(Long reviewId) {
        Long currentUserId = UserContext.requireCurrentUserId();
        Review review = reviewMapper.selectById(reviewId);
        if (review == null || !currentUserId.equals(review.getUserId())) {
            throw new RuntimeException("review not found");
        }
        return review;
    }

    private void validateReviewPayload(Integer score, String content) {
        if (score == null || score < 1 || score > 5) {
            throw new RuntimeException("review score is invalid");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("review content is required");
        }
    }
}
