package com.yujian.yupicturebackend.mq.Routing;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class DirectConsumer1 {
    private static final String EXCHANGE_NAME = "logs_direct_exchange";

    public static void main(String[] argv) throws Exception {
        if (argv.length < 1) {
            System.err.println("Usage: DirectConsumer [info] [warning] [error]");
            System.exit(1);
        }

        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "direct");
        String queueName = channel.queueDeclare().getQueue();

        System.out.print(" [*] Binding queue to severities:");
        for (String severity : argv) {
            // 绑定队列，使用指定的 severity 作为 bindingKey
            channel.queueBind(queueName, EXCHANGE_NAME, severity);
            System.out.print(" " + severity);
        }
        System.out.println("\n [*] Waiting for messages. To exit press CTRL+C");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            String routingKey = delivery.getEnvelope().getRoutingKey();
            System.out.println(" [x] Received '" + routingKey + "':'" + message + "'");
        };
        
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }
}