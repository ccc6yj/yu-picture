package com.yujian.yupicturebackend.mq.core.PriorityQueue;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class PriorityProducer {
    private static final String PRIORITY_QUEUE = "priority_queue";

    public static void main(String[] args) throws Exception {
        try (Connection connection = ConnectionUtil.getConnection();
             Channel channel = connection.createChannel()) {
            
            // 1. 声明一个支持优先级的队列，设置最大优先级
            java.util.Map<String, Object> queueArgs = new java.util.HashMap<>();
            queueArgs.put("x-max-priority", 10);
            channel.queueDeclare(PRIORITY_QUEUE, true, false, false, queueArgs);

            // 2. 发送带优先级的消息
            for (int i = 1; i <= 10; i++) {
                int priority = (i % 3 == 0) ? 9 : 1; // 3的倍数是高优先级
                String message = "Message with priority " + priority;
                
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .priority(priority)
                        .build();

                channel.basicPublish("", PRIORITY_QUEUE, props, message.getBytes(StandardCharsets.UTF_8));
                System.out.println(" [x] Sent: '" + message + "'");
            }
        }
    }
}