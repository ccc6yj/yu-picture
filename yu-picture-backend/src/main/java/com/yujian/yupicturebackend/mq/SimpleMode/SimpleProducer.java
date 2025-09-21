package com.yujian.yupicturebackend.mq.SimpleMode;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.nio.charset.StandardCharsets;

public class SimpleProducer {
    private final static String QUEUE_NAME = "simple_queue";

    public static void main(String[] argv) throws Exception {
        // 获取连接
        try (Connection connection = ConnectionUtil.getConnection();
             // 创建通道
             Channel channel = connection.createChannel()) {

            // 声明队列
            // durable: 是否持久化, exclusive: 是否独占, autoDelete: 是否自动删除, arguments: 其他参数
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);

            String message = "Hello RabbitMQ!";

            // 发送消息
            // exchange: 交换机名称, routingKey: 路由键, props: 其他属性, body: 消息体
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));

            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}