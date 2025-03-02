package com.springleaf.couponfury.engine.mq.consumer;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.springleaf.couponfury.engine.mq.event.BaseEvent;
import com.springleaf.couponfury.engine.mq.event.CouponRemindDelayEvent;
import com.springleaf.couponfury.engine.service.handler.remind.CouponTemplateRemindExecutor;
import com.springleaf.couponfury.engine.service.handler.remind.dto.CouponTemplateRemindDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 提醒抢券消费者
 */
@Slf4j(topic = "CouponTemplateRemindDelayConsumer")
@Component
public class CouponTemplateRemindDelayConsumer {

    @Resource
    private CouponTemplateRemindExecutor couponTemplateRemindExecutor;

    @Value("${spring.rabbitmq.topic.coupon-remind-delay}")
    private String topic;

    @RabbitListener(queuesToDeclare = @Queue(value = "coupon.remind.delay"))
    public void listener(String message) {
        try {
            log.info("[消费者] 提醒用户抢券 - 执行消费逻辑，topic: {}, message: {}", topic, message);
            // 转换对象
            BaseEvent.EventMessage<CouponRemindDelayEvent.CouponRemindDelayMessage> eventMessage = JSON.parseObject(message,
                    new TypeReference<BaseEvent.EventMessage<CouponRemindDelayEvent.CouponRemindDelayMessage>>() {
                    }.getType());
            CouponRemindDelayEvent.CouponRemindDelayMessage messageData = eventMessage.getData();
            CouponTemplateRemindDTO couponTemplateRemindDTO = BeanUtil.toBean(messageData, CouponTemplateRemindDTO.class);
            // 根据不同策略向用户发送消息提醒
            couponTemplateRemindExecutor.executeRemindCouponTemplate(couponTemplateRemindDTO);

        } catch (Exception e) {
            log.error("监听[消费者] 提醒用户抢券 - 消费失败 topic: {} message: {}", topic, message);
        }
    }
}
