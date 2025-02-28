package com.springleaf.couponfury.engine.mq.event;

import com.springleaf.couponfury.engine.dto.req.CouponTemplateRedeemReqDTO;
import com.springleaf.couponfury.engine.dto.resp.CouponTemplateQueryRespDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 用户兑换优惠券事件
 */
@Component
public class UserCouponRedeemEvent extends BaseEvent<UserCouponRedeemEvent.UserCouponRedeemMessage> {

    @Value("${spring.rabbitmq.topic.user-coupon-redeem}")
    String topic;

    @Override
    public EventMessage<UserCouponRedeemMessage> buildEventMessage(UserCouponRedeemMessage data) {
        return EventMessage.<UserCouponRedeemMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(data)
                .build();
    }

    @Override
    public String topic() {
        return this.topic;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCouponRedeemMessage {
        /**
         * Web 请求参数
         */
        private CouponTemplateRedeemReqDTO requestParam;

        /**
         * 领取次数
         */
        private Integer receiveCount;

        /**
         * 优惠券模板
         */
        private CouponTemplateQueryRespDTO couponTemplate;

        /**
         * 用户 ID
         */
        private String userId;
    }

}
