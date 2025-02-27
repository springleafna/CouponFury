package com.springleaf.couponfury.engine.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCouponMapper {

    /**
     * 自减优惠券模板库存
     *
     * @param couponTemplateId 优惠券模板 ID
     * @return 是否发生记录变更
     */
    int decrementCouponTemplateStock(@Param("shopNumber") Long shopNumber, @Param("couponTemplateId") Long couponTemplateId, @Param("decrementStock") Long decrementStock);
}
