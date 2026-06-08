package cn.yy.myrent.service;

import cn.yy.myrent.entity.HouseAlert;
import cn.yy.myrent.dto.HouseAlertCreateReqDTO;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IHouseAlertService extends IService<HouseAlert> {

    List<HouseAlert> findMatchingAlerts(HouseChangedEvent event);

    HouseAlert createAlert(HouseAlertCreateReqDTO reqDTO, Long userId);

    List<HouseAlert> listMine(Long userId);

    void disableAlert(Long alertId, Long userId);
}
