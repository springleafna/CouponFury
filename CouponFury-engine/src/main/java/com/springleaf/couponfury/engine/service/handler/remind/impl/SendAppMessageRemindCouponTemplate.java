package com.springleaf.couponfury.engine.service.handler.remind.impl;

import com.springleaf.couponfury.engine.service.handler.remind.RemindCouponTemplate;
import com.springleaf.couponfury.engine.service.handler.remind.dto.CouponTemplateRemindDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 应用 App 弹框方式提醒用户抢券
 */
@Slf4j
@Component
public class SendAppMessageRemindCouponTemplate implements RemindCouponTemplate {

    /**
     * 应用 App 弹框方式提醒用户抢券
     *
     * @param couponTemplateRemindDTO 提醒所需要的信息
     */
    @Override
    public boolean remind(CouponTemplateRemindDTO couponTemplateRemindDTO) {
        // 空实现
        log.info("应用 App 弹框方式提醒用户抢券");
        return true;
    }
}
