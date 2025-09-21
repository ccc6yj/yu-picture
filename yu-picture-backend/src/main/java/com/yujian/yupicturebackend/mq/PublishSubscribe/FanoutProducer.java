package com.yujian.yupicturebackend.mq.PublishSubscribe;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class FanoutProducer {
    private static final String EXCHANGE_NAME = "logs_fanout_exchange";

    public static void main(String[] argv) throws Exception {
        try (Connection connection = ConnectionUtil.getConnection();
             Channel channel = connection.createChannel()) {
            
            // 声明 fanout 类型的 exchange
            channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

            String message = "Log message: Something happened!";
            
            // 发布消息到 exchange
            channel.basicPublish(EXCHANGE_NAME, "", null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}