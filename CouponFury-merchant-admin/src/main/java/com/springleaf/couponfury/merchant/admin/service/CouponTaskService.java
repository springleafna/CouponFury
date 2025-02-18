package com.springleaf.couponfury.merchant.admin.service;

import com.springleaf.couponfury.merchant.admin.dto.req.CouponTaskCreateReqDTO;

public interface CouponTaskService {
    /**
     * 商家创建优惠券推送任务
     *
     * @param requestParam 请求参数
     */
    void createCouponTask(CouponTaskCreateReqDTO requestParam);
}
