package com.yujian.yupicturebackend.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@TableName(value = "bank_receipt")
@Data
public class BankReceipt implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("payer_name")
    private String payerName;

    @TableField("payer_bank")
    private String payerBank;

    @TableField("payer_account")
    private String payerAccount;

    @TableField("payment_amount")
    private BigDecimal paymentAmount;

    @TableField("payment_datetime")
    private Date paymentDatetime;

    @TableField("used_amount")
    private BigDecimal usedAmount;

    private Integer status;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
