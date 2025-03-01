package com.springleaf.couponfury.engine.service;

import com.springleaf.couponfury.engine.dto.req.CouponTemplateRemindCreateReqDTO;

/**
 * 优惠券预约提醒业务逻辑层
 */
public interface CouponTemplateRemindService {

    /**
     * 创建抢券预约提醒
     *
     * @param requestParam 请求参数
     */
    void createCouponRemind(CouponTemplateRemindCreateReqDTO requestParam);
}
