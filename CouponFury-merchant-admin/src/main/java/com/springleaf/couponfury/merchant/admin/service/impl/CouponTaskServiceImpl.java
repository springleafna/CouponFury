package com.springleaf.couponfury.merchant.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.springleaf.couponfury.framework.exception.ClientException;
import com.springleaf.couponfury.merchant.admin.common.context.UserContext;
import com.springleaf.couponfury.merchant.admin.common.enums.CouponTaskSendTypeEnum;
import com.springleaf.couponfury.merchant.admin.common.enums.CouponTaskStatusEnum;
import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTaskDO;
import com.springleaf.couponfury.merchant.admin.dao.mapper.CouponTaskMapper;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTaskCreateReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.merchant.admin.service.CouponTaskService;
import com.springleaf.couponfury.merchant.admin.service.CouponTemplateService;
import com.springleaf.couponfury.merchant.admin.service.handle.excel.RowCountListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class CouponTaskServiceImpl implements CouponTaskService {

    @Resource
    private CouponTaskMapper couponTaskMapper;
    @Resource
    private CouponTemplateService couponTemplateService;

    @Override
    public void createCouponTask(CouponTaskCreateReqDTO requestParam) {
        // 验证非空参数
        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplateById(requestParam.getCouponTemplateId());
        if (couponTemplate == null) {
            throw new ClientException("优惠券模板不存在，请检查提交信息是否正确");
        }
        // 验证定时发送，发送时间是否为空
        if (requestParam.getSendType() == CouponTaskSendTypeEnum.SCHEDULED.getType() && requestParam.getSendTime() == null) {
            throw new ClientException("定时发送时，发送时间不能为空");
        }

        // 构建优惠券推送任务数据库持久层实体
        CouponTaskDO couponTaskDO = BeanUtil.copyProperties(requestParam, CouponTaskDO.class);
        couponTaskDO.setBatchId(IdUtil.getSnowflakeNextId());
        couponTaskDO.setOperatorId(Long.parseLong(UserContext.getUserId()));
        couponTaskDO.setShopNumber(UserContext.getShopNumber());
        couponTaskDO.setStatus(
                Objects.equals(requestParam.getSendType(), CouponTaskSendTypeEnum.IMMEDIATE.getType())
                        ? CouponTaskStatusEnum.IN_PROGRESS.getStatus()
                        : CouponTaskStatusEnum.PENDING.getStatus()
        );

        // 通过 EasyExcel 监听器获取 Excel 中所有行数
        RowCountListener listener = new RowCountListener();
        EasyExcel.read(requestParam.getFileAddress(), listener).sheet().doRead();

        // 为什么需要统计行数？因为发送后需要比对所有优惠券是否都已发放到用户账号
        int totalRows = listener.getRowCount();
        couponTaskDO.setSendNum(totalRows);

        // 保存优惠券推送任务记录到数据库
        couponTaskMapper.saveCouponTask(couponTaskDO);
    }
}
