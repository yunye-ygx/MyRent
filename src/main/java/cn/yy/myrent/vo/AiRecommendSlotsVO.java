package cn.yy.myrent.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiRecommendSlotsVO {

    private String city;

    private String locationName;

    private Integer budgetYuan;

    private String budgetScope;

    private String rentMode;

    private String priority;

    private List<String> preferences;
}
