package com.yujian.yupicturebackend.service.verification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yujian.yupicturebackend.mapper.BankReceiptMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class VerificationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger("VerificationProcess");

    private final ThreadPoolExecutor verificationExecutor;
    private final BankReceiptMapper bankReceiptMapper;
    private final VerificationService verificationService;

    public VerificationOrchestrator(@Qualifier("verificationExecutor") ThreadPoolExecutor verificationExecutor,
                                  BankReceiptMapper bankReceiptMapper,
                                  VerificationService verificationService) {
        this.verificationExecutor = verificationExecutor;
        this.bankReceiptMapper = bankReceiptMapper;
        this.verificationService = verificationService;
    }

    public VerificationSummary runVerificationProcess() throws ExecutionException, InterruptedException {
        long startTime = System.currentTimeMillis();
        log.info("开始批量核销流程...");

        // 1. 找出所有有待核销收款的客户
        QueryWrapper<com.yujian.yupicturebackend.domain.BankReceipt> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("DISTINCT payer_name").in("status", 0, 1);
        List<String> customerNames = bankReceiptMapper.selectObjs(queryWrapper).stream().map(o -> (String)o).collect(Collectors.toList());

        if (customerNames.isEmpty()) {
            log.info("没有找到需要核销的客户。");
            return new VerificationSummary();
        }
        log.info("发现 {} 个待处理客户。", customerNames.size());

        // 2. 为每个客户创建一个异步任务
        List<CompletableFuture<VerificationService.VerificationResult>> futures = new ArrayList<>();
        for (String customerName : customerNames) {
            CompletableFuture<VerificationService.VerificationResult> future = CompletableFuture.supplyAsync(() ->
                verificationService.processCustomer(customerName), verificationExecutor);
            futures.add(future);
        }


        // 3. 等待所有任务完成并聚合结果
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        VerificationSummary summary = new VerificationSummary();
        for (CompletableFuture<VerificationService.VerificationResult> future : futures) {
            VerificationService.VerificationResult result = future.get();
            summary.add(result);
        }

        long endTime = System.currentTimeMillis();
        summary.setTotalTimeSeconds((endTime - startTime) / 1000.0);
        log.info("批量核销流程结束。总耗时: {} 秒", summary.getTotalTimeSeconds());
        log.info("最终结果: {}", summary);

        return summary;
    }

    @Data
    public static class VerificationSummary {
        private double totalTimeSeconds = 0;
        private int totalVerifiedCount = 0;
        private BigDecimal totalPrincipal = BigDecimal.ZERO;
        private BigDecimal totalInterest = BigDecimal.ZERO;

        public void add(VerificationService.VerificationResult result) {
            this.totalVerifiedCount += result.getVerifiedCount();
            this.totalPrincipal = this.totalPrincipal.add(result.getTotalPrincipal());
            this.totalInterest = this.totalInterest.add(result.getTotalInterest());
        }

        // Getters and Setters
    }
}
