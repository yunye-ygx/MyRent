package cn.yy.myrent.consumer;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import cn.yy.myrent.sync.house.service.HouseHotSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class HouseHotSyncConsumer {

    private final ObjectMapper objectMapper;
    private final HouseHotSyncService houseHotSyncService;

    @RabbitListener(queues = RabbitMQConfig.HOUSE_HOT_SYNC_QUEUE, ackMode = "MANUAL")
    public void consume(String body, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            HouseChangedEvent event = objectMapper.readValue(body, HouseChangedEvent.class);
            houseHotSyncService.syncHouseChange(event.getHouseId());
            channel.basicAck(deliveryTag, false);
            log.info("house hot sync message consumed, houseId={}, eventType={}, eventId={}",
                    event.getHouseId(), event.getEventType(), event.getEventId());
        } catch (Exception e) {
            log.error("house hot sync message consume failed, deliveryTag={}, body={}", deliveryTag, body, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
