package cn.yy.myrent.consumer;

import cn.yy.myrent.sync.house.model.HouseChangedEvent;
import cn.yy.myrent.sync.house.notify.HouseEventNotificationDispatcher;
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
class HouseNotifyConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HouseEventNotificationDispatcher dispatcher;

    @Mock
    private Channel channel;

    @InjectMocks
    private HouseNotifyConsumer consumer;

    @Test
    void consumeShouldDispatchNotificationEventAndAck() throws Exception {
        HouseChangedEvent event = new HouseChangedEvent().setHouseId(7L).setEventType("HOUSE_CREATED").setEventId("evt-1");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(99L);
        Message message = new Message(new byte[0], properties);
        String body = "{\"houseId\":7}";
        when(objectMapper.readValue(body, HouseChangedEvent.class)).thenReturn(event);

        consumer.consume(body, message, channel);

        verify(dispatcher).dispatch(event);
        verify(channel).basicAck(99L, false);
    }

    @Test
    void consumeShouldNackWhenNotificationDispatchFails() throws Exception {
        HouseChangedEvent event = new HouseChangedEvent().setHouseId(7L).setEventType("HOUSE_CREATED").setEventId("evt-1");
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(99L);
        Message message = new Message(new byte[0], properties);
        String body = "{\"houseId\":7}";
        when(objectMapper.readValue(body, HouseChangedEvent.class)).thenReturn(event);
        doThrow(new RuntimeException("db down")).when(dispatcher).dispatch(event);

        consumer.consume(body, message, channel);

        verify(channel).basicNack(99L, false, true);
    }
}
