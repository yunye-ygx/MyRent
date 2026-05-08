package cn.yy.myrent.consumer;

import cn.yy.myrent.sync.house.model.HouseSyncMessage;
import cn.yy.myrent.sync.house.service.HouseHotSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseHotSyncConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HouseHotSyncService houseHotSyncService;

    @Mock
    private Channel channel;

    @InjectMocks
    private HouseHotSyncConsumer consumer;

    @Test
    void consumeShouldSyncHouseChangeAndAck() throws Exception {
        HouseSyncMessage syncMessage = new HouseSyncMessage();
        syncMessage.setHouseId(7L);
        syncMessage.setEventType("HOUSE_ES_UPSERT");
        syncMessage.setMessageId("m1");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(99L);
        Message message = new Message(new byte[0], properties);
        String body = "{\"houseId\":7}";
        when(objectMapper.readValue(body, HouseSyncMessage.class)).thenReturn(syncMessage);

        consumer.consume(body, message, channel);

        verify(houseHotSyncService).syncHouseChange(7L);
        verify(channel).basicAck(99L, false);
    }

    @Test
    void consumeShouldNackWhenSyncFails() throws Exception {
        HouseSyncMessage syncMessage = new HouseSyncMessage();
        syncMessage.setHouseId(7L);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(99L);
        Message message = new Message(new byte[0], properties);
        String body = "{\"houseId\":7}";
        when(objectMapper.readValue(body, HouseSyncMessage.class)).thenReturn(syncMessage);
        doThrow(new RuntimeException("redis down")).when(houseHotSyncService).syncHouseChange(7L);

        consumer.consume(body, message, channel);

        verify(channel).basicNack(99L, false, true);
    }
}
