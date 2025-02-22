package com.springleaf.couponfury.distribution.dao.mapper;

import com.springleaf.couponfury.distribution.dao.entity.CouponTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 优惠券模板数据库持久层
 */
@Mapper
public interface CouponTemplateMapper {

    /**
     * 自减优惠券模板库存
     *
     * @param couponTemplateId 优惠券模板 ID
     * @return 是否发生记录变更
     */
    int decrementCouponTemplateStock(@Param("shopNumber") Long shopNumber, @Param("couponTemplateId") Long couponTemplateId, @Param("decrementStock") Integer decrementStock);

    /*
     * 根据优惠券模板id和店铺编号查询优惠券模板
     */
    CouponTemplateDO getCouponTemplateByShopNumberAndId(@Param("shopNumber") Long shopNumber, @Param("couponTemplateId") Long couponTemplateId);
}
