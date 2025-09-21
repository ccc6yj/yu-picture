package com.yujian.yupicturebackend.mq.PublishSubscribe;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class FanoutConsumer1 {
    private static final String EXCHANGE_NAME = "logs_fanout_exchange";

    public static void main(String[] argv) throws Exception {
        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
        
        // 声明一个临时的、非持久的、独占的、自动删除的队列
        String queueName = channel.queueDeclare().getQueue();
        
        // 将队列绑定到 exchange
        channel.queueBind(queueName, EXCHANGE_NAME, "");

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
        };
        
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }
}