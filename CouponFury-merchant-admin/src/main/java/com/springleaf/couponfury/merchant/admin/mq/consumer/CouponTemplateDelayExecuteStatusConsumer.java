package com.springleaf.couponfury.merchant.admin.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.google.gson.JsonObject;
import com.springleaf.couponfury.merchant.admin.service.CouponTemplateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 优惠券推送延迟执行-变更记录发送状态消费者
 */
@Component
@Slf4j(topic = "CouponTemplateDelayExecuteStatusConsumer")
public class CouponTemplateDelayExecuteStatusConsumer {
    @Value("${spring.rabbitmq.topic.coupon-template-delay-execute-status}")
    private String topic;

    @Resource
    private CouponTemplateService couponTemplateService;

    @RabbitListener(queues = "coupon-template-delay-execute-status")
    public void listener(String message) {
        // 开头打印日志，平常可 Debug 看任务参数，线上可报平安（比如消息是否消费，重新投递时获取参数等）
        log.info("[消费者] 优惠券模板定时执行@变更模板表状态 - 执行消费逻辑，消息体：{}", message);
    }

}
