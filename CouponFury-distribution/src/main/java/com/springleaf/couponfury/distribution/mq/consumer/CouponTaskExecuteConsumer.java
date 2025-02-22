package com.springleaf.couponfury.distribution.mq.consumer;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.springleaf.couponfury.distribution.common.enums.CouponTaskStatusEnum;
import com.springleaf.couponfury.distribution.common.enums.CouponTemplateStatusEnum;
import com.springleaf.couponfury.distribution.dao.entity.CouponTaskDO;
import com.springleaf.couponfury.distribution.dao.entity.CouponTemplateDO;
import com.springleaf.couponfury.distribution.dao.mapper.CouponTaskMapper;
import com.springleaf.couponfury.distribution.dao.mapper.CouponTemplateMapper;
import com.springleaf.couponfury.distribution.dao.mapper.UserCouponMapper;
import com.springleaf.couponfury.distribution.mq.event.BaseEvent;
import com.springleaf.couponfury.distribution.service.handler.excel.CouponTaskExcelObject;
import com.springleaf.couponfury.distribution.service.handler.excel.ReadExcelDistributionListener;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j(topic = "CouponTaskExecuteConsumer")
@Component
public class CouponTaskExecuteConsumer {

    @Value("${spring.rabbitmq.topic.coupon-task-execute}")
    private String topic;

    @Resource
    private CouponTaskMapper couponTaskMapper;
    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private UserCouponMapper userCouponMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queuesToDeclare = @Queue(value = "coupon-task-execute"))
    public void listen(String message) {
        try {
            log.info("监听活动sku库存消耗为0消息 topic: {} message: {}", topic, message);
            // 转换对象
            BaseEvent.EventMessage<Long> eventMessage = JSON.parseObject(message, new TypeReference<BaseEvent.EventMessage<Long>>() {
            }.getType());
            Long couponTaskId = eventMessage.getData();
            // 判断优惠券模板发送状态是否为执行中，如果不是有可能是被取消状态
            CouponTaskDO couponTaskDO = couponTaskMapper.selectCouponTaskById(couponTaskId);
            if (ObjectUtil.notEqual(couponTaskDO.getStatus(), CouponTaskStatusEnum.IN_PROGRESS.getStatus())) {
                log.warn("[消费者] 优惠券推送任务正式执行 - 推送任务记录状态异常：{}，已终止推送", couponTaskDO.getStatus());
                return;
            }

            // 判断优惠券状态是否正确
            CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(couponTaskDO.getShopNumber(), couponTaskDO.getCouponTemplateId());
            Integer status = couponTemplateDO.getStatus();
            if (ObjectUtil.notEqual(status, CouponTemplateStatusEnum.ACTIVE.getStatus())) {
                log.error("[消费者] 优惠券推送任务正式执行 - 优惠券ID：{}，优惠券模板状态：{}", couponTaskDO.getCouponTemplateId(), status);
                return;
            }

            // 正式开始执行优惠券推送任务
            ReadExcelDistributionListener readExcelDistributionListener = new ReadExcelDistributionListener(
                    couponTaskId,
                    couponTemplateDO,
                    stringRedisTemplate,
                    couponTemplateMapper,
                    userCouponMapper,
                    couponTaskMapper
            );
            EasyExcel.read(couponTaskDO.getFileAddress(), CouponTaskExcelObject.class, readExcelDistributionListener).sheet().doRead();

        } catch (Exception e) {
            log.error("监听活动sku库存消耗为0消息，消费失败 topic: {} message: {}", topic, message);
            throw e;
        }
    }

}
