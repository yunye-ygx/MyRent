package cn.yy.myrent.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiRecommendChatVO {

    private String sessionId;

    private String stage;

    private String assistantReply;

    private AiRecommendSlotsVO slots;

    private List<String> missingSlots;

    private AiPreviewVO preview;

    private SmartGuideResultVO recommendation;
}
