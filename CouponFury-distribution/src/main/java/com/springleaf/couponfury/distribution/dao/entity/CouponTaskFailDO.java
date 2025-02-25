package com.springleaf.couponfury.distribution.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 优惠券模板失败记录数据库持久层实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponTaskFailDO {

    /**
     * id
     */
    private Long id;

    /**
     * 批量id
     */
    private Long batchId;

    /**
     * JSON字符串，存储失败原因，Excel 行数等信息
     */
    private String jsonObject;
}
