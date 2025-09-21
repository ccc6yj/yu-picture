package com.yujian.yupicturebackend.mq.RPC;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.yujian.yupicturebackend.mq.ConnectionUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class RPCClient implements AutoCloseable {
    private final Connection connection;
    private final Channel channel;
    private final String requestQueueName = "rpc_queue";

    public RPCClient() throws IOException, TimeoutException {
        connection = ConnectionUtil.getConnection();
        channel = connection.createChannel();
    }

    public String call(String message) throws IOException, InterruptedException {
        final String corrId = UUID.randomUUID().toString();

        // 声明一个用于接收响应的临时回调队列
        String replyQueueName = channel.queueDeclare().getQueue();

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();
        
        // 使用一个阻塞队列来等待响应
        final BlockingQueue<String> response = new ArrayBlockingQueue<>(1);

        channel.basicPublish("", requestQueueName, props, message.getBytes(StandardCharsets.UTF_8));
        System.out.println(" [x] Requesting fib(" + message + ")");

        // 监听回调队列
        String ctag = channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
            if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                response.offer(new String(delivery.getBody(), StandardCharsets.UTF_8));
            }
        }, consumerTag -> {});

        // 阻塞等待，直到收到响应
        String result = response.take();
        channel.basicCancel(ctag);
        return result;
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }

    public static void main(String[] args) {
        try (RPCClient fibonacciRpc = new RPCClient()) {
            for (int i = 0; i < 32; i++) {
                String i_str = Integer.toString(i);
                String result = fibonacciRpc.call(i_str);
                System.out.println(" [.] Got '" + result + "'");
            }
        } catch (IOException | TimeoutException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}