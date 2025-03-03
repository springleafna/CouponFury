package com.springleaf.couponfury.engine.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.springleaf.couponfury.engine.common.context.UserContext;
import com.springleaf.couponfury.engine.dao.entity.CouponTemplateRemindDO;
import com.springleaf.couponfury.engine.dao.mapper.CouponTemplateRemindMapper;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateQueryReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateRemindCancelReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateRemindCreateReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateRemindQueryReqDTO;
import com.springleaf.couponfury.engine.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.engine.dto.resp.CouponTemplateRemindQueryRespDTO;
import com.springleaf.couponfury.engine.mq.event.CouponRemindDelayEvent;
import com.springleaf.couponfury.engine.mq.producer.EventPublisher;
import com.springleaf.couponfury.engine.service.CouponTemplateRemindService;
import com.springleaf.couponfury.engine.service.CouponTemplateService;
import com.springleaf.couponfury.engine.service.handler.remind.dto.CouponTemplateRemindDTO;
import com.springleaf.couponfury.engine.toolkit.CouponTemplateRemindUtil;
import com.springleaf.couponfury.framework.exception.ClientException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

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
            // 或运算合并用户已设置的提醒和新设置的提醒
            couponTemplateRemindDO.setInformation(information | bitMap);

            couponTemplateRemindMapper.updateCouponTemplateRemindInformation(couponTemplateRemindDO);
        }

        // mq发送预约提醒抢购优惠券延时消息
        long delayTime = DateUtil.offsetMinute(couponTemplate.getValidStartTime(), -requestParam.getRemindTime()).getTime() - DateUtil.date().getTime();

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

    @Override
    public List<CouponTemplateRemindQueryRespDTO> listCouponRemind(CouponTemplateRemindQueryReqDTO requestParam) {
        return null;
    }

    @Override
    @Transactional
    public void cancelCouponRemind(CouponTemplateRemindCancelReqDTO requestParam) {
        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService
                .findCouponTemplate(new CouponTemplateQueryReqDTO(requestParam.getShopNumber(), requestParam.getCouponTemplateId()));
        if (couponTemplate.getValidStartTime().before(new Date())) {
            throw new ClientException("无法取消已开始领取的优惠券预约");
        }

        CouponTemplateRemindDO couponTemplateRemindDO = couponTemplateRemindMapper.getCouponRemindByUserIdAndCouponTemplateId(UserContext.getUserId(), requestParam.getCouponTemplateId());
        if (couponTemplateRemindDO == null) {
            throw new ClientException("优惠券模板预约信息不存在");
        }

        // 计算 BitMap 信息
        Long bitMap = CouponTemplateRemindUtil.calculateBitMap(requestParam.getRemindTime(), requestParam.getType());
        if ((bitMap & couponTemplateRemindDO.getInformation()) == 0L) {
            throw new ClientException("您没有预约该时间点的提醒");
        }
        bitMap ^= couponTemplateRemindDO.getInformation();
        if (bitMap.equals(0L)) {
            // 如果新 BitMap 信息是 0，说明已经没有预约提醒了，可以直接删除
            if (couponTemplateRemindMapper.deleteCouponTemplateRemind(couponTemplateRemindDO) == 0) {
                // MySQL 乐观锁进行删除，如果删除失败，说明用户可能同时正在进行删除、新增提醒操作
                throw new ClientException("取消提醒失败，请刷新页面后重试");
            }
        } else {
            // 虽然删除了这个预约提醒，但还有其它提醒，那就更新数据库
            couponTemplateRemindDO.setInformation(bitMap);
            if (couponTemplateRemindMapper.updateCouponTemplateRemindInformation(couponTemplateRemindDO) == 0) {
                // MySQL 乐观锁进行更新，如果更新失败，说明用户可能同时正在进行删除、新增提醒操作
                throw new ClientException("取消提醒失败，请刷新页面后重试");
            }
        }
    }

    @Override
    public boolean isCancelRemind(CouponTemplateRemindDTO requestParam) {
        CouponTemplateRemindDO couponTemplateRemindDO = couponTemplateRemindMapper.getCouponRemindByUserIdAndCouponTemplateId(requestParam.getUserId(), requestParam.getCouponTemplateId());
        if (couponTemplateRemindDO == null) {
            // 数据库中没该条预约提醒，说明被取消
            return true;
        }

        // 即使存在数据，也要检查该类型的该时间点是否有提醒
        Long information = couponTemplateRemindDO.getInformation();
        Long bitMap = CouponTemplateRemindUtil.calculateBitMap(requestParam.getRemindTime(), requestParam.getType());

        // 按位与等于 0 说明用户取消了预约
        return (bitMap & information) == 0L;
    }
}
