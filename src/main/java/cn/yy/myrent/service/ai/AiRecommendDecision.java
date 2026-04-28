package cn.yy.myrent.service.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendDecision {

    private String action;

    private String reply;

    private AiRecommendSlots slots;

    @Builder.Default
    private List<String> missingSlots = new ArrayList<>();
}
