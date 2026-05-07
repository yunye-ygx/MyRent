package cn.yy.myrent.service.discovery;

import java.util.List;

public interface HouseRankingService {

    HouseRankResult rank(List<HouseRecallCandidate> candidates, HouseRankQuery query);
}
