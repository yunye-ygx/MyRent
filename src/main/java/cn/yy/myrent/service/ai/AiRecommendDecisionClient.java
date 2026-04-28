package cn.yy.myrent.service.ai;

public interface AiRecommendDecisionClient {

    AiRecommendDecision decide(AiRecommendSessionState sessionState, String userMessage);
}
