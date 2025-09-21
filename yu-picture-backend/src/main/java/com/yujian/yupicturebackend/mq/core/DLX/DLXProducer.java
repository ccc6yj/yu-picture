package com.yujian.yupicturebackend.mq.core.DLX;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class DLXProducer {
    public static void main(String[] args) throws Exception {
        try (Connection connection = ConnectionUtil.getConnection();
             Channel channel = connection.createChannel()) {
            String message = "This message will become a dead letter.";
            channel.basicPublish(DLXSetup.NORMAL_EXCHANGE, "normal_routing_key", null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent: " + message);
        }
    }
}