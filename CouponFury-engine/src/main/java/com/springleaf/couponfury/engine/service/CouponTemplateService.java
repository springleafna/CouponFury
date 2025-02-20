package com.springleaf.couponfury.engine.service;

import com.springleaf.couponfury.engine.dto.req.CouponTemplateQueryReqDTO;
import com.springleaf.couponfury.engine.dto.resp.CouponTemplateQueryRespDTO;

/**
 * 优惠券模板业务逻辑层
 */
public interface CouponTemplateService {
    /**
     * 查询优惠券模板
     *
     * @param requestParam 请求参数
     * @return 优惠券模板信息
     */
    CouponTemplateQueryRespDTO findCouponTemplate(CouponTemplateQueryReqDTO requestParam);
}
