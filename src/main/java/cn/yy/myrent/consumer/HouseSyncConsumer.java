package cn.yy.myrent.consumer;

import cn.yy.myrent.config.RabbitMQConfig;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.model.HouseSyncMessage;
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
            HouseSyncMessage syncMessage = objectMapper.readValue(body, HouseSyncMessage.class);
            Long houseId = syncMessage.getHouseId();
            String eventType = syncMessage.getEventType();

            if (HouseSyncConstants.EVENT_HOUSE_ES_DELETE.equals(eventType)) {
                houseEsSyncService.deleteByHouseId(houseId);
            } else if (HouseSyncConstants.EVENT_HOUSE_ES_UPSERT.equals(eventType)) {
                houseEsSyncService.upsertByHouseId(houseId);
            } else {
                log.warn("未知房源同步事件，忽略处理，eventType={}, messageId={}", eventType, syncMessage.getMessageId());
            }

            channel.basicAck(deliveryTag, false);
            log.info("房源同步消息消费成功，houseId={}, eventType={}, messageId={}",
                    syncMessage.getHouseId(),
                    syncMessage.getEventType(),
                    syncMessage.getMessageId());
        } catch (Exception e) {
            log.error("房源同步消息消费失败，deliveryTag={}, body={}", deliveryTag, body, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}

