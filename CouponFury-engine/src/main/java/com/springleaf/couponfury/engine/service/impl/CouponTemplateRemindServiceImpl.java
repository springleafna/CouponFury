package com.springleaf.couponfury.engine.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.springleaf.couponfury.engine.common.context.UserContext;
import com.springleaf.couponfury.engine.dao.entity.CouponTemplateRemindDO;
import com.springleaf.couponfury.engine.dao.mapper.CouponTemplateRemindMapper;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateQueryReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateRemindCreateReqDTO;
import com.springleaf.couponfury.engine.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.engine.mq.event.CouponRemindDelayEvent;
import com.springleaf.couponfury.engine.mq.producer.EventPublisher;
import com.springleaf.couponfury.engine.service.CouponTemplateRemindService;
import com.springleaf.couponfury.engine.service.CouponTemplateService;
import com.springleaf.couponfury.engine.toolkit.CouponTemplateRemindUtil;
import com.springleaf.couponfury.framework.exception.ClientException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠券预约提醒业务逻辑实现层
 */
@Service
public class CouponTemplateRemindServiceImpl implements CouponTemplateRemindService {

    @Resource
    private CouponTemplateService couponTemplateService;
    @Resource
    private CouponTemplateRemindMapper couponTemplateRemindMapper;
    @Resource
    private CouponRemindDelayEvent couponRemindDelayEvent;
    @Resource
    private EventPublisher eventPublisher;

    @Override
    @Transactional
    public void createCouponRemind(CouponTemplateRemindCreateReqDTO requestParam) {
        // // 验证优惠券是否存在，避免缓存穿透问题并获取优惠券开抢时间
        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService
                .findCouponTemplate(new CouponTemplateQueryReqDTO(requestParam.getShopNumber(), requestParam.getCouponTemplateId()));

        // 查询用户是否已经预约过优惠券的提醒信息
        // TODO:这里的用户id需要修改
        CouponTemplateRemindDO couponTemplateRemindDO = couponTemplateRemindMapper.getCouponRemindByUserIdAndCouponTemplateId(UserContext.getUserId(), couponTemplate.getId());
        // 如果没创建过提醒
        if (couponTemplateRemindDO == null) {
            couponTemplateRemindDO = BeanUtil.toBean(requestParam, CouponTemplateRemindDO.class);

            // 设置优惠券开抢时间信息
            couponTemplateRemindDO.setStartTime(couponTemplate.getValidStartTime());
            couponTemplateRemindDO.setInformation(CouponTemplateRemindUtil.calculateBitMap(requestParam.getRemindTime(), requestParam.getType()));
            // TODO:这里的用户id需要修改
            couponTemplateRemindDO.setUserId(Long.parseLong(UserContext.getUserId()));

            couponTemplateRemindMapper.saveCouponTemplateRemind(couponTemplateRemindDO);
        } else {
            Long information = couponTemplateRemindDO.getInformation();
            Long bitMap = CouponTemplateRemindUtil.calculateBitMap(requestParam.getRemindTime(), requestParam.getType());
            if ((information & bitMap) != 0L) {
                throw new ClientException("已经创建过该提醒了");
            }
            // 异或运算合并用户已设置的提醒和新设置的提醒
            couponTemplateRemindDO.setInformation(information ^ bitMap);

            couponTemplateRemindMapper.updateCouponTemplateRemindInformation(couponTemplateRemindDO);
        }

        // mq发送预约提醒抢购优惠券延时消息
        long delayTime = DateUtil.offsetMinute(couponTemplate.getValidStartTime(), -requestParam.getRemindTime()).getTime() - DateUtil.date().getTime();
        System.out.println("----------delayTime-------:" + delayTime);

        CouponRemindDelayEvent.CouponRemindDelayMessage couponRemindDelayMessage = CouponRemindDelayEvent.CouponRemindDelayMessage.builder()
                .couponTemplateId(couponTemplate.getId())
                // TODO:这里的用户id需要修改
                .userId(UserContext.getUserId())
                .contact(UserContext.getUserId())
                .shopNumber(couponTemplate.getShopNumber())
                .type(requestParam.getType())
                .remindTime(requestParam.getRemindTime())
                .startTime(couponTemplate.getValidStartTime())
                .delayTime(delayTime)
                .build();

        eventPublisher.delayPublish(couponRemindDelayEvent.topic(), couponRemindDelayEvent.buildEventMessage(couponRemindDelayMessage), (int) delayTime);

    }
}
