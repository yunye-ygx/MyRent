package cn.yy.myrent.sync.house.notify;

import cn.yy.myrent.common.NotificationType;
import cn.yy.myrent.entity.PublisherFollow;
import cn.yy.myrent.mapper.PublisherFollowMapper;
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
public class PublisherFollowNotifyStrategy implements HouseNotifyStrategy {

    private static final int STATUS_ACTIVE = 1;

    private final PublisherFollowMapper publisherFollowMapper;

    public PublisherFollowNotifyStrategy(PublisherFollowMapper publisherFollowMapper) {
        this.publisherFollowMapper = publisherFollowMapper;
    }

    @Override
    public boolean supports(HouseChangedEvent event) {
        return event != null
                && HouseSyncConstants.EVENT_HOUSE_CREATED.equals(event.getEventType())
                && event.getPublisherUserId() != null
                && event.getHouseId() != null;
    }

    @Override
    public List<HouseNotifyItem> buildNotifications(HouseChangedEvent event) {
        List<PublisherFollow> follows = publisherFollowMapper.selectList(new QueryWrapper<PublisherFollow>()
                .eq("publisher_user_id", event.getPublisherUserId())
                .eq("status", STATUS_ACTIVE));
        if (follows.isEmpty()) {
            log.info("no active followers found for publisher, publisherUserId={}, houseId={}",
                    event.getPublisherUserId(), event.getHouseId());
            return Collections.emptyList();
        }
        log.info("found {} active followers for publisher, publisherUserId={}, houseId={}",
                follows.size(), event.getPublisherUserId(), event.getHouseId());

        List<HouseNotifyItem> items = new ArrayList<>(follows.size());
        for (PublisherFollow follow : follows) {
            items.add(new HouseNotifyItem(
                    follow.getUserId(),
                    NotificationType.PUBLISHER_NEW_HOUSE,
                    "Publisher posted a new house",
                    safeHouseTitle(event) + " is now available.",
                    "publisher:" + event.getPublisherUserId() + ":house:" + event.getHouseId() + ":new",
                    event.getHouseId(),
                    "{\"houseId\":" + event.getHouseId() + ",\"publisherUserId\":" + event.getPublisherUserId() + "}"
            ));
        }
        return items;
    }

    private String safeHouseTitle(HouseChangedEvent event) {
        return event == null || event.getHouseTitle() == null || event.getHouseTitle().isBlank()
                ? "This house"
                : event.getHouseTitle();
    }
}
