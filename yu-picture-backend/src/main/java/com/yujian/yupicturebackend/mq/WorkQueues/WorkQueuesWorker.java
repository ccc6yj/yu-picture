package com.yujian.yupicturebackend.mq.WorkQueues;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class WorkQueuesWorker {
    private final static String QUEUE_NAME = "work_queue";

    public static void main(String[] argv) throws Exception {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        
        // 设置预取计数为1，这样RabbitMQ在收到消费者确认之前，不会向该消费者发送超过1条消息
        // 这实现了公平分发（Fair Dispatch）
        int prefetchCount = 1;
        channel.basicQos(prefetchCount);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
            try {
                // 模拟耗时任务
                for (char ch : message.toCharArray()) {
                    if (ch == '.') {
                        Thread.sleep(1000);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                System.out.println(" [x] Done with " + message);
                // 手动发送消息确认
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        // 关闭自动确认
        boolean autoAck = false;
        channel.basicConsume(QUEUE_NAME, autoAck, deliverCallback, consumerTag -> {});
    }
}