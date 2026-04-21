package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.dto.ReviewCreateReqDTO;
import cn.yy.myrent.dto.ReviewUpdateReqDTO;
import cn.yy.myrent.entity.Review;
import cn.yy.myrent.service.IReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/review")
@Tag(name = "评论管理")
@Slf4j
@RequiredArgsConstructor
public class ReviewController {

    private final IReviewService reviewService;

    @PostMapping
    @Operation(summary = "创建评论")
    public Result<Long> create(@Valid @RequestBody ReviewCreateReqDTO req) {
        try {
            Review review = reviewService.createReview(req);
            return Result.success(review.getId());
        } catch (IllegalStateException e) {
            return Result.error(401, e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage() == null ? "评论创建失败" : e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改评论")
    public Result<Void> update(@PathVariable("id") Long id, @Valid @RequestBody ReviewUpdateReqDTO req) {
        try {
            reviewService.updateReview(id, req);
            return Result.success();
        } catch (IllegalStateException e) {
            return Result.error(401, e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage() == null ? "评论修改失败" : e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询评论详情")
    public Result<Review> getById(@PathVariable("id") Long id) {
        try {
            return Result.success(reviewService.getReviewDetail(id));
        } catch (IllegalStateException e) {
            return Result.error(401, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("query review detail failed, id={}", id, e);
            return Result.error(e.getMessage() == null ? "评论不存在" : e.getMessage());
        }
    }
}
