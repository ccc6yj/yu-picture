package com.yujian.yupicturebackend.mq.Routing;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class DirectProducer {
    private static final String EXCHANGE_NAME = "logs_direct_exchange";

    public static void main(String[] argv) throws Exception {
        try (Connection connection = ConnectionUtil.getConnection();
             Channel channel = connection.createChannel()) {
            
            channel.exchangeDeclare(EXCHANGE_NAME, "direct");

            // 定义不同的日志级别作为 routingKey
            String[] severities = {"info", "warning", "error"};
            
            for (String severity : severities) {
                String message = "This is a log with severity: " + severity;
                channel.basicPublish(EXCHANGE_NAME, severity, null, message.getBytes(StandardCharsets.UTF_8));
                System.out.println(" [x] Sent '" + severity + "':'" + message + "'");
            }
        }
    }
}