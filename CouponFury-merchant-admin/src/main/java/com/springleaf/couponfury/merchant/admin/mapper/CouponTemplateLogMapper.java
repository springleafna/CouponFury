package com.springleaf.couponfury.merchant.admin.mapper;

import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTemplateLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券模板操作日志数据库持久层
 */
@Mapper
public interface CouponTemplateLogMapper {
    void saveCouponTemplateLog(CouponTemplateLogDO couponTemplateLogDO);
}
