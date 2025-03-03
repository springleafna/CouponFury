package com.springleaf.couponfury.engine.mq.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class CouponRemindDelayEvent extends BaseEvent<CouponRemindDelayEvent.CouponRemindDelayMessage>{

    @Value("${spring.rabbitmq.topic.coupon-remind-delay}")
    String topic;

    @Override
    public EventMessage<CouponRemindDelayMessage> buildEventMessage(CouponRemindDelayMessage data) {
        return EventMessage.<CouponRemindDelayMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(data)
                .build();
    }

    @Override
    public String topic() {
        return this.topic;
    }

    /**
     * 优惠券提醒抢券事件
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponRemindDelayMessage {

        /**
         * 优惠券模板id
         */
        private Long couponTemplateId;

        /**
         * 店铺编号
         */
        private Long shopNumber;

        /**
         * 用户id
         */
        private String userId;

        /**
         * 用户联系方式，可能是邮箱、手机号、等等
         */
        private String contact;

        /**
         * 提醒方式
         */
        private Integer type;

        /**
         * 提醒时间，比如五分钟，十分钟，十五分钟
         */
        private Integer remindTime;

        /**
         * 开抢时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date startTime;

        /**
         * 具体延迟时间
         */
        private Long delayTime;
    }
}
