package cn.yy.myrent.service.hot;

import lombok.Data;

@Data
public class HouseHotScoreSnapshot {

    private Long houseId;

    private long totalFavoriteCount;

    private long recentFavoriteCount;

    private long recentConsultCount;

    private long recentReplyCount;

    private double hotScore;
}
