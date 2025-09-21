package com.yujian.yupicturebackend.mq;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ConnectionUtil {
    private static final String HOST = "localhost";

    public static Connection getConnection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        // factory.setUsername("guest");
        // factory.setPassword("guest");
        // factory.setPort(5672);
        return factory.newConnection();
    }
}