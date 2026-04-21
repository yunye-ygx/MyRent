package cn.yy.myrent.service.impl;

import cn.yy.myrent.common.OrderStatus;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.ReviewCreateReqDTO;
import cn.yy.myrent.dto.ReviewUpdateReqDTO;
import cn.yy.myrent.entity.Order;
import cn.yy.myrent.entity.Review;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.ReviewMapper;
import cn.yy.myrent.vo.HouseReviewItemVO;
import cn.yy.myrent.vo.HouseReviewPageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void createReviewShouldMoveCompletedOrderToReviewed() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("ORDER-REVIEW-1");
        order.setUserId(2001L);
        order.setHouseId(301L);
        order.setStatus(OrderStatus.COMPLETED);

        when(orderMapper.selectOrderNo("ORDER-REVIEW-1")).thenReturn(order);
        when(reviewMapper.selectByOrderNo("ORDER-REVIEW-1")).thenReturn(null);
        when(reviewMapper.insert(any(Review.class))).thenReturn(1);
        when(orderMapper.markReviewedIfCompleted(eq("ORDER-REVIEW-1"), eq(2001L), eq(OrderStatus.COMPLETED), eq(OrderStatus.REVIEWED), any()))
                .thenReturn(1);

        ReviewCreateReqDTO req = new ReviewCreateReqDTO();
        req.setOrderNo("ORDER-REVIEW-1");
        req.setScore(5);
        req.setContent("房源整体不错，首版评论链路验证通过。");

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(2001L);
            Review review = reviewService.createReview(req);
            assertEquals("ORDER-REVIEW-1", review.getOrderNo());
            assertEquals(0, review.getEditCount());
        }
    }

    @Test
    void createReviewShouldRejectDuplicateOrderReview() {
        Order order = new Order();
        order.setOrderNo("ORDER-REVIEW-2");
        order.setUserId(2002L);
        order.setStatus(OrderStatus.COMPLETED);

        Review existing = new Review();
        existing.setId(9L);
        existing.setOrderNo("ORDER-REVIEW-2");

        when(orderMapper.selectOrderNo("ORDER-REVIEW-2")).thenReturn(order);
        when(reviewMapper.selectByOrderNo("ORDER-REVIEW-2")).thenReturn(existing);

        ReviewCreateReqDTO req = new ReviewCreateReqDTO();
        req.setOrderNo("ORDER-REVIEW-2");
        req.setScore(4);
        req.setContent("重复评论应被拒绝。");

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(2002L);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> reviewService.createReview(req));
            assertEquals("review already exists", ex.getMessage());
        }
    }

    @Test
    void updateReviewShouldAllowOneEditOnly() {
        Review review = new Review();
        review.setId(11L);
        review.setOrderNo("ORDER-REVIEW-3");
        review.setUserId(2003L);
        review.setEditCount(0);

        when(reviewMapper.selectById(11L)).thenReturn(review);
        when(reviewMapper.updateContentIfEditable(eq(11L), eq(2003L), eq(3), eq("修改后的评价内容"), any()))
                .thenReturn(1);

        ReviewUpdateReqDTO req = new ReviewUpdateReqDTO();
        req.setScore(3);
        req.setContent("修改后的评价内容");

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(2003L);
            assertDoesNotThrow(() -> reviewService.updateReview(11L, req));
        }
    }

    @Test
    void updateReviewShouldRejectSecondEdit() {
        Review review = new Review();
        review.setId(12L);
        review.setUserId(2004L);
        review.setEditCount(1);

        when(reviewMapper.selectById(12L)).thenReturn(review);

        ReviewUpdateReqDTO req = new ReviewUpdateReqDTO();
        req.setScore(2);
        req.setContent("第二次修改应该失败。");

        try (MockedStatic<UserContext> mockedUserContext = Mockito.mockStatic(UserContext.class)) {
            mockedUserContext.when(UserContext::requireCurrentUserId).thenReturn(2004L);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> reviewService.updateReview(12L, req));
            assertEquals("review cannot be edited anymore", ex.getMessage());
        }
    }

    @Test
    void pageHouseReviewsShouldReturnSummaryAndLatestRecords() {
        HouseReviewItemVO item = new HouseReviewItemVO();
        item.setReviewId(21L);
        item.setOrderNo("ORDER-REVIEW-LIST-1");
        item.setScore(5);
        item.setContent("评论列表应返回最新记录。");
        item.setReviewerName("测试用户");
        item.setEdited(false);
        item.setCreateTime(LocalDateTime.of(2026, 4, 21, 11, 0, 0));
        item.setUpdateTime(LocalDateTime.of(2026, 4, 21, 11, 0, 0));

        when(reviewMapper.avgScoreByHouseId(301L)).thenReturn(4.5D);
        when(reviewMapper.countByHouseId(301L)).thenReturn(2L);
        when(reviewMapper.selectLatestByHouseId(301L, 0L, 5L)).thenReturn(List.of(item));

        HouseReviewPageVO result = reviewService.pageHouseReviews(301L, 1, 5);

        assertEquals(4.5D, result.getAverageScore());
        assertEquals(2L, result.getReviewCount());
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getReviewerName());
        assertFalse(result.getRecords().get(0).getEdited());
    }
}
