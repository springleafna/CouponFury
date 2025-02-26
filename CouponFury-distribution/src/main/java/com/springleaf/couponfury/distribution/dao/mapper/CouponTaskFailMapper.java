package com.springleaf.couponfury.distribution.dao.mapper;

import com.springleaf.couponfury.distribution.dao.entity.CouponTaskFailDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CouponTaskFailMapper {

    void saveCouponTaskFail(CouponTaskFailDO couponTaskFailDO);

    /**
     * 批量新增优惠券模板失败记录
     * @param couponTaskFailDOList 优惠券模板失败记录列表
     */
    void saveCouponTaskFailList(List<CouponTaskFailDO> couponTaskFailDOList);

    /**
     * 查找用户分发任务失败记录
     * @param batchId 分发任务批次ID
     * @param maxId    上次读取最大ID
     * @param batchUserCouponSize 分页限制
     * @return 分发任务失败记录列表
     */
    List<CouponTaskFailDO> getTaskFailList(@Param("batchId") Long batchId, @Param("maxId") Long maxId, @Param("batchUserCouponSize") int batchUserCouponSize);
}
