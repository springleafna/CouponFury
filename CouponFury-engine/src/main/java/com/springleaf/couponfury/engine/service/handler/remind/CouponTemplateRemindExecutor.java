package com.springleaf.couponfury.engine.service.handler.remind;

import com.springleaf.couponfury.engine.common.enums.CouponRemindTypeEnum;
import com.springleaf.couponfury.engine.service.handler.remind.dto.CouponTemplateRemindDTO;
import com.springleaf.couponfury.engine.service.handler.remind.impl.SendAppMessageRemindCouponTemplate;
import com.springleaf.couponfury.engine.service.handler.remind.impl.SendEmailRemindCouponTemplate;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 执行相应的抢券提醒
 */
@Component
public class CouponTemplateRemindExecutor {

    @Resource
    private SendAppMessageRemindCouponTemplate sendAppMessageRemindCouponTemplate;

    @Resource
    private SendEmailRemindCouponTemplate sendEmailRemindCouponTemplate;

    /**
     * 执行提醒
     *
     * @param couponTemplateRemindDTO 用户预约提醒请求信息
     */
    public void executeRemindCouponTemplate(CouponTemplateRemindDTO couponTemplateRemindDTO) {
        // 向用户发起消息提醒
        switch (Objects.requireNonNull(CouponRemindTypeEnum.getByType(couponTemplateRemindDTO.getType()))) {
            case APP -> sendAppMessageRemindCouponTemplate.remind(couponTemplateRemindDTO);
            case EMAIL -> sendEmailRemindCouponTemplate.remind(couponTemplateRemindDTO);
            default -> {
            }
        }
    }
}
