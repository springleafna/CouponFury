package com.springleaf.couponfury.merchant.admin.mq.event;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 优惠券推送任务事件消息
 * XXL-Job扫描定时分发的优惠券任务
 * 任务被执行时会通过mq发送该消息
 * 被监听后执行优惠券推送任务
 */
@Component
public class CouponTaskExecuteMessageEvent extends BaseEvent<Long>{

    @Value("${spring.rabbitmq.topic.coupon-task-execute}")

    @Override
    public EventMessage<Long> buildEventMessage(Long taskId) {
        return EventMessage.<Long>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(taskId)
                .build();
    }

    @Override
    public String topic() {
        return topic();
    }
}
