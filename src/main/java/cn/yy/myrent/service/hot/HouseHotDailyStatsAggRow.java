package cn.yy.myrent.service.hot;

public record HouseHotDailyStatsAggRow(Long houseId,
                                       Long recentBrowseCount,
                                       Long recentFavoriteCount,
                                       Long recentConsultCount) {
}
