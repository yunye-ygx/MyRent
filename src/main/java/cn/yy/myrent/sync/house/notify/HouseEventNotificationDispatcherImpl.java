package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.service.INotificationService;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class HouseEventNotificationDispatcherImpl implements HouseEventNotificationDispatcher {

    private final List<HouseNotifyStrategy> strategies;
    private final INotificationService notificationService;

    public HouseEventNotificationDispatcherImpl(List<HouseNotifyStrategy> strategies,
                                                INotificationService notificationService) {
        this.strategies = strategies;
        this.notificationService = notificationService;
    }

    @Override
    public void dispatch(HouseChangedEvent event) {
        if (event == null || strategies == null || strategies.isEmpty()) {
            log.warn("notification dispatch skipped, event={}, strategyCount={}", event, strategies == null ? 0 : strategies.size());
            return;
        }

        List<HouseNotifyItem> allItems = new ArrayList<>();
        boolean anyMatched = false;
        for (HouseNotifyStrategy strategy : strategies) {
            if (strategy.supports(event)) {
                anyMatched = true;
                log.info("notification strategy matched, strategy={}, houseId={}, eventType={}",
                        strategy.getClass().getSimpleName(), event.getHouseId(), event.getEventType());
                allItems.addAll(strategy.buildNotifications(event));
            }
        }
        if (!anyMatched) {
            log.info("no notification strategy matched, houseId={}, eventType={}, publisherUserId={}",
                    event.getHouseId(), event.getEventType(), event.getPublisherUserId());
            return;
        }

        Set<Long> notifiedUserIds = new LinkedHashSet<>();
        int createdCount = 0;
        for (HouseNotifyItem item : allItems) {
            if (notifiedUserIds.contains(item.userId())) {
                log.info("duplicate notification skipped, userId={}, houseId={}", item.userId(), event.getHouseId());
                continue;
            }
            notifiedUserIds.add(item.userId());
            notificationService.createHouseNotification(
                    item.userId(), item.type(), item.title(), item.content(),
                    item.bizKey(), item.targetId(), item.extraJson()
            );
            createdCount++;
        }
        log.info("notification dispatch finished, houseId={}, eventType={}, totalItems={}, created={}",
                event.getHouseId(), event.getEventType(), allItems.size(), createdCount);
    }
}
