package cn.yy.myrent.service.ai;

import cn.yy.myrent.vo.AiPreviewVO;

public interface AiPreviewService {

    AiPreviewVO build(String locationName, Integer budgetYuan, String budgetScope, String rentMode);
}
