package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HouseReviewItemVO {

    private Long reviewId;

    private String orderNo;

    private Integer score;

    private String content;

    private String reviewerName;

    private Boolean edited;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
