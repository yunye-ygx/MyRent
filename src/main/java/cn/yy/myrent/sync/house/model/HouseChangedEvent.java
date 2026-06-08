package cn.yy.myrent.sync.house.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class HouseChangedEvent {

    private String eventId;

    private String eventType;

    private LocalDateTime occurredAt;

    private Long houseId;

    private Long publisherUserId;

    private Integer priceYuan;

    private Integer previousPriceYuan;

    private String city;

    private String region;

    private Integer rentType;

    private Integer version;

    private String houseTitle;
}
