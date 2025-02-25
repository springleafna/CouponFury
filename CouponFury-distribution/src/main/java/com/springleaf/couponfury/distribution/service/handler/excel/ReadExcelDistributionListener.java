package com.springleaf.couponfury.distribution.service.handler.excel;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.fastjson2.JSON;
import com.springleaf.couponfury.distribution.common.constant.DistributionRedisConstant;
import com.springleaf.couponfury.distribution.common.constant.EngineRedisConstant;
import com.springleaf.couponfury.distribution.dao.entity.CouponTaskDO;
import com.springleaf.couponfury.distribution.dao.entity.CouponTaskFailDO;
import com.springleaf.couponfury.distribution.dao.entity.CouponTemplateDO;
import com.springleaf.couponfury.distribution.dao.mapper.CouponTaskFailMapper;
import com.springleaf.couponfury.distribution.mq.event.CouponTemplateDistributionEvent;
import com.springleaf.couponfury.distribution.mq.producer.EventPublisher;
import com.springleaf.couponfury.distribution.toolkit.StockDecrementReturnCombinedUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Map;

/**
 * 优惠券任务读取 Excel 分发监听器
 * 不要被Spring管理 因为每次读取 Excel 都会创建一个新的实例
 * 所以不要使用@Component注解
 * 参数使用构造函数注入
 * 重写的invoke方法会在每次读取 Excel 行数据时被调用
 * 重写的doAfterAllAnalysed方法会在整个数据解析完成后被调用一次
 */
public class ReadExcelDistributionListener extends AnalysisEventListener<CouponTaskExcelObject> {

    private final CouponTaskDO couponTaskDO;
    private final CouponTemplateDO couponTemplateDO;
    private final StringRedisTemplate stringRedisTemplate;
    private final CouponTaskFailMapper couponTaskFailMapper;
    private final CouponTemplateDistributionEvent couponTemplateDistributionEvent;
    private final EventPublisher eventPublisher;

    private int rowCount = 1;
    private final static String STOCK_DECREMENT_AND_BATCH_SAVE_USER_RECORD_LUA_PATH = "lua/stock_decrement_and_batch_save_user_record.lua";
    // 每次批量分发用户优惠券的数量 每1000条记录发送一次消息 进行一次分发
    private final static int BATCH_USER_COUPON_SIZE = 1000;

    public ReadExcelDistributionListener(CouponTaskDO couponTaskDO, CouponTemplateDO couponTemplateDO, StringRedisTemplate stringRedisTemplate, CouponTaskFailMapper couponTaskFailMapper, CouponTemplateDistributionEvent couponTemplateDistributionEvent, EventPublisher eventPublisher) {
        this.couponTaskDO = couponTaskDO;
        this.couponTemplateDO = couponTemplateDO;
        this.stringRedisTemplate = stringRedisTemplate;
        this.couponTaskFailMapper = couponTaskFailMapper;
        this.couponTemplateDistributionEvent = couponTemplateDistributionEvent;
        this.eventPublisher = eventPublisher;
    }


    /**
     * 此方法会在Excel文件每读一行都会被调用一次
     * @param data  优惠券推送任务 Excel 元数据实体
     * @param context   分析上下文
     */
    @Override
    public void invoke(CouponTaskExcelObject data, AnalysisContext context) {

        Long couponTaskId = couponTaskDO.getId();

        // 获取当前进度，判断是否已经执行过。如果已执行，则跳过即可，防止执行到一半应用宕机
        String templateTaskExecuteProgressKey = String.format(DistributionRedisConstant.TEMPLATE_TASK_EXECUTE_PROGRESS_KEY, couponTaskId);
        String progress = stringRedisTemplate.opsForValue().get(templateTaskExecuteProgressKey);
        if (StrUtil.isNotBlank(progress) && Integer.parseInt(progress) >= rowCount) {
            // 执行下一行，rowCount加1
            ++rowCount;
            return;
        }

        // 获取 LUA 脚本，并保存到 Hutool 的单例管理容器，下次直接获取不需要加载
        DefaultRedisScript<Long> buildLuaScript = Singleton.get(STOCK_DECREMENT_AND_BATCH_SAVE_USER_RECORD_LUA_PATH, () -> {
            // 第一次执行时会加载 Lua 脚本
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            // 从路径中加载 Lua 脚本
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(STOCK_DECREMENT_AND_BATCH_SAVE_USER_RECORD_LUA_PATH)));
            // 设置返回类型为Long
            redisScript.setResultType(Long.class);
            return redisScript;
        });

        // 执行 LUA 脚本进行 扣减库存 以及增加 Redis 用户领券记录
        String couponTemplateKey = String.format(EngineRedisConstant.COUPON_TEMPLATE_KEY, couponTemplateDO.getId());
        String batchUserSetKey = String.format(DistributionRedisConstant.TEMPLATE_TASK_EXECUTE_BATCH_USER_KEY, couponTaskId);
        Map<Object, Object> userRowNumMap = MapUtil.builder()
                .put("userId", data.getUserId())
                .put("rowNum", rowCount + 1)
                .build();
        Long combinedFiled = stringRedisTemplate.execute(buildLuaScript, ListUtil.of(couponTemplateKey, batchUserSetKey), JSON.toJSONString(userRowNumMap));

        // firstField 为 false 说明优惠券已经没有库存了
        boolean firstField = StockDecrementReturnCombinedUtil.extractFirstField(combinedFiled);
        if (!firstField) {
            // 同步当前执行进度到缓存
            stringRedisTemplate.opsForValue().set(templateTaskExecuteProgressKey, String.valueOf(rowCount));
            // 执行下一行，rowCount加1
            ++rowCount;

            // 添加到 t_coupon_task_fail 并标记错误原因，方便后续查看未成功发送的原因和记录
            Map<Object, Object> objectMap = MapUtil.builder()
                    .put("rowNum", rowCount + 1)
                    .put("cause", "优惠券模板无库存")
                    .build();
            CouponTaskFailDO couponTaskFailDO = CouponTaskFailDO.builder()
                    .batchId(couponTaskDO.getBatchId())
                    .jsonObject(JSON.toJSONString(objectMap))
                    .build();
            couponTaskFailMapper.saveCouponTaskFail(couponTaskFailDO);
            return;
        }

        // 获取用户领券集合长度
        int batchUserSetSize = StockDecrementReturnCombinedUtil.extractSecondField(combinedFiled.intValue());

        // batchUserSetSize = BATCH_USER_COUPON_SIZE 时发送消息消费，不满足条件仅记录执行进度即可
        if (batchUserSetSize < BATCH_USER_COUPON_SIZE || batchUserSetSize % BATCH_USER_COUPON_SIZE != 0) {
            // 同步当前 Excel 执行进度到缓存
            stringRedisTemplate.opsForValue().set(templateTaskExecuteProgressKey, String.valueOf(rowCount));
            ++rowCount;
            return;
        }

        // 发送消息队列执行用户优惠券模板分发逻辑
        CouponTemplateDistributionEvent.CouponTemplateDistributionMessage couponTemplateDistributionMessage = CouponTemplateDistributionEvent.CouponTemplateDistributionMessage.builder()
                .couponTaskId(couponTaskId)
                .shopNumber(couponTaskDO.getShopNumber())
                .couponTemplateId(couponTemplateDO.getId())
                .couponTaskBatchId(couponTaskDO.getBatchId())
                .couponTemplateConsumeRule(couponTemplateDO.getConsumeRule())
                .batchUserSetSize(batchUserSetSize)
                .distributionEndFlag(Boolean.FALSE)
                .build();
        eventPublisher.publish(couponTemplateDistributionEvent.topic(), couponTemplateDistributionEvent.buildEventMessage(couponTemplateDistributionMessage));

        // 同步当前执行进度到缓存
        stringRedisTemplate.opsForValue().set(templateTaskExecuteProgressKey, String.valueOf(rowCount));
        ++rowCount;

    }

    /**
     * 整个 Excel 文件读取结束后调用 只会调用一次
     * @param analysisContext   分析上下文
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        // 发送 Excel 解析完成标识，即使不满足批量保存的数量也得保存到数据库
        CouponTemplateDistributionEvent.CouponTemplateDistributionMessage couponTemplateDistributionMessage = CouponTemplateDistributionEvent.CouponTemplateDistributionMessage.builder()
                .distributionEndFlag(Boolean.TRUE) // 设置解析完成标识
                .shopNumber(couponTaskDO.getShopNumber())
                .couponTemplateId(couponTemplateDO.getId())
                .couponTemplateConsumeRule(couponTemplateDO.getConsumeRule())
                .couponTaskBatchId(couponTaskDO.getBatchId())
                .couponTaskId(couponTaskDO.getId())
                .build();
        eventPublisher.publish(couponTemplateDistributionEvent.topic(), couponTemplateDistributionEvent.buildEventMessage(couponTemplateDistributionMessage));
    }
}
