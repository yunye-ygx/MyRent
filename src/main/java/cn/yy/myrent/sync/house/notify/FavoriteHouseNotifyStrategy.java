package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class FavoriteHouseNotifyStrategy implements HouseNotifyStrategy {

    private static final int STATUS_ACTIVE = 1;

    private final HouseFavoriteMapper houseFavoriteMapper;

    public FavoriteHouseNotifyStrategy(HouseFavoriteMapper houseFavoriteMapper) {
        this.houseFavoriteMapper = houseFavoriteMapper;
    }

    @Override
    public boolean supports(HouseChangedEvent event) {
        if (event == null || event.getHouseId() == null || event.getEventType() == null) {
            return false;
        }
        return HouseSyncConstants.EVENT_HOUSE_PRICE_CHANGED.equals(event.getEventType())
                || HouseSyncConstants.EVENT_HOUSE_RENTED.equals(event.getEventType())
                || HouseSyncConstants.EVENT_HOUSE_OFFLINE.equals(event.getEventType())
                || HouseSyncConstants.EVENT_HOUSE_DELETED.equals(event.getEventType());
    }

    @Override
    public List<HouseNotifyItem> buildNotifications(HouseChangedEvent event) {
        List<HouseFavorite> favorites = houseFavoriteMapper.selectList(new QueryWrapper<HouseFavorite>()
                .eq("house_id", event.getHouseId())
                .eq("status", STATUS_ACTIVE));
        if (favorites.isEmpty()) {
            log.info("no active favorites found for house, houseId={}, eventType={}", event.getHouseId(), event.getEventType());
            return Collections.emptyList();
        }
        log.info("found {} active favorites for house, houseId={}, eventType={}", favorites.size(), event.getHouseId(), event.getEventType());

        List<HouseNotifyItem> items = new ArrayList<>(favorites.size());
        for (HouseFavorite favorite : favorites) {
            items.add(new HouseNotifyItem(
                    favorite.getUserId(),
                    event.getEventType(),
                    resolveTitle(event),
                    resolveContent(event),
                    resolveBizKey(event),
                    event.getHouseId(),
                    buildExtraJson(event)
            ));
        }
        return items;
    }

    private String resolveTitle(HouseChangedEvent event) {
        if (HouseSyncConstants.EVENT_HOUSE_PRICE_CHANGED.equals(event.getEventType())) {
            return "Price changed";
        }
        if (HouseSyncConstants.EVENT_HOUSE_RENTED.equals(event.getEventType())) {
            return "House rented";
        }
        if (HouseSyncConstants.EVENT_HOUSE_OFFLINE.equals(event.getEventType())) {
            return "House offline";
        }
        return "House deleted";
    }

    private String resolveContent(HouseChangedEvent event) {
        if (HouseSyncConstants.EVENT_HOUSE_PRICE_CHANGED.equals(event.getEventType())) {
            return "The monthly price changed from "
                    + valueOrZero(event.getPreviousPriceYuan())
                    + " to "
                    + valueOrZero(event.getPriceYuan())
                    + ".";
        }
        if (HouseSyncConstants.EVENT_HOUSE_RENTED.equals(event.getEventType())) {
            return safeHouseTitle(event) + " has been rented.";
        }
        if (HouseSyncConstants.EVENT_HOUSE_OFFLINE.equals(event.getEventType())) {
            return safeHouseTitle(event) + " is now offline.";
        }
        return safeHouseTitle(event) + " is no longer available.";
    }

    private String resolveBizKey(HouseChangedEvent event) {
        if (HouseSyncConstants.EVENT_HOUSE_PRICE_CHANGED.equals(event.getEventType())) {
            return "house:" + event.getHouseId() + ":price:" + valueOrZero(event.getPreviousPriceYuan()) + "->" + valueOrZero(event.getPriceYuan());
        }
        return "house:" + event.getHouseId() + ":type:" + event.getEventType() + ":event:" + event.getEventId();
    }

    private String buildExtraJson(HouseChangedEvent event) {
        if (HouseSyncConstants.EVENT_HOUSE_PRICE_CHANGED.equals(event.getEventType())) {
            return "{\"houseId\":" + event.getHouseId()
                    + ",\"oldPrice\":" + valueOrZero(event.getPreviousPriceYuan())
                    + ",\"newPrice\":" + valueOrZero(event.getPriceYuan()) + "}";
        }
        return "{\"houseId\":" + event.getHouseId() + ",\"eventType\":\"" + event.getEventType() + "\"}";
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeHouseTitle(HouseChangedEvent event) {
        return event == null || event.getHouseTitle() == null || event.getHouseTitle().isBlank()
                ? "This house"
                : event.getHouseTitle();
    }
}
