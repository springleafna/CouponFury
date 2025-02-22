package com.springleaf.couponfury.merchant.admin.job;

import com.springleaf.couponfury.merchant.admin.common.enums.CouponTaskStatusEnum;
import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTaskDO;
import com.springleaf.couponfury.merchant.admin.dao.mapper.CouponTaskMapper;
import com.springleaf.couponfury.merchant.admin.mq.event.CouponTaskExecuteMessageEvent;
import com.springleaf.couponfury.merchant.admin.mq.producer.EventPublisher;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 优惠券推送任务扫描定时发送记录 XXL-JOB 任务处理器
 */
@Component
public class CouponTaskJobHandler extends IJobHandler {

    @Resource
    private CouponTaskMapper couponTaskMapper;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private CouponTaskExecuteMessageEvent couponTaskExecuteMessageEvent;

    // 每次处理的数据量
    private static final int MAX_LIMIT = 100;

    /**
     * 定时任务，扫描优惠券推送任务表，并将待执行的任务发送给用户
     * XXL_JOB 设置了该任务的执行时间为 0/5 0 0 * * ?，即每五秒执行一次
     * 判断条件为：任务状态为 PENDING待执行，且执行时间小于等于当前时间
     * 从而将优惠券推送任务表中符合条件的优惠券分发给用户
     * 每次处理的数据量为 100
     */
    @XxlJob(value = "couponTemplateTask")
    @Override
    public void execute() throws Exception {
        // 用于标识已经处理过的任务的最大 ID
        long initId = 0;
        Date now = new Date();

        while (true) {
            // 获取已到执行时间待执行的优惠券定时分发任务
            List<CouponTaskDO> couponTaskDOList = fetchPendingTasks(initId, now);

            if (couponTaskDOList.isEmpty()) {
                break;
            }

            // 调用分发任务对用户发送优惠券
            for (CouponTaskDO couponTaskDO : couponTaskDOList) {
                distributeCoupon(couponTaskDO);
            }

            // 查询出来的数据如果小于 MAX_LIMIT 意味着后面将不再有数据，返回即可
            if (couponTaskDOList.size() < MAX_LIMIT) {
                break;
            }

            // 更新 initId 为当前列表中最大 ID
            initId = couponTaskDOList.stream()
                    .mapToLong(CouponTaskDO::getId)
                    .max()
                    .orElse(initId);
        }
    }

    private void distributeCoupon(CouponTaskDO couponTask) {
        // 修改延时执行推送任务任务状态为执行中
        couponTaskMapper.updateCouponTaskStatusById(couponTask.getId(), CouponTaskStatusEnum.IN_PROGRESS.getStatus());
        // 通过消息队列发送消息，由分发服务消费者消费该消息
        eventPublisher.publish(couponTaskExecuteMessageEvent.topic(), couponTaskExecuteMessageEvent.buildEventMessage(couponTask.getId()));
    }

    private List<CouponTaskDO> fetchPendingTasks(long initId, Date now) {
        return couponTaskMapper.selectByStatusAndLimit(initId, now, CouponTaskStatusEnum.PENDING.getStatus(), MAX_LIMIT);
    }
}
