package com.yujian.yupicturebackend.service.verification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yujian.yupicturebackend.domain.BankReceipt;
import com.yujian.yupicturebackend.domain.RentSchedule;
import com.yujian.yupicturebackend.mapper.BankReceiptMapper;
import com.yujian.yupicturebackend.mapper.RentScheduleMapper;
import com.yujian.yupicturebackend.service.IBankReceiptService;
import com.yujian.yupicturebackend.service.IRentScheduleService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger("VerificationProcess");

    // 定义状态常量
    private static final int STATUS_UNUSED = 0;
    private static final int STATUS_PARTIALLY_USED = 1;
    private static final int STATUS_USED = 2;

    private final BankReceiptMapper bankReceiptMapper;
    private final RentScheduleMapper rentScheduleMapper;

    // 注入 IService 以使用批量更新功能
    @Resource
    private IBankReceiptService bankReceiptService;
    @Resource
    private IRentScheduleService rentScheduleService;


    public VerificationService(BankReceiptMapper bankReceiptMapper, RentScheduleMapper rentScheduleMapper) {
        this.bankReceiptMapper = bankReceiptMapper;
        this.rentScheduleMapper = rentScheduleMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public VerificationResult processCustomer(String customerName) {
        log.info("开始处理客户: {}", customerName);

        // 1. 找出该客户所有未完全使用的收款记录
        QueryWrapper<BankReceipt> receiptWrapper = new QueryWrapper<>();
        receiptWrapper.eq("payer_name", customerName)
                .in("status", STATUS_UNUSED, STATUS_PARTIALLY_USED)
                .orderByAsc("payment_datetime");
        List<BankReceipt> receipts = bankReceiptMapper.selectList(receiptWrapper);

        BigDecimal totalPayment = receipts.stream()
                .map(r -> r.getPaymentAmount().subtract(r.getUsedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPayment.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("客户 {} 没有有效的待核销金额。", customerName);
            return new VerificationResult();
        }
        log.info("客户 {} 待核销总金额: {}", customerName, totalPayment);
        BigDecimal originalTotalPayment = totalPayment;

        // 2. 找出该客户所有未完全核销的租金计划，按应收日期升序
        QueryWrapper<RentSchedule> scheduleWrapper = new QueryWrapper<>();
        scheduleWrapper.eq("lessee_name", customerName)
                .in("status", STATUS_UNUSED, STATUS_PARTIALLY_USED)
                .orderByAsc("due_date");
        List<RentSchedule> schedules = rentScheduleMapper.selectList(scheduleWrapper);

        if (schedules.isEmpty()) {
            log.warn("客户 {} 有待核销金额 {}，但没有找到待核销的租金计划。", customerName, totalPayment);
            return new VerificationResult();
        }

        VerificationResult result = new VerificationResult();
        // 创建用于批量更新的列表
        List<RentSchedule> updatedSchedules = new ArrayList<>();
        List<BankReceipt> updatedReceipts = new ArrayList<>();


        // 3. 循环核销租金计划
        for (RentSchedule schedule : schedules) {
            if (totalPayment.compareTo(BigDecimal.ZERO) <= 0) {
                break; // 客户的钱用完了
            }

            BigDecimal remainingInterest = schedule.getInterestDue().subtract(schedule.getInterestReceived());
            BigDecimal remainingPrincipal = schedule.getPrincipalDue().subtract(schedule.getPrincipalReceived());
            boolean updated = false;

            // 3.1 优先核销利息
            if (remainingInterest.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal paymentForInterest = totalPayment.min(remainingInterest);
                schedule.setInterestReceived(schedule.getInterestReceived().add(paymentForInterest));
                totalPayment = totalPayment.subtract(paymentForInterest);
                result.addInterest(paymentForInterest);
                updated = true;
                log.debug("客户: {}, 租金计划ID: {}, 核销利息: {}", customerName, schedule.getId(), paymentForInterest);
            }

            // 3.2 核销本金
            if (totalPayment.compareTo(BigDecimal.ZERO) > 0 && remainingPrincipal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal paymentForPrincipal = totalPayment.min(remainingPrincipal);
                schedule.setPrincipalReceived(schedule.getPrincipalReceived().add(paymentForPrincipal));
                totalPayment = totalPayment.subtract(paymentForPrincipal);
                result.addPrincipal(paymentForPrincipal);
                updated = true;
                log.debug("客户: {}, 租金计划ID: {}, 核销本金: {}", customerName, schedule.getId(), paymentForPrincipal);
            }

            // 3.3 更新租金计划状态，并加入待更新列表
            if (updated) {
                result.incrementVerifiedCount();
                if (schedule.getPrincipalReceived().compareTo(schedule.getPrincipalDue()) >= 0 &&
                        schedule.getInterestReceived().compareTo(schedule.getInterestDue()) >= 0) {
                    schedule.setStatus(STATUS_USED); // 已核销
                } else {
                    schedule.setStatus(STATUS_PARTIALLY_USED); // 部分核销
                }
                updatedSchedules.add(schedule);
            }
        }

        // **一次性批量更新租金计划**
        if (!updatedSchedules.isEmpty()) {
            rentScheduleService.updateBatchById(updatedSchedules);
        }

        // 4. 更新收款单状态
        BigDecimal amountToUpdateOnReceipts = originalTotalPayment.subtract(totalPayment);
        for (BankReceipt receipt : receipts) {
            if (amountToUpdateOnReceipts.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal availableOnReceipt = receipt.getPaymentAmount().subtract(receipt.getUsedAmount());
            BigDecimal usageOnThisReceipt = amountToUpdateOnReceipts.min(availableOnReceipt);

            if (usageOnThisReceipt.compareTo(BigDecimal.ZERO) > 0) {
                receipt.setUsedAmount(receipt.getUsedAmount().add(usageOnThisReceipt));
                if (receipt.getUsedAmount().compareTo(receipt.getPaymentAmount()) >= 0) {
                    receipt.setStatus(STATUS_USED);
                } else {
                    receipt.setStatus(STATUS_PARTIALLY_USED);
                }
                updatedReceipts.add(receipt);
                amountToUpdateOnReceipts = amountToUpdateOnReceipts.subtract(usageOnThisReceipt);
            }
        }

        // **一次性批量更新收款单**
        if (!updatedReceipts.isEmpty()) {
            bankReceiptService.updateBatchById(updatedReceipts);
        }

        log.info("客户 {} 处理完毕。本次核销笔数: {}, 本金: {}, 利息: {}",
                customerName, result.getVerifiedCount(), result.getTotalPrincipal(), result.getTotalInterest());

        return result;
    }

    @Data
    public static class VerificationResult {
        private int verifiedCount = 0;
        private BigDecimal totalPrincipal = BigDecimal.ZERO;
        private BigDecimal totalInterest = BigDecimal.ZERO;

        public void addPrincipal(BigDecimal principal) {
            if (principal != null && principal.compareTo(BigDecimal.ZERO) > 0) {
                this.totalPrincipal = this.totalPrincipal.add(principal);
            }
        }

        public void addInterest(BigDecimal interest) {
            if (interest != null && interest.compareTo(BigDecimal.ZERO) > 0) {
                this.totalInterest = this.totalInterest.add(interest);
            }
        }

        public void incrementVerifiedCount() {
            this.verifiedCount++;
        }
    }
}
