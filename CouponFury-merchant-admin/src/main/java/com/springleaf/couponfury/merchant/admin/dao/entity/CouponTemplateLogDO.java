package com.springleaf.couponfury.merchant.admin.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 优惠券模板操作日志数据库持久层实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponTemplateLogDO {

    /**
     * id
     */
    private Long id;

    /**
     * 店铺编号
     */
    private Long shopNumber;

    /**
     * 优惠券模板ID
     */
    private String couponTemplateId;

    /**
     * 操作人
     */
    private String operatorId;

    /**
     * 操作日志
     */
    private String operationLog;

    /**
     * 原始数据
     */
    private String originalData;

    /**
     * 修改后数据
     */
    private String modifiedData;

    /**
     * 创建时间
     */
    private Date createTime;
}
