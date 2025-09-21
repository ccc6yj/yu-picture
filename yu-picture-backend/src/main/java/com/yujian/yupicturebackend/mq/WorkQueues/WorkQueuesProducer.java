package com.yujian.yupicturebackend.mq.WorkQueues;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class WorkQueuesProducer {
    private final static String QUEUE_NAME = "work_queue";

    public static void main(String[] argv) throws Exception {
        try (Connection connection = ConnectionUtil.getConnection();
             Channel channel = connection.createChannel()) {

            channel.queueDeclare(QUEUE_NAME, false, false, false, null);

            for (int i = 1; i <= 10; i++) {
                String message = "Task " + i;
                // 为了演示效果，我们让包含"."的消息模拟更耗时的任务
                if (i % 3 == 0) {
                    message += "...";
                }

                channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
                System.out.println(" [x] Sent '" + message + "'");
            }
        }
    }
}