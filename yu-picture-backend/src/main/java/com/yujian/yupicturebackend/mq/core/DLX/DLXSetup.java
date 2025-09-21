package com.yujian.yupicturebackend.mq.core.DLX;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.util.HashMap;
import java.util.Map;

public class DLXSetup {
    public static final String NORMAL_EXCHANGE = "normal_exchange";
    public static final String NORMAL_QUEUE = "normal_queue";
    public static final String DLX_EXCHANGE = "dlx_exchange";
    public static final String DLX_QUEUE = "dlx_queue";

    public static void main(String[] args) throws Exception {
        try (Connection connection = ConnectionUtil.getConnection();
             Channel channel = connection.createChannel()) {
            
            // 1. 声明死信交换机和死信队列
            channel.exchangeDeclare(DLX_EXCHANGE, "direct");
            channel.queueDeclare(DLX_QUEUE, true, false, false, null);
            channel.queueBind(DLX_QUEUE, DLX_EXCHANGE, "dlx_routing_key");
            System.out.println("DLX setup complete.");

            // 2. 声明正常业务交换机和队列，并为其绑定死信交换机
            Map<String, Object> queueArgs = new HashMap<>();
            queueArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);
            queueArgs.put("x-dead-letter-routing-key", "dlx_routing_key"); // 可选
            // 也可以在这里设置消息TTL
            // queueArgs.put("x-message-ttl", 10000); 

            channel.exchangeDeclare(NORMAL_EXCHANGE, "direct");
            channel.queueDeclare(NORMAL_QUEUE, true, false, false, queueArgs);
            channel.queueBind(NORMAL_QUEUE, NORMAL_EXCHANGE, "normal_routing_key");
            System.out.println("Normal queue setup complete.");
        }
    }
}