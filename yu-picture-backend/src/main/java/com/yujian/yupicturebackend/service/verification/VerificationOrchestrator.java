package com.yujian.yupicturebackend.service.verification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yujian.yupicturebackend.exception.BusinessException;
import com.yujian.yupicturebackend.exception.ErrorCode;
import com.yujian.yupicturebackend.mapper.BankReceiptMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class VerificationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger("VerificationProcess");

    private final ThreadPoolExecutor verificationExecutor;
    private final BankReceiptMapper bankReceiptMapper;
    private final VerificationService verificationService;
    /**
     * 每个批次处理的客户数量，支持通过配置调整，避免硬编码导致不同环境无法调优。
     */
    private final int customerBatchSize;
    /**
     * 运行状态开关，防止在核销未结束前被重复触发导致重复扣款。
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VerificationOrchestrator(@Qualifier("verificationExecutor") ThreadPoolExecutor verificationExecutor,
                                  BankReceiptMapper bankReceiptMapper,
                                  VerificationService verificationService,
                                  @Value("${verification.customer-batch-size:200}") int customerBatchSize) {
        this.verificationExecutor = verificationExecutor;
        this.bankReceiptMapper = bankReceiptMapper;
        this.verificationService = verificationService;
        this.customerBatchSize = customerBatchSize > 0 ? customerBatchSize : 200;
    }

    public VerificationSummary runVerificationProcess() throws ExecutionException, InterruptedException {
        if (!running.compareAndSet(false, true)) {
            // 限制多次触发同一核销任务，避免并发写导致金额重复扣减
            log.warn("核销任务仍在执行中，本次触发被忽略");
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前已有核销任务在执行，请稍后再试");
        }
        long startTime = System.currentTimeMillis();
        log.info("开始批量核销流程...");

        try {
            // 1. 找出所有有待核销收款的客户
            QueryWrapper<com.yujian.yupicturebackend.domain.BankReceipt> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("DISTINCT payer_name").in("status", 0, 1);
            List<String> customerNames = bankReceiptMapper.selectObjs(queryWrapper).stream().map(o -> (String)o).collect(Collectors.toList());

            if (customerNames.isEmpty()) {
                log.info("没有找到需要核销的客户。");
                return new VerificationSummary();
            }
            log.info("发现 {} 个待处理客户。", customerNames.size());

            // 2. 为每个批次的客户创建一个异步任务
            // 将客户拆成固定大小的批次，减少线程池任务数并提升单次数据库命中率
            List<List<String>> customerBatches = partitionCustomers(customerNames, customerBatchSize);
            log.info("客户总数: {}，按批次大小 {} 拆分为 {} 个任务", customerNames.size(), customerBatchSize, customerBatches.size());

            List<CompletableFuture<VerificationService.VerificationResult>> futures = new ArrayList<>();
            for (List<String> batch : customerBatches) {
                // 异步执行每个批次，内部自行批量查询与更新
                CompletableFuture<VerificationService.VerificationResult> future = CompletableFuture.supplyAsync(() ->
                        verificationService.processCustomersBatch(batch), verificationExecutor);
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
        } finally {
            running.set(false);
        }
    }

    private List<List<String>> partitionCustomers(List<String> customerNames, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        if (customerNames == null || customerNames.isEmpty()) {
            return batches;
        }
        if (batchSize <= 0 || batchSize >= customerNames.size()) {
            batches.add(new ArrayList<>(customerNames));
            return batches;
        }
        for (int i = 0; i < customerNames.size(); i += batchSize) {
            int end = Math.min(i + batchSize, customerNames.size());
            batches.add(new ArrayList<>(customerNames.subList(i, end)));
        }
        return batches;
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

    }
}
