package cn.yy.myrent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiPreviewVO {

    private String locationName;

    private Integer candidateCount;

    private List<AiPreviewGroupVO> groups = new ArrayList<>();
}
