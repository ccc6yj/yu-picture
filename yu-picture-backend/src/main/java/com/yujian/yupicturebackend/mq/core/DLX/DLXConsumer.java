package com.yujian.yupicturebackend.mq.core.DLX;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class DLXConsumer {
    public static void main(String[] args) throws Exception {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        System.out.println(" [*] Waiting for messages in normal queue.");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received from normal queue: '" + message + "'. Rejecting it.");
            // 拒绝消息，并且不重新入队，使其进入死信队列
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
        };
        channel.basicConsume(DLXSetup.NORMAL_QUEUE, false, deliverCallback, consumerTag -> {});
    }
}