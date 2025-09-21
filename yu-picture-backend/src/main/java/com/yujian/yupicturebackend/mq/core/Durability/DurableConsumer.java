package com.yujian.yupicturebackend.mq.core.Durability;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class DurableConsumer {
    private static final String DURABLE_QUEUE = "durable_task_queue";

    public static void main(String[] args) throws Exception {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        boolean durable = true;
        channel.queueDeclare(DURABLE_QUEUE, durable, false, false, null);
        System.out.println(" [*] Waiting for durable messages.");

        channel.basicQos(1);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
            try {
                Thread.sleep(1000); // 模拟工作
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                System.out.println(" [x] Done");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        channel.basicConsume(DURABLE_QUEUE, false, deliverCallback, consumerTag -> {});
    }
}