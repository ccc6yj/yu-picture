package com.yujian.yupicturebackend.controller;

import com.yujian.yupicturebackend.service.verification.TestDataGeneratorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test-data")
//@Profile("dev") // 仅在 'dev' 环境下激活此控制器
public class TestDataController {

    private final TestDataGeneratorService testDataGeneratorService;

    public TestDataController(TestDataGeneratorService testDataGeneratorService) {
        this.testDataGeneratorService = testDataGeneratorService;
    }

    /**
     * 生成测试数据。警告：此操作会向数据库写入大量数据，请仅在测试环境使用！
     * @param customerCount 客户数量
     * @param schedulesPerCustomer 每个客户的租金计划数
     * @param receiptsPerCustomer 每个客户的收款单数
     * @return 执行结果
     */
    @PostMapping("/generate")
    public Map<String, Object> generateData(
            @RequestParam(defaultValue = "200000") int customerCount,
            @RequestParam(defaultValue = "5") int schedulesPerCustomer,
            @RequestParam(defaultValue = "5") int receiptsPerCustomer) {
        
        long totalRecords = (long) customerCount * (schedulesPerCustomer + receiptsPerCustomer);
        
        // 异步执行，避免HTTP请求超时
        new Thread(() -> testDataGeneratorService.generateAndInsertData(customerCount, schedulesPerCustomer, receiptsPerCustomer)).start();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Data generation started in background.");
        response.put("customerCount", customerCount);
        response.put("schedulesPerCustomer", schedulesPerCustomer);
        response.put("receiptsPerCustomer", receiptsPerCustomer);
        response.put("approxTotalRecords", totalRecords);
        
        return response;
    }
}
