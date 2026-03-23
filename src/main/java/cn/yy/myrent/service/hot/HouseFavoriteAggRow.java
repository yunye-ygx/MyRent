package cn.yy.myrent.service.hot;

import lombok.Data;

@Data
public class HouseFavoriteAggRow {

    private Long houseId;

    private Long totalFavoriteCount;

    private Long recentFavoriteCount;
}
