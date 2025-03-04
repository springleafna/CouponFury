package com.springleaf.couponfury.engine.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 优惠券结算实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponSettlementDO {

    /**
     * id
     */
    private Long id;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 优惠券id
     */
    private Long couponId;

    /**
     * 结算单状态 0：锁定 1：已取消 2：已支付 3：已退款
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
