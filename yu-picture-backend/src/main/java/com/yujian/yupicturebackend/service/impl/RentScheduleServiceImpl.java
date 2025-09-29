package com.yujian.yupicturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yujian.yupicturebackend.domain.RentSchedule;
import com.yujian.yupicturebackend.mapper.RentScheduleMapper;
import com.yujian.yupicturebackend.service.IRentScheduleService;
import org.springframework.stereotype.Service;

@Service
public class RentScheduleServiceImpl extends ServiceImpl<RentScheduleMapper, RentSchedule> implements IRentScheduleService {
}
