package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyOrderItemVO {

    private Long id;

    private String orderNo;

    private Long houseId;

    private Integer amount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

    private LocalDateTime paidTime;

    private Long reviewId;

    private Boolean hasReview;

    private Boolean canComplete;

    private Boolean canReview;

    private Boolean canEditReview;
}
