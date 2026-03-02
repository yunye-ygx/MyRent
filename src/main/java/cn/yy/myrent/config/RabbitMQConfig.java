package cn.yy.myrent.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // 1. 定义死信交换机 (托盘) 和 死信队列 (接漏的)
    public static final String ORDER_DL_EXCHANGE = "order.dl.exchange";
    public static final String ORDER_DL_QUEUE = "order.dl.queue";
    public static final String ORDER_DL_ROUTING_KEY = "order.dl.routing.key";

    // 2. 定义普通交换机 和 普通队列 (沙漏)
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_QUEUE = "order.queue";
    public static final String ORDER_ROUTING_KEY = "order.routing.key";

    // ================== 死信组件配置 ==================
    @Bean
    public DirectExchange orderDlExchange() {
        return new DirectExchange(ORDER_DL_EXCHANGE);
    }

    @Bean
    public Queue orderDlQueue() {
        return QueueBuilder.durable(ORDER_DL_QUEUE).build();
    }

    @Bean
    public Binding orderDlBinding(Queue orderDlQueue, DirectExchange orderDlExchange) {
        return BindingBuilder.bind(orderDlQueue).to(orderDlExchange).with(ORDER_DL_ROUTING_KEY);
    }

    // ================== 普通组件配置 (核心秘密在这里) ==================
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderQueue() {
        // 面试考点：普通队列在创建时，必须绑定死信交换机！这样消息死了才知道往哪掉
        Map<String, Object> args = new HashMap<>(2);
        args.put("x-dead-letter-exchange", ORDER_DL_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_DL_ROUTING_KEY);
        // 注意：我们不在队列上设置统一的 TTL，而是在发送消息时设置，方便开发测试(比如开发时设10秒，上线设30分)
        return QueueBuilder.durable(ORDER_QUEUE).withArguments(args).build();
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(ORDER_ROUTING_KEY);
    }
}
