package com.springleaf.couponfury.engine.dao.mapper;

import com.springleaf.couponfury.engine.dao.entity.CouponTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CouponTemplateMapper {
    /*
     * 根据优惠券模板id和店铺编号查询优惠券模板
     */
    CouponTemplateDO getCouponTemplateByShopNumberAndId(@Param("shopNumber") Long shopNumber, @Param("couponTemplateId") Long couponTemplateId);
}
