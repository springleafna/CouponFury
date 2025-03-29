package com.springleaf.couponfury.merchant.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.fastjson2.JSONObject;
import com.springleaf.couponfury.framework.exception.ClientException;
import com.springleaf.couponfury.merchant.admin.common.context.UserContext;
import com.springleaf.couponfury.merchant.admin.common.enums.CouponTaskSendTypeEnum;
import com.springleaf.couponfury.merchant.admin.common.enums.CouponTaskStatusEnum;
import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTaskDO;
import com.springleaf.couponfury.merchant.admin.dao.mapper.CouponTaskMapper;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTaskCreateReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.merchant.admin.mq.event.CouponTaskExecuteMessageEvent;
import com.springleaf.couponfury.merchant.admin.mq.producer.EventPublisher;
import com.springleaf.couponfury.merchant.admin.service.CouponTaskService;
import com.springleaf.couponfury.merchant.admin.service.CouponTemplateService;
import com.springleaf.couponfury.merchant.admin.service.handle.excel.RowCountListener;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.*;

@Service
@Slf4j
public class CouponTaskServiceImpl implements CouponTaskService {

    @Resource
    private CouponTaskMapper couponTaskMapper;
    @Resource
    private CouponTemplateService couponTemplateService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private CouponTaskExecuteMessageEvent couponTaskExecuteMessageEvent;

    /**
     * 为什么这里拒绝策略使用直接丢弃任务？因为在发送任务时如果遇到发送数量为空，会重新进行统计
     * corePoolSize：因为属于后管任务，大概率不会很频繁，所以直接取服务器 CPU 核数。
     * maximumPoolSize：运行任务属于 IO 密集型，最大线程数直接服务器 CPU 核数 2 倍。
     * workQueue：理论上说我们不会有阻塞的情况，因为设置的线程数不少，所以如果使用不存储任务的同步队列。
     * handler：如果线程数都在运行，直接将任务丢弃即可，因为我们还有延时队列兜底。
     */
    private final ExecutorService executorService = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() << 1,
            60,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.DiscardPolicy()
    );

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void createCouponTask(CouponTaskCreateReqDTO requestParam) {
        // 验证非空参数
        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplateById(requestParam.getCouponTemplateId());
        if (couponTemplate == null) {
            throw new ClientException("优惠券模板不存在，请检查提交信息是否正确");
        }
        // 验证定时发送，发送时间是否为空
        if (requestParam.getSendType() == CouponTaskSendTypeEnum.SCHEDULED.getType() && requestParam.getSendTime() == null) {
            throw new ClientException("定时发送时，发送时间不能为空");
        }

        // 构建优惠券推送任务数据库持久层实体
        CouponTaskDO couponTaskDO = BeanUtil.copyProperties(requestParam, CouponTaskDO.class);
        couponTaskDO.setBatchId(IdUtil.getSnowflakeNextId());
        couponTaskDO.setOperatorId(Long.parseLong(UserContext.getUserId()));
        couponTaskDO.setShopNumber(UserContext.getShopNumber());
        couponTaskDO.setStatus(
                Objects.equals(requestParam.getSendType(), CouponTaskSendTypeEnum.IMMEDIATE.getType())
                        ? CouponTaskStatusEnum.IN_PROGRESS.getStatus()
                        : CouponTaskStatusEnum.PENDING.getStatus()
        );

        // 保存优惠券推送任务记录到数据库
        couponTaskMapper.saveCouponTask(couponTaskDO);

        // 为什么需要统计行数？因为发送后需要比对所有优惠券是否都已发放到用户账号
        // 100 万数据大概需要 4 秒才能返回前端，如果加上验证将会时间更长，所以这里将最耗时的统计操作异步化
        JSONObject delayJsonObject = JSONObject
                .of("fileAddress", requestParam.getFileAddress(), "couponTaskId", couponTaskDO.getId());

        executorService.execute(() -> {
            try {
                refreshCouponTaskSendNum(delayJsonObject);
            } catch (Exception e) {
                log.error("统计行数失败: {}", delayJsonObject, e);
            }
        });

        // 假设刚把消息提交到线程池，突然应用宕机了，我们通过延迟队列进行兜底 Refresh
        RBlockingDeque<Object> blockingDeque = redissonClient.getBlockingDeque("COUPON_TASK_SEND_NUM_DELAY_QUEUE");
        RDelayedQueue<Object> delayedQueue = redissonClient.getDelayedQueue(blockingDeque);
        // 这里延迟时间设置 20 秒，原因是我们笃定上面线程池 20 秒之内就能结束任务
        delayedQueue.offer(delayJsonObject, 20, TimeUnit.SECONDS);

        // 如果是立即发送任务，直接调用消息队列进行发送流程
        if (Objects.equals(requestParam.getSendType(), CouponTaskSendTypeEnum.IMMEDIATE.getType())) {
            eventPublisher.publish(couponTaskExecuteMessageEvent.topic(), couponTaskExecuteMessageEvent.buildEventMessage(couponTaskDO.getId()));
        }
    }

    private void refreshCouponTaskSendNum(JSONObject delayJsonObject) {
        String fileAddress = delayJsonObject.getString("fileAddress");
        Long id = delayJsonObject.getLong("couponTaskId");
        if (fileAddress == null || id == null) {
            throw new ClientException("刷新优惠券推送任务发送行数参数错误，请检查参数是否正确：fileAddress: " + fileAddress + ", couponTaskId: " + id);
        }
        log.info("开始统计优惠券推送任务 {} 的发送行数，文件地址: {}", id, fileAddress);

        // 通过 EasyExcel 监听器获取 Excel 中所有行数
        RowCountListener listener = new RowCountListener();
        EasyExcel.read(fileAddress, listener)
                .sheet()
                .doRead();
        int totalRows = listener.getRowCount();

        // 刷新优惠券推送记录中发送行数
        couponTaskMapper.updateCouponTaskSendNumById(id, totalRows);
    }

    /**
     * 优惠券延迟刷新发送条数兜底消费者｜这是兜底策略，一般来说不会执行这段逻辑
     * 如果延迟消息没有持久化成功，或者 Redis 挂了怎么办？后续可以人工处理
     */
    @Service
    @RequiredArgsConstructor
    class RefreshCouponTaskDelayQueueRunner implements CommandLineRunner {

        private final CouponTaskMapper couponTaskMapper;
        private final RedissonClient redissonClient;

        @Override
        public void run(String... args) throws Exception {
            Executors.newSingleThreadExecutor(
                            runnable -> {
                                Thread thread = new Thread(runnable);
                                thread.setName("delay_coupon-task_send-num_consumer");
                                thread.setDaemon(Boolean.TRUE);
                                return thread;
                            })
                    .execute(() -> {
                        RBlockingDeque<JSONObject> blockingDeque = redissonClient.getBlockingDeque("COUPON_TASK_SEND_NUM_DELAY_QUEUE");
                        for (; ; ) {
                            try {
                                // 获取延迟队列已到达时间元素
                                JSONObject delayJsonObject = blockingDeque.take();
                                if (delayJsonObject != null) {
                                    // 获取优惠券推送记录，查看发送条数是否已经有值，有的话代表上面线程池已经处理完成，无需再处理
                                    CouponTaskDO couponTaskDO = couponTaskMapper.selectCouponTaskById(delayJsonObject.getLong("couponTaskId"));
                                    if (couponTaskDO.getSendNum() == null) {
                                        refreshCouponTaskSendNum(delayJsonObject);
                                    }
                                }
                            } catch (Throwable ignored) {
                                // 忽略异常，继续轮询延迟队列
                            }
                        }
                    });
        }
    }
}
