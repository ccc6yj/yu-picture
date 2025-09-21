package com.yujian.yupicturebackend.mq.core.PriorityQueue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class PriorityConsumer {
    private static final String PRIORITY_QUEUE = "priority_queue";

    public static void main(String[] args) throws Exception {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();
        
        System.out.println(" [*] Waiting for priority messages.");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "' with priority: " + delivery.getProperties().getPriority());
            try {
                Thread.sleep(500); // 模拟处理
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        channel.basicConsume(PRIORITY_QUEUE, false, deliverCallback, consumerTag -> {});
    }
}