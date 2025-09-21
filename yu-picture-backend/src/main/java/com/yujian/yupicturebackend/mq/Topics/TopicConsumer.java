package com.yujian.yupicturebackend.mq.Topics;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class TopicConsumer {
    private static final String EXCHANGE_NAME = "logs_topic_exchange";

    public static void main(String[] argv) throws Exception {
        if (argv.length < 1) {
            System.err.println("Usage: TopicConsumer <binding_key>...");
            System.exit(1);
        }

        Connection connection = ConnectionUtil.getConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName = channel.queueDeclare().getQueue();

        System.out.print(" [*] Binding queue to keys:");
        for (String bindingKey : argv) {
            channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
            System.out.print(" " + bindingKey);
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