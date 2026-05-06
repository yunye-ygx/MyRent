package cn.yy.myrent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRecommendInteractionSlotPatchDTO {

    private String priority;

    private String rentMode;

    private Integer budgetYuan;

    private String budgetScope;

    private String locationName;

    private List<String> preferences = new ArrayList<>();
}
