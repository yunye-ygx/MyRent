package cn.yy.myrent.consumer;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class HouseSyncConsumer {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HouseEsSyncService houseEsSyncService;

    @RabbitListener(queues = RabbitMQConfig.HOUSE_SYNC_QUEUE, ackMode = "MANUAL")
    public void consume(String body, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            HouseChangedEvent event = objectMapper.readValue(body, HouseChangedEvent.class);
            Long houseId = event.getHouseId();
            String eventType = event.getEventType();

            if (HouseSyncConstants.EVENT_HOUSE_DELETED.equals(eventType)) {
                houseEsSyncService.deleteByHouseId(houseId);
            } else if (houseId != null) {
                houseEsSyncService.upsertByHouseId(houseId);
            } else {
                log.warn("unknown house sync event ignored, eventType={}, eventId={}", eventType, event.getEventId());
            }

            channel.basicAck(deliveryTag, false);
            log.info("house sync message consumed, houseId={}, eventType={}, eventId={}",
                    event.getHouseId(),
                    event.getEventType(),
                    event.getEventId());
        } catch (Exception e) {
            log.error("house sync message consume failed, deliveryTag={}, body={}", deliveryTag, body, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
