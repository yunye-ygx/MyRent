package cn.yy.myrent.sync.house.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.sync.house.service.HouseHotSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseHotSyncServiceImpl implements HouseHotSyncService {

    private static final int HOUSE_STATUS_AVAILABLE = 1;

    private final HouseMapper houseMapper;
    private final HouseHotService houseHotService;

    @Override
    public void syncHouseChange(Long houseId) {
        if (houseId == null) {
            return;
        }

        House house = houseMapper.selectById(houseId);
        if (house == null || house.getStatus() == null || house.getStatus() != HOUSE_STATUS_AVAILABLE) {
            houseHotService.removeHouseFromAllHotRankings(houseId);
            log.info("removed unavailable house from hot rankings, houseId={}", houseId);
        }
    }
}
