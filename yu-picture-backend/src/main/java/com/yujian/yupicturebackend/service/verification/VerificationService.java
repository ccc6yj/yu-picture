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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger("VerificationProcess");

    // 定义状态常量
    private static final int STATUS_UNUSED = 0;
    private static final int STATUS_PARTIALLY_USED = 1;
    private static final int STATUS_USED = 2;
    private static final int UPDATE_BATCH_SIZE = 500;

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
        QueryWrapper<BankReceipt> receiptWrapper = new QueryWrapper<>();
        receiptWrapper.eq("payer_name", customerName)
                .in("status", STATUS_UNUSED, STATUS_PARTIALLY_USED)
                .orderByAsc("payment_datetime");
        List<BankReceipt> receipts = bankReceiptMapper.selectList(receiptWrapper);

        QueryWrapper<RentSchedule> scheduleWrapper = new QueryWrapper<>();
        scheduleWrapper.eq("lessee_name", customerName)
                .in("status", STATUS_UNUSED, STATUS_PARTIALLY_USED)
                .orderByAsc("due_date");
        List<RentSchedule> schedules = rentScheduleMapper.selectList(scheduleWrapper);

        ProcessingOutcome outcome = processSingleCustomer(customerName, receipts, schedules);
        flushUpdates(outcome.getUpdatedSchedules(), outcome.getUpdatedReceipts());
        return outcome.getResult();
    }

    @Transactional(rollbackFor = Exception.class)
    public VerificationResult processCustomersBatch(List<String> customerNames) {
        if (customerNames == null || customerNames.isEmpty()) {
            return new VerificationResult();
        }

        // 一次性拉取这一批客户的收款单，减少逐客户查询导致的 N 次往返
        QueryWrapper<BankReceipt> receiptWrapper = new QueryWrapper<>();
        receiptWrapper.in("payer_name", customerNames)
                .in("status", STATUS_UNUSED, STATUS_PARTIALLY_USED)
                .orderByAsc("payment_datetime");
        List<BankReceipt> allReceipts = bankReceiptMapper.selectList(receiptWrapper);

        // 使用 LinkedHashMap 保持原始顺序，方便任务日志与数据顺序对齐
        Map<String, List<BankReceipt>> receiptsByCustomer = allReceipts.stream()
                .collect(Collectors.groupingBy(BankReceipt::getPayerName, LinkedHashMap::new, Collectors.toList()));

        QueryWrapper<RentSchedule> scheduleWrapper = new QueryWrapper<>();
        scheduleWrapper.in("lessee_name", customerNames)
                .in("status", STATUS_UNUSED, STATUS_PARTIALLY_USED)
                .orderByAsc("due_date");
        List<RentSchedule> allSchedules = rentScheduleMapper.selectList(scheduleWrapper);

        // 一次性拉取租金计划并在内存按客户分组，避免重复 SQL
        Map<String, List<RentSchedule>> schedulesByCustomer = allSchedules.stream()
                .collect(Collectors.groupingBy(RentSchedule::getLesseeName, LinkedHashMap::new, Collectors.toList()));

        VerificationResult batchResult = new VerificationResult();
        List<RentSchedule> schedulesToUpdate = new ArrayList<>();
        List<BankReceipt> receiptsToUpdate = new ArrayList<>();

        for (String customerName : customerNames) {
            List<BankReceipt> receipts = new ArrayList<>(receiptsByCustomer.getOrDefault(customerName, Collections.emptyList()));
            List<RentSchedule> schedules = new ArrayList<>(schedulesByCustomer.getOrDefault(customerName, Collections.emptyList()));
            // 在内存中维持原先的时间排序，避免数据库大范围排序带来的开销
            receipts.sort(Comparator.comparing(BankReceipt::getPaymentDatetime, Comparator.nullsLast(Comparator.naturalOrder())));
            schedules.sort(Comparator.comparing(RentSchedule::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
            ProcessingOutcome outcome = processSingleCustomer(customerName, receipts, schedules);
            batchResult.merge(outcome.getResult());
            if (!outcome.getUpdatedSchedules().isEmpty()) {
                schedulesToUpdate.addAll(outcome.getUpdatedSchedules());
            }
            if (!outcome.getUpdatedReceipts().isEmpty()) {
                receiptsToUpdate.addAll(outcome.getUpdatedReceipts());
            }
        }

        flushUpdates(schedulesToUpdate, receiptsToUpdate);
        return batchResult;
    }

    private ProcessingOutcome processSingleCustomer(String customerName,
                                                    List<BankReceipt> receipts,
                                                    List<RentSchedule> schedules) {
        log.info("开始处理客户: {}", customerName);

        // 计算该客户所有收款单的剩余可用金额
        // 统一使用安全取值和非负裁剪，避免历史脏数据导致金额为 null 或出现负值
        BigDecimal totalPayment = receipts.stream()
                .map(this::getAvailableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalPayment = totalPayment.max(BigDecimal.ZERO);

        if (totalPayment.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("客户 {} 没有有效的待核销金额。", customerName);
            return ProcessingOutcome.empty();
        }

        if (schedules == null || schedules.isEmpty()) {
            log.warn("客户 {} 有待核销金额 {}，但没有找到待核销的租金计划。", customerName, totalPayment);
            return ProcessingOutcome.empty();
        }

        log.info("客户 {} 待核销总金额: {}", customerName, totalPayment);
        BigDecimal originalTotalPayment = totalPayment;

        VerificationResult result = new VerificationResult();
        // 收集需要写回数据库的对象，避免在循环中频繁落库
        List<RentSchedule> updatedSchedules = new ArrayList<>();
        List<BankReceipt> updatedReceipts = new ArrayList<>();

        // 按照到期日顺序消耗资金，先利息后本金
        for (RentSchedule schedule : schedules) {
            if (totalPayment.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal remainingInterest = remainingAmount(schedule.getInterestDue(), schedule.getInterestReceived());
            BigDecimal remainingPrincipal = remainingAmount(schedule.getPrincipalDue(), schedule.getPrincipalReceived());
            boolean updated = false;

            if (remainingInterest.compareTo(BigDecimal.ZERO) > 0) {
                // 先覆盖未收利息，满足业务优先级
                BigDecimal paymentForInterest = totalPayment.min(remainingInterest);
                if (paymentForInterest.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal currentInterestReceived = safeAmount(schedule.getInterestReceived());
                    schedule.setInterestReceived(currentInterestReceived.add(paymentForInterest));
                    totalPayment = totalPayment.subtract(paymentForInterest);
                    result.addInterest(paymentForInterest);
                    updated = true;
                    log.debug("客户: {}, 租金计划ID: {}, 核销利息: {}", customerName, schedule.getId(), paymentForInterest);
                }
            }

            if (totalPayment.compareTo(BigDecimal.ZERO) > 0 && remainingPrincipal.compareTo(BigDecimal.ZERO) > 0) {
                // 利息抵扣完后再处理本金
                BigDecimal paymentForPrincipal = totalPayment.min(remainingPrincipal);
                if (paymentForPrincipal.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal currentPrincipalReceived = safeAmount(schedule.getPrincipalReceived());
                    schedule.setPrincipalReceived(currentPrincipalReceived.add(paymentForPrincipal));
                    totalPayment = totalPayment.subtract(paymentForPrincipal);
                    result.addPrincipal(paymentForPrincipal);
                    updated = true;
                    log.debug("客户: {}, 租金计划ID: {}, 核销本金: {}", customerName, schedule.getId(), paymentForPrincipal);
                }
            }

            if (updated) {
                result.incrementVerifiedCount();
                BigDecimal updatedPrincipalDue = safeAmount(schedule.getPrincipalDue());
                BigDecimal updatedInterestDue = safeAmount(schedule.getInterestDue());
                BigDecimal updatedPrincipalReceived = safeAmount(schedule.getPrincipalReceived());
                BigDecimal updatedInterestReceived = safeAmount(schedule.getInterestReceived());
                if (updatedPrincipalReceived.compareTo(updatedPrincipalDue) >= 0 &&
                        updatedInterestReceived.compareTo(updatedInterestDue) >= 0) {
                    schedule.setStatus(STATUS_USED);
                } else {
                    schedule.setStatus(STATUS_PARTIALLY_USED);
                }
                updatedSchedules.add(schedule);
            }
        }

        // 统计本轮实际使用的金额，用于回填到收款单
        // 原始金额减去剩余金额得到实际本轮使用的金额，负数则回退为 0
        BigDecimal amountToUpdateOnReceipts = originalTotalPayment.subtract(totalPayment);
        if (amountToUpdateOnReceipts.compareTo(BigDecimal.ZERO) < 0) {
            amountToUpdateOnReceipts = BigDecimal.ZERO;
        }
        for (BankReceipt receipt : receipts) {
            if (amountToUpdateOnReceipts.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal availableOnReceipt = getAvailableAmount(receipt);
            BigDecimal usageOnThisReceipt = amountToUpdateOnReceipts.min(availableOnReceipt);

            if (usageOnThisReceipt.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentUsedAmount = safeAmount(receipt.getUsedAmount());
                receipt.setUsedAmount(currentUsedAmount.add(usageOnThisReceipt));
                BigDecimal paymentAmount = safeAmount(receipt.getPaymentAmount());
                if (receipt.getUsedAmount().compareTo(paymentAmount) >= 0) {
                    receipt.setStatus(STATUS_USED);
                } else {
                    receipt.setStatus(STATUS_PARTIALLY_USED);
                }
                updatedReceipts.add(receipt);
                amountToUpdateOnReceipts = amountToUpdateOnReceipts.subtract(usageOnThisReceipt);
            }
        }

        if (result.getVerifiedCount() > 0 || result.getTotalPrincipal().compareTo(BigDecimal.ZERO) > 0
                || result.getTotalInterest().compareTo(BigDecimal.ZERO) > 0) {
            log.info("客户 {} 处理完毕。本次核销笔数: {}, 本金: {}, 利息: {}",
                    customerName, result.getVerifiedCount(), result.getTotalPrincipal(), result.getTotalInterest());
        }

        return new ProcessingOutcome(result, updatedSchedules, updatedReceipts);
    }

    private void flushUpdates(List<RentSchedule> schedulesToUpdate, List<BankReceipt> receiptsToUpdate) {
        if (schedulesToUpdate != null && !schedulesToUpdate.isEmpty()) {
            // 拆分成小批次写库，避免 updateBatchById 一次性载荷过大
            updateRentSchedulesInChunks(schedulesToUpdate);
        }
        if (receiptsToUpdate != null && !receiptsToUpdate.isEmpty()) {
            // 同步处理收款单的批量更新，均衡数据库压力
            updateBankReceiptsInChunks(receiptsToUpdate);
        }
    }

    private void updateRentSchedulesInChunks(List<RentSchedule> schedules) {
        for (int i = 0; i < schedules.size(); i += UPDATE_BATCH_SIZE) {
            int end = Math.min(i + UPDATE_BATCH_SIZE, schedules.size());
            rentScheduleService.updateBatchById(new ArrayList<>(schedules.subList(i, end)));
        }
    }

    private void updateBankReceiptsInChunks(List<BankReceipt> receipts) {
        for (int i = 0; i < receipts.size(); i += UPDATE_BATCH_SIZE) {
            int end = Math.min(i + UPDATE_BATCH_SIZE, receipts.size());
            bankReceiptService.updateBatchById(new ArrayList<>(receipts.subList(i, end)));
        }
    }

    /**
     * 金额字段兜底为 0，避免数据库返回 null 时出现 NPE。
     */
    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * 计算收款单剩余可用金额，若历史记录为负则裁剪为 0。
     */
    private BigDecimal getAvailableAmount(BankReceipt receipt) {
        if (receipt == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal payment = safeAmount(receipt.getPaymentAmount());
        BigDecimal used = safeAmount(receipt.getUsedAmount());
        BigDecimal available = payment.subtract(used);
        if (available.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return available;
    }

    /**
     * 获取租金计划待核销金额，确保不会出现负值。
     */
    private BigDecimal remainingAmount(BigDecimal due, BigDecimal received) {
        BigDecimal safeDue = safeAmount(due);
        BigDecimal safeReceived = safeAmount(received);
        BigDecimal remaining = safeDue.subtract(safeReceived);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return remaining;
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

        public void merge(VerificationResult other) {
            if (other == null) {
                return;
            }
            this.verifiedCount += other.verifiedCount;
            this.totalPrincipal = this.totalPrincipal.add(other.totalPrincipal);
            this.totalInterest = this.totalInterest.add(other.totalInterest);
        }
    }

    // 聚合一次客户处理过程中的结果对象，便于统一提交
    private static class ProcessingOutcome {
        private final VerificationResult result;
        private final List<RentSchedule> updatedSchedules;
        private final List<BankReceipt> updatedReceipts;

        private ProcessingOutcome(VerificationResult result,
                                  List<RentSchedule> updatedSchedules,
                                  List<BankReceipt> updatedReceipts) {
            this.result = result;
            this.updatedSchedules = updatedSchedules;
            this.updatedReceipts = updatedReceipts;
        }

        private static ProcessingOutcome empty() {
            return new ProcessingOutcome(new VerificationResult(), Collections.emptyList(), Collections.emptyList());
        }

        public VerificationResult getResult() {
            return result;
        }

        public List<RentSchedule> getUpdatedSchedules() {
            return updatedSchedules;
        }

        public List<BankReceipt> getUpdatedReceipts() {
            return updatedReceipts;
        }
    }
}
