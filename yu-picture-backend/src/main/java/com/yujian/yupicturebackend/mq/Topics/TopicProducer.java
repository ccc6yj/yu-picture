package com.yujian.yupicturebackend.mq.Topics;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class TopicProducer {
    private static final String EXCHANGE_NAME = "logs_topic_exchange";

    public static void main(String[] argv) throws Exception {
        try (Connection connection = ConnectionUtil.getConnection();
             Channel channel = connection.createChannel()) {
            
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");

            String[] routingKeys = {
                "kern.critical", "kern.info",
                "auth.critical", "auth.warning",
                "cron.info", "app.user.login"
            };

            for (String routingKey : routingKeys) {
                String message = "Message with routing key: " + routingKey;
                channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes(StandardCharsets.UTF_8));
                System.out.println(" [x] Sent '" + routingKey + "':'" + message + "'");
            }
        }
    }
}