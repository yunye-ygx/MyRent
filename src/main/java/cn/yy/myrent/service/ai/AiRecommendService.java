package cn.yy.myrent.service.ai;

import cn.yy.myrent.dto.AiRecommendChatReqDTO;
import cn.yy.myrent.vo.AiRecommendChatVO;

public interface AiRecommendService {

    AiRecommendChatVO getOrCreateSession(Long userId);

    AiRecommendChatVO chat(Long userId, AiRecommendChatReqDTO reqDTO);

    AiRecommendChatVO reset(Long userId);
}
