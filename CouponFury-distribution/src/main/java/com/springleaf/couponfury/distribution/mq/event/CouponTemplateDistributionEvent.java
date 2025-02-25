package com.springleaf.couponfury.distribution.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 优惠券模板任务分发执行事件
 */
@Component
public class CouponTemplateDistributionEvent extends BaseEvent<CouponTemplateDistributionEvent.CouponTemplateDistributionMessage> {

    @Value("${spring.rabbitmq.topic.coupon-execute-distribution}")
    private String topic;

    @Override
    public EventMessage<CouponTemplateDistributionMessage> buildEventMessage(CouponTemplateDistributionMessage data) {
        return EventMessage.<CouponTemplateDistributionMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(data)
                .build();
    }

    @Override
    public String topic() {
        return topic;
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponTemplateDistributionMessage {

        /**
         * 优惠券分发任务id
         */
        private Long couponTaskId;

        /**
         * 优惠券分发任务批量id
         */
        private Long couponTaskBatchId;

        /**
         * 店铺编号
         */
        private Long shopNumber;

        /**
         * 优惠券模板id
         */
        private Long couponTemplateId;

        /**
         * 消耗规则
         */
        private String couponTemplateConsumeRule;

        /**
         * 批量保存用户优惠券 Set 长度，默认满 5000 才会批量保存数据库
         */
        private Integer batchUserSetSize;

        /**
         * 分发结束标识
         */
        private Boolean distributionEndFlag;
    }

}
