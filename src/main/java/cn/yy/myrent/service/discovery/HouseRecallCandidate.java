package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;

public record HouseRecallCandidate(
        House house,
        HouseRecallMatchTier matchTier,
        HouseRecallEvidence recallEvidence
) {
}
