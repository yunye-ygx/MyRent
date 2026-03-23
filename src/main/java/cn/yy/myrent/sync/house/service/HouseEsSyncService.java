package cn.yy.myrent.sync.house.service;

import java.time.LocalDateTime;

public interface HouseEsSyncService {

    void upsertByHouseId(Long houseId);

    void deleteByHouseId(Long houseId);

    void compensateByCreateTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    int rebuildAllFromDb();
}
