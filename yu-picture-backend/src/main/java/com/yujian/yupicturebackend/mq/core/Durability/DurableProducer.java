package com.yujian.yupicturebackend.mq.core.Durability;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MessageProperties;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class DurableProducer {
    private static final String DURABLE_QUEUE = "durable_task_queue";

    public static void main(String[] argv) throws Exception {
        try (Connection connection = ConnectionUtil.getConnection();
             Channel channel = connection.createChannel()) {
            
            // 1. 声明一个持久化的队列
            boolean durable = true;
            channel.queueDeclare(DURABLE_QUEUE, durable, false, false, null);

            String message = "This is a durable message.";

            // 2. 将消息标记为持久化
            channel.basicPublish("", DURABLE_QUEUE,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes(StandardCharsets.UTF_8));
            
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}