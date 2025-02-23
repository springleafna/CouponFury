package com.springleaf.couponfury.distribution.dao.mapper;

import com.springleaf.couponfury.distribution.dao.entity.CouponTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 优惠券推送任务数据库持久层
 */
@Mapper
public interface CouponTaskMapper {
    void updateCouponTaskStatusById(CouponTaskDO couponTaskDO);

    CouponTaskDO getCouponTaskById(@Param("id") Long couponTaskId);
}
