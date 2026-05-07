package cn.yy.myrent.service.discovery;

import java.util.List;

public record HouseRankResult(
        List<HouseRankedItem> rankedItems,
        List<HouseRankedItem> currentPageItems,
        long total
) {

    public HouseRankResult {
        rankedItems = rankedItems == null ? List.of() : List.copyOf(rankedItems);
        currentPageItems = currentPageItems == null ? List.of() : List.copyOf(currentPageItems);
    }
}
