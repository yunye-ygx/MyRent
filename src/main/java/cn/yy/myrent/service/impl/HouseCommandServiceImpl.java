package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseCommandService;
import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.HouseSyncDispatcher;
import cn.yy.myrent.sync.house.classifier.HouseChangeClassificationResult;
import cn.yy.myrent.sync.house.classifier.HouseChangeClassifier;
import cn.yy.myrent.sync.house.model.HouseSyncContext;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class HouseCommandServiceImpl extends ServiceImpl<HouseMapper, House> implements IHouseCommandService {

    private static final Logger log = LoggerFactory.getLogger(HouseCommandServiceImpl.class);

    @Autowired
    private HouseSyncDispatcher houseSyncDispatcher;

    @Autowired
    private HouseChangeClassifier houseChangeClassifier;

    @Autowired
    private INotificationService notificationService;

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

        dispatchCoreEvent(house.getId(), HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, "house-create");
        notificationService.notifyHouseCreated(house);
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
            log.info("房源修改失败，houseId={}", id);
            return false;
        }

        House latestHouse = this.getById(id);
        if (latestHouse == null) {
            latestHouse = reqHouse;
            latestHouse.setId(id);
        }
        notificationService.notifyHouseUpdated(oldHouse, latestHouse);

        log.info("House update classified, houseId={}, changedFields={}, coreChanged={}",
                id,
                classificationResult.getChangedFields(),
                classificationResult.isCoreChanged());

        if (classificationResult.isCoreChanged()) {
            log.info("此次是核心字段更新，触发ES同步，插入本地表，houseId={}", id);
            dispatchCoreEvent(id, HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, "house-update-core");
        } else {
            log.info("此次是非核心字段更新，触发ES同步，houseId={}", id);
            dispatchNormalEventAfterCommit(id, HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, "house-update-normal");
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

        dispatchCoreEvent(id, HouseSyncConstants.EVENT_HOUSE_ES_DELETE, "house-delete");
        notificationService.notifyHouseDeleted(oldHouse);
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

        log.info("House status changed, triggering ES sync, houseId={}", houseId);
        dispatchCoreEvent(houseId, HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, reason);

        House latestHouse = this.getById(houseId);
        if (latestHouse == null) {
            latestHouse = new House()
                    .setId(houseId)
                    .setTitle(oldHouse.getTitle())
                    .setPrice(oldHouse.getPrice())
                    .setStatus(targetStatus)
                    .setVersion(oldHouse.getVersion() == null ? 1 : oldHouse.getVersion() + 1);
        }
        notificationService.notifyHouseUpdated(oldHouse, latestHouse);
        return true;
    }

    private void dispatchCoreEvent(Long houseId, String eventType, String reason) {
        HouseSyncContext context = new HouseSyncContext();
        context.setHouseId(houseId);
        context.setEventType(eventType);
        context.setCoreEvent(true);
        context.setReason(reason);

        log.info("Dispatching ES sync, houseId={}, eventType={}, reason={}", houseId, eventType, reason);
        houseSyncDispatcher.dispatch(context);
    }

    private void dispatchNormalEventAfterCommit(Long houseId, String eventType, String reason) {
        HouseSyncContext context = new HouseSyncContext();
        context.setHouseId(houseId);
        context.setEventType(eventType);
        context.setCoreEvent(false);
        context.setReason(reason);

        log.info("Dispatching ES sync, houseId={}, eventType={}, reason={}", houseId, eventType, reason);
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
}
