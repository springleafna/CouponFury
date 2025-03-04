package com.springleaf.couponfury.engine.dao.mapper;

import com.springleaf.couponfury.engine.dao.entity.CouponSettlementDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CouponSettlementMapper {
    CouponSettlementDO getCouponSettlementByCouponIdAndUserIdInStatus(@Param("couponId") Long couponId, @Param("userId") Long userId);

    void saveCouponSettlement(CouponSettlementDO couponSettlementDO);

    int updateCouponSettlementStatus(@Param("orderId") Long orderId, @Param("couponId") Long couponId, @Param("userId") Long userId, @Param("oldStatus") int oldStatus, @Param("freshStatus") int freshStatus);
}
