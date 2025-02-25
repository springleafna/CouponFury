package com.springleaf.couponfury.distribution.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户优惠券数据库持久层实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCouponDO {

    /**
     * id
     */
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 优惠券模板id
     */
    private Long couponTemplateId;

    /**
     * 领取时间
     */
    private Date receiveTime;

    /**
     * 领取次数
     */
    private Integer receiveCount;

    /**
     * 有效期开始时间
     */
    private Date validStartTime;

    /**
     * 有效期结束时间
     */
    private Date validEndTime;

    /**
     * 使用时间
     */
    private Date useTime;

    /**
     * 券来源 0：领券中心 1：平台发放 2：店铺领取
     */
    private Integer source;

    /**
     * 状态 0：未使用 1：锁定 2：已使用 3：已过期 4：已撤回
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

    /**
     * 删除标识 0：未删除 1：已删除
     */
    private Integer delFlag;

    /**
     * 分发 Excel 表格中用户所在的行数
     * 不建议这么写，应该再创建一个 DTO，然后进行包装转换。为了避免代码扩散，这里小小的不规范一次
     */
    private Integer rowNum;
}
