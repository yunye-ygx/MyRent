package cn.yy.myrent.sync.house.service;

public interface HouseEsSyncService {

    void upsertByHouseId(Long houseId);

    void deleteByHouseId(Long houseId);
}

