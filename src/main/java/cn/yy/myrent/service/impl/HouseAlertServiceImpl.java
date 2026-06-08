package cn.yy.myrent.service.impl;

import cn.yy.myrent.dto.HouseAlertCreateReqDTO;
import cn.yy.myrent.entity.HouseAlert;
import cn.yy.myrent.mapper.HouseAlertMapper;
import cn.yy.myrent.service.IHouseAlertService;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class HouseAlertServiceImpl extends ServiceImpl<HouseAlertMapper, HouseAlert> implements IHouseAlertService {

    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DISABLED = 0;
    private static final int YUAN_TO_FEN = 100;

    @Override
    public List<HouseAlert> findMatchingAlerts(HouseChangedEvent event) {
        if (event == null
                || event.getPriceYuan() == null
                || event.getRentType() == null
                || isBlank(event.getCity())
                || isBlank(event.getRegion())) {
            return Collections.emptyList();
        }

        // event.getPriceYuan() is in fen (分), house_alert.max_price is in yuan (元)
        int eventPriceYuan = event.getPriceYuan() / YUAN_TO_FEN;

        return this.list(new LambdaQueryWrapper<HouseAlert>()
                .eq(HouseAlert::getStatus, STATUS_ACTIVE)
                .eq(HouseAlert::getCity, event.getCity())
                .eq(HouseAlert::getRegion, event.getRegion())
                .eq(HouseAlert::getRentType, event.getRentType())
                .ge(HouseAlert::getMaxPrice, eventPriceYuan));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HouseAlert createAlert(HouseAlertCreateReqDTO reqDTO, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user id cannot be null");
        }
        if (reqDTO == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        LocalDateTime now = LocalDateTime.now();
        HouseAlert alert = new HouseAlert()
                .setUserId(userId)
                .setCity(reqDTO.getCity().trim())
                .setRegion(reqDTO.getRegion().trim())
                .setMaxPrice(reqDTO.getMaxPrice())
                .setRentType(reqDTO.getRentType())
                .setStatus(STATUS_ACTIVE)
                .setCreateTime(now)
                .setUpdateTime(now);
        this.save(alert);
        return alert;
    }

    @Override
    public List<HouseAlert> listMine(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return this.list(new LambdaQueryWrapper<HouseAlert>()
                .eq(HouseAlert::getUserId, userId)
                .orderByDesc(HouseAlert::getStatus)
                .orderByDesc(HouseAlert::getUpdateTime)
                .orderByDesc(HouseAlert::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableAlert(Long alertId, Long userId) {
        if (alertId == null || userId == null) {
            return;
        }
        this.update(new LambdaUpdateWrapper<HouseAlert>()
                .eq(HouseAlert::getId, alertId)
                .eq(HouseAlert::getUserId, userId)
                .eq(HouseAlert::getStatus, STATUS_ACTIVE)
                .set(HouseAlert::getStatus, STATUS_DISABLED)
                .set(HouseAlert::getUpdateTime, LocalDateTime.now()));
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
