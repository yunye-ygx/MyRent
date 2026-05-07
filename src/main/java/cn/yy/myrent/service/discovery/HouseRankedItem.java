package cn.yy.myrent.service.discovery;

import cn.yy.myrent.entity.House;

import java.util.List;

public record HouseRankedItem(
        House house,
        double score,
        HouseScoreBreakdown scoreBreakdown,
        List<HouseReasonCode> reasonCodes
) {

    public HouseRankedItem {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
