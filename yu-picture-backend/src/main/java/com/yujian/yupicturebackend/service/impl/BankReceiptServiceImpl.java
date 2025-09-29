package com.yujian.yupicturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yujian.yupicturebackend.domain.BankReceipt;
import com.yujian.yupicturebackend.mapper.BankReceiptMapper;
import com.yujian.yupicturebackend.service.IBankReceiptService;
import org.springframework.stereotype.Service;

@Service
public class BankReceiptServiceImpl extends ServiceImpl<BankReceiptMapper, BankReceipt> implements IBankReceiptService {
}
