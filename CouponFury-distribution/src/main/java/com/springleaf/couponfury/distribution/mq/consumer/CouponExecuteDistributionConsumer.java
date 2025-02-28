package com.springleaf.couponfury.distribution.mq.consumer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springleaf.couponfury.distribution.common.constant.DistributionRedisConstant;
import com.springleaf.couponfury.distribution.common.constant.EngineRedisConstant;
import com.springleaf.couponfury.distribution.common.enums.CouponSourceEnum;
import com.springleaf.couponfury.distribution.common.enums.CouponStatusEnum;
import com.springleaf.couponfury.distribution.common.enums.CouponTaskStatusEnum;
import com.springleaf.couponfury.distribution.dao.entity.CouponTaskDO;
import com.springleaf.couponfury.distribution.dao.entity.CouponTaskFailDO;
import com.springleaf.couponfury.distribution.dao.entity.CouponTemplateDO;
import com.springleaf.couponfury.distribution.dao.entity.UserCouponDO;
import com.springleaf.couponfury.distribution.dao.mapper.CouponTaskFailMapper;
import com.springleaf.couponfury.distribution.dao.mapper.CouponTaskMapper;
import com.springleaf.couponfury.distribution.dao.mapper.CouponTemplateMapper;
import com.springleaf.couponfury.distribution.dao.mapper.UserCouponMapper;
import com.springleaf.couponfury.distribution.mq.event.BaseEvent;
import com.springleaf.couponfury.distribution.mq.event.CouponTemplateDistributionEvent;
import com.springleaf.couponfury.distribution.service.handler.excel.UserCouponTaskFailExcelObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchExecutorException;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.*;

/**
 * 进行优惠券分发的执行 分发到每个用户消费者
 * 将优惠券分发到用户账户，包括数据库和缓存等多个存储介质。
 */
@Slf4j(topic = "CouponExecuteDistributionConsumer")
@Component
public class CouponExecuteDistributionConsumer {

    @Value("${spring.rabbitmq.topic.coupon-execute-distribution}")
    private String topic;

    @Resource
    private UserCouponMapper userCouponMapper;
    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private CouponTaskMapper couponTaskMapper;
    @Resource
    private CouponTaskFailMapper couponTaskFailMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Lazy
    @Resource
    private CouponExecuteDistributionConsumer couponExecuteDistributionConsumer;

    private final static int BATCH_USER_COUPON_SIZE = 1000;
    private static final String BATCH_SAVE_USER_COUPON_LUA_PATH = "lua/batch_user_coupon_list.lua";
    // 失败的记录保存到 /tm p目录下的 Excel 文件中
    private final String excelPath = Paths.get("").toAbsolutePath() + "/tmp";


    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queuesToDeclare = @Queue(value = "coupon.execute.distribution"))
    public void listener(String message) throws JsonProcessingException {
        try {
            log.info("[消费者] 优惠券推送任务正式执行 - 执行消费逻辑，topic: {}, message: {}", topic, message);
            // 将消息转换成CouponTemplateDistributionMessage对象
            BaseEvent.EventMessage<CouponTemplateDistributionEvent.CouponTemplateDistributionMessage> eventMessage = JSON.parseObject(message,
                    new TypeReference<BaseEvent.EventMessage<CouponTemplateDistributionEvent.CouponTemplateDistributionMessage>>() {
                    }.getType());

            CouponTemplateDistributionEvent.CouponTemplateDistributionMessage messageData = eventMessage.getData();

            // 判断保存用户优惠券集合是否达到批量保存数量
            if (!messageData.getDistributionEndFlag() && messageData.getBatchUserSetSize() % BATCH_USER_COUPON_SIZE == 0) {
                decrementCouponTemplateStockAndSaveUserCouponList(messageData);
            }

            // 分发任务结束标识为 TRUE，代表已经没有 Excel 记录了
            if (messageData.getDistributionEndFlag()) {
                String batchUserSetKey = String.format(DistributionRedisConstant.TEMPLATE_TASK_EXECUTE_BATCH_USER_KEY, messageData.getCouponTaskId());
                Long batchUserIdsSize = stringRedisTemplate.opsForSet().size(batchUserSetKey);
                if (batchUserIdsSize != null) {
                    messageData.setBatchUserSetSize(batchUserIdsSize.intValue());
                }

                decrementCouponTemplateStockAndSaveUserCouponList(messageData);
                List<String> batchUserMaps = stringRedisTemplate.opsForSet().pop(batchUserSetKey, Integer.MAX_VALUE);
                // 此时待保存入库用户优惠券列表如果还有值，就意味着可能库存不足引起的
                if (CollUtil.isNotEmpty(batchUserMaps)) {
                    // 添加到 t_coupon_task_fail 并标记错误原因，方便后续查看未成功发送的原因和记录
                    List<CouponTaskFailDO> couponTaskFailDOList = new ArrayList<>(batchUserMaps.size());
                    for (String batchUserMapStr : batchUserMaps) {
                        Map<Object, Object> objectMap = MapUtil.builder()
                                .put("rowNum", JSON.parseObject(batchUserMapStr).get("rowNum"))
                                .put("cause", "优惠券模板库存不足")
                                .build();
                        CouponTaskFailDO couponTaskFailDO = CouponTaskFailDO.builder()
                                .batchId(messageData.getCouponTaskBatchId())
                                .jsonObject(JSON.toJSONString(objectMap))
                                .build();
                        couponTaskFailDOList.add(couponTaskFailDO);
                    }

                    // 添加到 t_coupon_task_fail 并标记错误原因
                    couponTaskFailMapper.saveCouponTaskFailList(couponTaskFailDOList);
                }

                // 分页查询的起始 ID
                // 每次查询的记录为 initId ~ BATCH_USER_COUPON_SIZE，比如： 0~1000，1000~2000，2000~3000。。。。。。
                long initId = 0;
                // 用于标识是否为第一次迭代 (判断是否需要创建 Excel 文件 如果第一次迭代发现无数据，则将 failFileAddress 设置为 null 不创建 Excel 文件)
                boolean isFirstIteration = true;
                String failFileAddress = excelPath + "/用户分发记录失败Excel-" + messageData.getCouponTaskBatchId() + ".xlsx";

                // 这里应该上传云 OSS 或者 MinIO 等存储平台，但是增加部署成功并且不太好往简历写就仅写入本地
                try (ExcelWriter excelWriter = EasyExcel.write(failFileAddress, UserCouponTaskFailExcelObject.class).build()) {
                    WriteSheet writeSheet = EasyExcel.writerSheet("用户分发失败Sheet").build();
                    while (true) {
                        List<CouponTaskFailDO> couponTaskFailDOList = listUserCouponTaskFail(messageData.getCouponTaskBatchId(), initId);
                        if (CollUtil.isEmpty(couponTaskFailDOList)) {
                            // 如果是第一次迭代且集合为空，则设置 failFileAddress 为 null
                            if (isFirstIteration) {
                                failFileAddress = null;
                            }
                            break;
                        }

                        // 标记第一次迭代已经完成
                        isFirstIteration = false;

                        // 将失败行数和失败原因写入 Excel 文件
                        List<UserCouponTaskFailExcelObject> excelDataList = couponTaskFailDOList.stream()
                                .map(each -> JSONObject.parseObject(each.getJsonObject(), UserCouponTaskFailExcelObject.class))
                                .toList();
                        excelWriter.write(excelDataList, writeSheet);

                        // 查询出来的数据如果小于 BATCH_USER_COUPON_SIZE 意味着后面将不再有数据，返回即可
                        if (couponTaskFailDOList.size() < BATCH_USER_COUPON_SIZE) {
                            break;
                        }

                        // 更新 initId 为当前列表中最大 ID
                        initId = couponTaskFailDOList.stream()
                                .mapToLong(CouponTaskFailDO::getId)
                                .max()
                                .orElse(initId);
                    }
                }

                // 确保所有用户都已经接到优惠券后，设置优惠券推送任务完成时间
                CouponTaskDO couponTaskDO = CouponTaskDO.builder()
                        .id(messageData.getCouponTaskId())
                        .status(CouponTaskStatusEnum.SUCCESS.getStatus())
                        .completionTime(new Date())
                        .failFileAddress(failFileAddress)
                        .build();
                couponTaskMapper.updateCouponTaskStatusById(couponTaskDO);
            }

        } catch (Exception e) {
            log.error("监听[消费者] 优惠券推送任务，消费失败 topic: {} message: {}", topic, message);
            throw e;
        }
    }

    private void decrementCouponTemplateStockAndSaveUserCouponList(CouponTemplateDistributionEvent.CouponTemplateDistributionMessage event) throws JsonProcessingException {
        // 如果等于 0 意味着已经没有了库存，直接返回即可
        Integer couponTemplateStock = decrementCouponTemplateStock(event, event.getBatchUserSetSize());
        if (couponTemplateStock <= 0) {
            return;
        }

        // 获取 Redis 中待保存入库用户优惠券列表
        String batchUserSetKey = String.format(DistributionRedisConstant.TEMPLATE_TASK_EXECUTE_BATCH_USER_KEY, event.getCouponTaskId());
        List<String> batchUserMaps = stringRedisTemplate.opsForSet().pop(batchUserSetKey, couponTemplateStock);

        // 因为 batchUserIds 数据较多，ArrayList 会进行数次扩容，为了避免额外性能消耗，直接初始化 batchUserIds 大小的数组
        List<UserCouponDO> userCouponDOList = null;
        if (batchUserMaps != null) {
            userCouponDOList = new ArrayList<>(batchUserMaps.size());
        }

        Date now = new Date();
        // 构建 userCouponDOList 用户优惠券批量数组
        if (batchUserMaps != null) {
            for (String each : batchUserMaps) {
                JSONObject userIdAndRowNumJsonObject = JSON.parseObject(each);
                DateTime validEndTime = DateUtil.offsetHour(now, JSON.parseObject(event.getCouponTemplateConsumeRule()).getInteger("validityPeriod"));
                UserCouponDO userCouponDO = UserCouponDO.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .couponTemplateId(event.getCouponTemplateId())
                        .rowNum(userIdAndRowNumJsonObject.getInteger("rowNum"))
                        .userId(userIdAndRowNumJsonObject.getLong("userId"))
                        .receiveTime(now)
                        .receiveCount(1) // 代表第一次领取该优惠券
                        .validStartTime(now)
                        .validEndTime(validEndTime)
                        .source(CouponSourceEnum.PLATFORM.getType())
                        .status(CouponStatusEnum.EFFECTIVE.getType())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .delFlag(0)
                        .build();
                userCouponDOList.add(userCouponDO);
            }
        }
        // 平台优惠券每个用户限领一次。批量新增用户优惠券记录，底层通过递归方式直到全部新增成功
        batchSaveUserCouponList(event.getCouponTemplateId(), event.getCouponTaskBatchId(), userCouponDOList);

        // 将这些优惠券添加到用户的领券记录 Redis缓存 中
        List<String> userIdList = null;
        if (userCouponDOList != null) {
            userIdList = userCouponDOList.stream()
                    .map(UserCouponDO::getUserId)
                    .map(String::valueOf)
                    .toList();
        }
        String userIdsJson = new ObjectMapper().writeValueAsString(userIdList);

        List<String> couponIdList = null;
        if (userCouponDOList != null) {
            couponIdList = userCouponDOList.stream()
                    .map(each -> StrUtil.builder()
                            .append(event.getCouponTemplateId())
                            .append("_")
                            .append(each.getId())
                            .toString())
                    .map(String::valueOf)
                    .toList();
        }
        String couponIdsJson = new ObjectMapper().writeValueAsString(couponIdList);

        // 调用 Lua 脚本时，传递参数
        List<String> keys = List.of(EngineRedisConstant.USER_COUPON_TEMPLATE_LIST_KEY);
        List<String> args = Arrays.asList(userIdsJson, couponIdsJson, String.valueOf(new Date().getTime()));

        // 获取 LUA 脚本，并保存到 Hutool 的单例管理容器，下次直接获取不需要加载
        DefaultRedisScript<Void> buildLuaScript = Singleton.get(BATCH_SAVE_USER_COUPON_LUA_PATH, () -> {
            DefaultRedisScript<Void> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(BATCH_SAVE_USER_COUPON_LUA_PATH)));
            redisScript.setResultType(Void.class);
            return redisScript;
        });
        stringRedisTemplate.execute(buildLuaScript, keys, args.toArray());
    }

    // 扣减数据库中优惠券模板的库存
    private Integer decrementCouponTemplateStock(CouponTemplateDistributionEvent.CouponTemplateDistributionMessage event, Integer decrementStockSize) {
        // 通过乐观机制自减优惠券库存记录
        Long couponTemplateId = event.getCouponTemplateId();
        int decremented = couponTemplateMapper.decrementCouponTemplateStock(event.getShopNumber(), couponTemplateId, decrementStockSize);

        // 如果修改记录失败，意味着优惠券库存已不足，需要重试获取到可自减的库存数值
        if (decremented < 0) {
            CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(event.getShopNumber(), couponTemplateId);
            return decrementCouponTemplateStock(event, couponTemplateDO.getStock());
        }

        return decrementStockSize;
    }

    private void batchSaveUserCouponList(Long couponTemplateId, Long couponTaskBatchId, List<UserCouponDO> userCouponDOList) {
        // 批量保存用户优惠券记录
        try {
            userCouponMapper.saveUserCouponList(userCouponDOList);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof BatchExecutorException) {
                // 添加到 t_coupon_task_fail 并标记错误原因，方便后续查看未成功发送的原因和记录
                List<CouponTaskFailDO> couponTaskFailDOList = new ArrayList<>();
                List<UserCouponDO> toRemove = new ArrayList<>();

                // 调用批量新增失败后，为了避免大量重复失败，我们通过新增单条记录方式执行
                userCouponDOList.forEach(each -> {
                    try {
                        userCouponMapper.saveUserCoupon(each);
                    } catch (Exception ignored) {
                        // 查询用户是否已经领取过优惠券
                        Boolean hasReceived = couponExecuteDistributionConsumer.hasUserReceivedCoupon(couponTemplateId, each.getUserId());
                        if (hasReceived) {
                            // 添加到 t_coupon_task_fail 并标记错误原因，方便后续查看未成功发送的原因和记录
                            Map<Object, Object> objectMap = MapUtil.builder()
                                    .put("rowNum", each.getRowNum())
                                    .put("cause", "用户已领取该优惠券")
                                    .build();
                            CouponTaskFailDO couponTaskFailDO = CouponTaskFailDO.builder()
                                    .batchId(couponTaskBatchId)
                                    .jsonObject(JSON.toJSONString(objectMap))
                                    .build();
                            couponTaskFailDOList.add(couponTaskFailDO);

                            // 从 userCouponDOList 中删除已经存在的记录
                            toRemove.add(each);
                        }
                    }
                });

                // 批量新增 t_coupon_task_fail 表
                couponTaskFailMapper.saveCouponTaskFailList(couponTaskFailDOList);

                // 删除已经重复的内容
                userCouponDOList.removeAll(toRemove);
                return;
            }

            throw e;
        }
    }

    /**
     * 查询用户是否已经领取过优惠券
     *
     * @param couponTemplateId 优惠券模板 ID
     * @param userId           用户 ID
     * @return 用户优惠券模板领取信息是否已存在
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Boolean hasUserReceivedCoupon(Long couponTemplateId, Long userId) {
        UserCouponDO userCouponDO = userCouponMapper.getUserCouponByCouponTemplateIdAndUserId(couponTemplateId, userId);
        return userCouponDO != null;
    }

    /**
     * 查询用户分发任务失败记录
     *
     * @param batchId 分发任务批次 ID
     * @param maxId   上次读取最大 ID
     * @return 用户分发任务失败记录集合
     */
    private List<CouponTaskFailDO> listUserCouponTaskFail(Long batchId, Long maxId) {
        return couponTaskFailMapper.getTaskFailList(batchId, maxId, BATCH_USER_COUPON_SIZE);
    }
}