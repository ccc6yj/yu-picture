package com.yujian.yupicturebackend.service.verification;

import com.yujian.yupicturebackend.domain.BankReceipt;
import com.yujian.yupicturebackend.domain.RentSchedule;
import com.yujian.yupicturebackend.service.IBankReceiptService;
import com.yujian.yupicturebackend.service.IRentScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TestDataGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(TestDataGeneratorService.class);

    private final IBankReceiptService bankReceiptService;
    private final IRentScheduleService rentScheduleService;
    // 线程池大小可根据服务器CPU核心数调整
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    public TestDataGeneratorService(IBankReceiptService bankReceiptService, IRentScheduleService rentScheduleService) {
        this.bankReceiptService = bankReceiptService;
        this.rentScheduleService = rentScheduleService;
    }

    public void generateAndInsertData(int customerCount, int schedulesPerCustomer, int receiptsPerCustomer) {
        log.info("开始生成测试数据... 客户数: {}, 每个客户租金计划数: {}, 每个客户收款单数: {}",
                customerCount, schedulesPerCustomer, receiptsPerCustomer);
        long startTime = System.currentTimeMillis();

        // 调整为更大的批处理大小
        final int BATCH_SIZE = 5000;
        // 计算每个线程处理的客户数量
        int threadCount = Runtime.getRuntime().availableProcessors() * 2;
        int customersPerThread = customerCount / threadCount;
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 多线程并行处理
        for (int t = 0; t < threadCount; t++) {
            final int startIndex = t * customersPerThread;
            final int endIndex = (t == threadCount - 1) ? customerCount : (t + 1) * customersPerThread;

            executorService.submit(() -> {
                try {
                    processBatch(startIndex, endIndex, schedulesPerCustomer, receiptsPerCustomer, BATCH_SIZE);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(); // 等待所有线程完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.currentTimeMillis();
        log.info("测试数据生成完毕。总耗时: {} 秒", (endTime - startTime)/1000);
    }

    @Transactional
    public void processBatch(int startIndex, int endIndex, int schedulesPerCustomer, int receiptsPerCustomer, int batchSize) {
        List<BankReceipt> receiptsBuffer = new ArrayList<>(batchSize);
        List<RentSchedule> schedulesBuffer = new ArrayList<>(batchSize);

        for (int i = startIndex; i < endIndex; i++) {
            String customerName = "TestCustomer_" + i;

            // 为每个客户生成租金计划
            for (int j = 0; j < schedulesPerCustomer; j++) {
                schedulesBuffer.add(createRandomRentSchedule(customerName));
                if (schedulesBuffer.size() >= batchSize) {
                    rentScheduleService.saveBatch(schedulesBuffer);
                    schedulesBuffer.clear();
                }
            }

            // 为每个客户生成收款记录
            for (int k = 0; k < receiptsPerCustomer; k++) {
                receiptsBuffer.add(createRandomBankReceipt(customerName));
                if (receiptsBuffer.size() >= batchSize) {
                    bankReceiptService.saveBatch(receiptsBuffer);
                    receiptsBuffer.clear();
                }
            }
        }

        // 插入剩余数据
        if (!schedulesBuffer.isEmpty()) {
            rentScheduleService.saveBatch(schedulesBuffer);
        }
        if (!receiptsBuffer.isEmpty()) {
            bankReceiptService.saveBatch(receiptsBuffer);
        }

        log.info("线程完成处理客户范围: {}-{}的数据", startIndex, endIndex);
    }

    private RentSchedule createRandomRentSchedule(String lesseeName) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        RentSchedule schedule = new RentSchedule();
        // 1. 获取一个日历实例，它会自动设置为当前日期和时间
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        // 2. 获取当前月份的最大天数 (例如，9月是30天，10月是31天)
        int maxDayOfMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        // 3. 生成一个 1 到 maxDayOfMonth 之间的随机整数作为日期
        int randomDay = random.nextInt(1, maxDayOfMonth + 1); // nextInt的上限是开区间，所以需要+1
        // 4. 将日历的日期设置为这个随机天
        calendar.set(java.util.Calendar.DAY_OF_MONTH, randomDay);
        schedule.setDueDate(calendar.getTime());
        schedule.setLesseeName(lesseeName);
        BigDecimal principal = BigDecimal.valueOf(random.nextDouble(500, 2000));
        BigDecimal interest = BigDecimal.valueOf(random.nextDouble(10, 100));
        schedule.setPrincipalDue(principal.setScale(2, BigDecimal.ROUND_HALF_UP));
        schedule.setInterestDue(interest.setScale(2, BigDecimal.ROUND_HALF_UP));
        schedule.setTotalDueAmount(principal.add(interest).setScale(2, BigDecimal.ROUND_HALF_UP));
        schedule.setPrincipalReceived(BigDecimal.ZERO);
        schedule.setInterestReceived(BigDecimal.ZERO);
        schedule.setStatus(0); // 未核销
        return schedule;
    }

    private BankReceipt createRandomBankReceipt(String payerName) {

        ThreadLocalRandom random = ThreadLocalRandom.current();
        BankReceipt receipt = new BankReceipt();
        receipt.setPayerName(payerName);
        receipt.setPayerAccount("6222020000123" + random.nextInt(10000, 99999));
        receipt.setPaymentDatetime(new Date());
        receipt.setPayerBank("Test Bank");
        receipt.setPaymentAmount(BigDecimal.valueOf(random.nextDouble(800, 3000)).setScale(2, BigDecimal.ROUND_HALF_UP));
        receipt.setUsedAmount(BigDecimal.ZERO);
        receipt.setStatus(0); // 未使用
        return receipt;
    }
}