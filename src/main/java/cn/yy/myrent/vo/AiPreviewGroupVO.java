package cn.yy.myrent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiPreviewGroupVO {

    private String groupKey;

    private String title;

    private String summary;

    private List<String> highlights = new ArrayList<>();

    private Integer sampleCount;

    private List<Long> sampleHouseIds = new ArrayList<>();

    private AiPreviewSlotPatchVO slotPatch;
}
