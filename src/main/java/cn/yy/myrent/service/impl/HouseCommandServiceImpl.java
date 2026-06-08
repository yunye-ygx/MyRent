package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.HouseSyncDispatcher;
import cn.yy.myrent.sync.house.classifier.HouseChangeClassificationResult;
import cn.yy.myrent.sync.house.classifier.HouseChangeClassifier;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import cn.yy.myrent.sync.house.model.HouseSyncContext;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class HouseCommandServiceImpl extends ServiceImpl<HouseMapper, House> implements IHouseCommandService {

    private static final Logger log = LoggerFactory.getLogger(HouseCommandServiceImpl.class);

    @Autowired
    private HouseSyncDispatcher houseSyncDispatcher;

    @Autowired
    private HouseChangeClassifier houseChangeClassifier;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createHouseWithSync(House house) {
        if (house == null) {
            return false;
        }

        boolean saved = this.save(house);
        if (!saved || house.getId() == null) {
            return false;
        }

        dispatchCoreEvent(buildEvent(HouseSyncConstants.EVENT_HOUSE_CREATED, house, null), "house-create");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateHouseWithSync(Long id, House reqHouse) {
        if (id == null || reqHouse == null) {
            return false;
        }

        House oldHouse = this.getById(id);
        if (oldHouse == null) {
            return false;
        }

        HouseChangeClassificationResult classificationResult = houseChangeClassifier.classify(id, oldHouse, reqHouse);
        if (!classificationResult.isChanged()) {
            log.info("House update request has no effective change, houseId={}", id);
            return true;
        }

        boolean updated = this.updateById(classificationResult.getUpdatePatch());
        if (!updated) {
            log.info("house update failed, houseId={}", id);
            return false;
        }

        House latestHouse = this.getById(id);
        if (latestHouse == null) {
            latestHouse = reqHouse;
            latestHouse.setId(id);
        }

        log.info("House update classified, houseId={}, changedFields={}, coreChanged={}",
                id,
                classificationResult.getChangedFields(),
                classificationResult.isCoreChanged());

        boolean dispatchedSpecificEvent = false;
        if (isPriceChanged(oldHouse, latestHouse)) {
            dispatchCoreEvent(buildEvent(HouseSyncConstants.EVENT_HOUSE_PRICE_CHANGED, latestHouse, oldHouse), "house-update-price");
            dispatchedSpecificEvent = true;
        }
        if (isStatusChangedTo(oldHouse, latestHouse, 2)) {
            dispatchCoreEvent(buildEvent(HouseSyncConstants.EVENT_HOUSE_RENTED, latestHouse, oldHouse), "house-update-rented");
            dispatchedSpecificEvent = true;
        }
        if (isStatusChangedTo(oldHouse, latestHouse, 0)) {
            dispatchCoreEvent(buildEvent(HouseSyncConstants.EVENT_HOUSE_OFFLINE, latestHouse, oldHouse), "house-update-offline");
            dispatchedSpecificEvent = true;
        }

        if (!dispatchedSpecificEvent && classificationResult.isCoreChanged()) {
            dispatchCoreEvent(buildEvent(HouseSyncConstants.EVENT_HOUSE_UPDATED, latestHouse, oldHouse), "house-update-core");
        } else if (!classificationResult.isCoreChanged()) {
            dispatchNormalEventAfterCommit(buildEvent(HouseSyncConstants.EVENT_HOUSE_UPDATED, latestHouse, oldHouse), "house-update-normal");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteHouseWithSync(Long id) {
        if (id == null) {
            return false;
        }

        House oldHouse = this.getById(id);
        boolean removed = this.removeById(id);
        if (!removed) {
            return false;
        }

        dispatchCoreEvent(buildEvent(HouseSyncConstants.EVENT_HOUSE_DELETED, oldHouse, null), "house-delete");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateHouseStatusWithSync(Long houseId, Integer expectedStatus, Integer targetStatus, String reason) {
        if (houseId == null || targetStatus == null) {
            return false;
        }

        House oldHouse = this.getById(houseId);
        if (oldHouse == null) {
            return false;
        }

        boolean updated = this.lambdaUpdate()
                .eq(House::getId, houseId)
                .eq(expectedStatus != null, House::getStatus, expectedStatus)
                .set(House::getStatus, targetStatus)
                .setSql("`version` = IFNULL(`version`,0) + 1")
                .update();
        if (!updated) {
            return false;
        }

        House latestHouse = this.getById(houseId);
        if (latestHouse == null) {
            latestHouse = new House()
                    .setId(houseId)
                    .setPublisherUserId(oldHouse.getPublisherUserId())
                    .setTitle(oldHouse.getTitle())
                    .setPrice(oldHouse.getPrice())
                    .setCity(oldHouse.getCity())
                    .setRegion(oldHouse.getRegion())
                    .setRentType(oldHouse.getRentType())
                    .setStatus(targetStatus)
                    .setVersion(oldHouse.getVersion() == null ? 1 : oldHouse.getVersion() + 1);
        }
        String eventType = resolveStatusEventType(targetStatus);
        dispatchCoreEvent(buildEvent(eventType == null ? HouseSyncConstants.EVENT_HOUSE_UPDATED : eventType, latestHouse, oldHouse), reason);
        return true;
    }

    private void dispatchCoreEvent(HouseChangedEvent event, String reason) {
        HouseSyncContext context = new HouseSyncContext();
        context.setEvent(event);
        context.setCoreEvent(true);
        context.setReason(reason);

        log.info("Dispatching house event, houseId={}, eventType={}, reason={}",
                context.getHouseId(),
                context.getEventType(),
                reason);
        houseSyncDispatcher.dispatch(context);
    }

    private void dispatchNormalEventAfterCommit(HouseChangedEvent event, String reason) {
        HouseSyncContext context = new HouseSyncContext();
        context.setEvent(event);
        context.setCoreEvent(false);
        context.setReason(reason);

        log.info("Dispatching house event after commit, houseId={}, eventType={}, reason={}",
                context.getHouseId(),
                context.getEventType(),
                reason);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            houseSyncDispatcher.dispatch(context);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                houseSyncDispatcher.dispatch(context);
            }
        });
    }

    private HouseChangedEvent buildEvent(String eventType, House currentHouse, House previousHouse) {
        House source = currentHouse == null ? previousHouse : currentHouse;
        if (source == null) {
            return null;
        }
        return new HouseChangedEvent()
                .setEventId(UUID.randomUUID().toString().replace("-", ""))
                .setEventType(eventType)
                .setOccurredAt(LocalDateTime.now())
                .setHouseId(source.getId())
                .setPublisherUserId(source.getPublisherUserId())
                .setPriceYuan(currentHouse == null ? source.getPrice() : currentHouse.getPrice())
                .setPreviousPriceYuan(previousHouse == null ? null : previousHouse.getPrice())
                .setCity(source.getCity())
                .setRegion(source.getRegion())
                .setRentType(source.getRentType())
                .setVersion(source.getVersion())
                .setHouseTitle(source.getTitle());
    }

    private boolean isPriceChanged(House oldHouse, House newHouse) {
        return oldHouse != null
                && newHouse != null
                && oldHouse.getPrice() != null
                && newHouse.getPrice() != null
                && !oldHouse.getPrice().equals(newHouse.getPrice());
    }

    private boolean isStatusChangedTo(House oldHouse, House newHouse, int targetStatus) {
        return oldHouse != null
                && newHouse != null
                && oldHouse.getStatus() != null
                && newHouse.getStatus() != null
                && !oldHouse.getStatus().equals(newHouse.getStatus())
                && Integer.valueOf(targetStatus).equals(newHouse.getStatus());
    }

    private String resolveStatusEventType(Integer status) {
        if (Integer.valueOf(2).equals(status)) {
            return HouseSyncConstants.EVENT_HOUSE_RENTED;
        }
        if (Integer.valueOf(0).equals(status)) {
            return HouseSyncConstants.EVENT_HOUSE_OFFLINE;
        }
        return null;
    }
}
