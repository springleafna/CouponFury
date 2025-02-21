package com.springleaf.couponfury.distribution.dao.mapper;

import com.springleaf.couponfury.distribution.dao.entity.UserCouponDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户优惠券数据库持久层
 */
@Mapper
public interface UserCouponMapper {
    void saveUserCoupon(UserCouponDO userCouponDO);
}
