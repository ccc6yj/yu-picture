接口文档地址: http://localhost:8123/api/doc.html

-- 银行收款表 (`bank_receipt`)
CREATE TABLE `bank_receipt` (
`id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
`payer_name` VARCHAR(255) NOT NULL COMMENT '付款账户名称',
`payer_bank` VARCHAR(255) COMMENT '付款银行名称',
`payer_account` VARCHAR(255) NOT NULL COMMENT '付款卡号',
`payment_amount` DECIMAL(18, 2) NOT NULL COMMENT '付款金额',
`payment_datetime` DATETIME NOT NULL COMMENT '付款日期时间',
`used_amount` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '已使用金额',
`status` TINYINT NOT NULL DEFAULT 0 COMMENT '使用状态（0-未使用, 1-部分使用, 2-已使用）',
`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
PRIMARY KEY (`id`),
INDEX `idx_payer_name` (`payer_name`),
INDEX `idx_payment_datetime` (`payment_datetime`)
) COMMENT '银行收款表';
-- 为 bank_receipt 表添加索引
CREATE INDEX idx_bankreceipt_status_payer ON bank_receipt(status, payer_name);
CREATE INDEX idx_payer_name_status ON bank_receipt(payer_name, status,payment_datetime);


-- 租金计划表 (`rent_schedule`)
CREATE TABLE `rent_schedule` (
`id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
`lessee_name` VARCHAR(255) NOT NULL COMMENT '承租人名称 (关联付款账户名称)',
`due_date` DATE NOT NULL COMMENT '应收日期',
`total_due_amount` DECIMAL(18, 2) NOT NULL COMMENT '应收总金额',
`principal_due` DECIMAL(18, 2) NOT NULL COMMENT '应收本金',
`interest_due` DECIMAL(18, 2) NOT NULL COMMENT '应收利息',
`principal_received` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '实收本金',
`interest_received` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '实收利息',
`status` TINYINT NOT NULL DEFAULT 0 COMMENT '核销状态（0-未核销, 1-部分核销, 2-已核销）',
`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时`product:stock:1001:segment:0`间',
`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
PRIMARY KEY (`id`),
INDEX `idx_lessee_name` (`lessee_name`),
INDEX `idx_due_date` (`due_date`)
) COMMENT '租金计划表';
-- 为 rent_schedule 表添加索引
CREATE INDEX idx_lessee_name_status ON rent_schedule(lessee_name, status, due_date);

select * from rent_schedule order by update_time desc;


select sum(pa.paymount) from  (select payment_amount paymount from bank_receipt where status in (0,1,2) and payer_name='TestCustomer_0') pa;

select *  from bank_receipt where status in (0,1,2) and payer_name='TestCustomer_105';
select * from bank_receipt where status=0;

select * from rent_schedule where lessee_name='TestCustomer_105';
select  COUNT(1) from rent_schedule where status=2;

select count(1) from bank_receipt;
select count(1) from rent_schedule;

truncate bank_receipt;
truncate rent_schedule;
