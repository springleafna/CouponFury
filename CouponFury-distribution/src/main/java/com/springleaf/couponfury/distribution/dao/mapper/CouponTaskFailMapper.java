package com.springleaf.couponfury.distribution.dao.mapper;

import com.springleaf.couponfury.distribution.dao.entity.CouponTaskFailDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CouponTaskFailMapper {
    void saveCouponTaskFail(CouponTaskFailDO couponTaskFailDO);

    /**
     * 批量新增优惠券模板失败记录
     * @param couponTaskFailDOList 优惠券模板失败记录列表
     */
    void saveCouponTaskFailList(List<CouponTaskFailDO> couponTaskFailDOList);
}
