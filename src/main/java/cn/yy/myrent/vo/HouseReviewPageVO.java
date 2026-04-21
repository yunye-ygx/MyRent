package cn.yy.myrent.vo;

import lombok.Data;

import java.util.List;

@Data
public class HouseReviewPageVO {

    private Double averageScore;

    private Long reviewCount;

    private List<HouseReviewItemVO> records;
}
