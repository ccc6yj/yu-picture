package com.yujian.yupicturebackend.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@TableName(value = "rent_schedule")
@Data
public class RentSchedule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("lessee_name")
    private String lesseeName;

    @TableField("due_date")
    private Date dueDate;

    @TableField("total_due_amount")
    private BigDecimal totalDueAmount;

    @TableField("principal_due")
    private BigDecimal principalDue;

    @TableField("interest_due")
    private BigDecimal interestDue;

    @TableField("principal_received")
    private BigDecimal principalReceived;

    @TableField("interest_received")
    private BigDecimal interestReceived;

    private Integer status;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
