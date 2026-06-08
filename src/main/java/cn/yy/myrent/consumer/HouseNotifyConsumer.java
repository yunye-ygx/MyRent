package cn.yy.myrent.consumer;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import cn.yy.myrent.sync.house.notify.HouseEventNotificationDispatcher;
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
public class HouseNotifyConsumer {

    private final ObjectMapper objectMapper;
    private final HouseEventNotificationDispatcher dispatcher;

    @RabbitListener(queues = RabbitMQConfig.HOUSE_NOTIFY_QUEUE, ackMode = "MANUAL")
    public void consume(String body, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            HouseChangedEvent event = objectMapper.readValue(body, HouseChangedEvent.class);
            dispatcher.dispatch(event);
            channel.basicAck(deliveryTag, false);
            log.info("house notification message consumed, houseId={}, eventType={}, eventId={}",
                    event.getHouseId(), event.getEventType(), event.getEventId());
        } catch (Exception e) {
            log.error("house notification message consume failed, deliveryTag={}, body={}", deliveryTag, body, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
