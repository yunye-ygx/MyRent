package cn.yy.myrent.service;

import cn.yy.myrent.entity.House;

public interface IHouseCommandService {

    boolean createHouseWithSync(House house);

    boolean updateHouseWithSync(Long id, House house);

    boolean deleteHouseWithSync(Long id);

    boolean updateHouseStatusWithSync(Long houseId, Integer expectedStatus, Integer targetStatus, String reason);
}

