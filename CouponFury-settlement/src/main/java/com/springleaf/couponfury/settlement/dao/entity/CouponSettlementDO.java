package com.springleaf.couponfury.settlement.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponSettlementDO {

    /**
     * 结算单ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 店铺编号
     */
    private Long shopNumber;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 结算单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 应付金额
     */
    private BigDecimal payableAmount;

    /**
     * 券金额
     */
    private BigDecimal couponAmount;

    /**
     * 结算单状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改时间
     */
    private Date updateTime;
}
