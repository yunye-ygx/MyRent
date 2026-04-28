package cn.yy.myrent.service.ai;

public interface AiRecommendStateStore {

    AiRecommendSessionState loadOrCreate(Long userId);

    void save(AiRecommendSessionState state);

    void reset(Long userId);
}
