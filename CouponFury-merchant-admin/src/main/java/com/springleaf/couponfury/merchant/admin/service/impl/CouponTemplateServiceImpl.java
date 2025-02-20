package com.springleaf.couponfury.merchant.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import com.springleaf.couponfury.framework.exception.ClientException;
import com.springleaf.couponfury.framework.exception.ServiceException;
import com.springleaf.couponfury.merchant.admin.common.constant.MerchantAdminRedisConstant;
import com.springleaf.couponfury.merchant.admin.common.context.UserContext;
import com.springleaf.couponfury.merchant.admin.common.enums.CouponTemplateStatusEnum;
import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTemplateDO;
import com.springleaf.couponfury.merchant.admin.dao.mapper.CouponTemplateMapper;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplateNumberReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplatePageQueryReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplateSaveReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.PageParamReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.resp.CouponTemplatePageQueryRespDTO;
import com.springleaf.couponfury.merchant.admin.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.merchant.admin.service.CouponTemplateService;
import com.springleaf.couponfury.merchant.admin.service.basics.chain.MerchantAdminChainContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.springleaf.couponfury.merchant.admin.common.enums.ChainBizMarkEnum.MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY;

@Service
@Slf4j
public class CouponTemplateServiceImpl implements CouponTemplateService {
    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private MerchantAdminChainContext<CouponTemplateSaveReqDTO> merchantAdminChainContext;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private RBloomFilter<String> couponTemplateQueryBloomFilter;

    @LogRecord(
            success = """
                    创建优惠券：{{#requestParam.name}}， \
                    优惠对象：{COMMON_ENUM_PARSE{'DiscountTargetEnum' + '_' + #requestParam.target}}， \
                    优惠类型：{COMMON_ENUM_PARSE{'DiscountTypeEnum' + '_' + #requestParam.type}}， \
                    库存数量：{{#requestParam.stock}}， \
                    优惠商品编码：{{#requestParam.goods}}， \
                    有效期开始时间：{{#requestParam.validStartTime}}， \
                    有效期结束时间：{{#requestParam.validEndTime}}， \
                    领取规则：{{#requestParam.receiveRule}}， \
                    消耗规则：{{#requestParam.consumeRule}};
                    """,
            type = "CouponTemplate",
            bizNo = "{{#bizNo}}",
            extra = "{{#requestParam.toString()}}"
    )
    @Override
    public void createCouponTemplate(CouponTemplateSaveReqDTO requestParam) {
        // 通过责任链验证请求参数是否正确
        merchantAdminChainContext.handler(MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY.name(), requestParam);

        // 新增优惠券模板信息到数据库
        CouponTemplateDO couponTemplateDO = BeanUtil.toBean(requestParam, CouponTemplateDO.class);
        couponTemplateDO.setStatus(CouponTemplateStatusEnum.ACTIVE.getStatus());
        couponTemplateDO.setShopNumber(UserContext.getShopNumber());
        couponTemplateMapper.saveCouponTemplate(couponTemplateDO);

        // 因为模板 ID 是运行中生成的，@LogRecord 默认拿不到，所以我们需要手动设置
        LogRecordContext.putVariable("bizNo", couponTemplateDO.getId());

        // 缓存预热：通过将数据库的记录序列化成 JSON 字符串放入 Redis 缓存
        CouponTemplateQueryRespDTO actualRespDTO = BeanUtil.toBean(couponTemplateDO, CouponTemplateQueryRespDTO.class);
        // 将actualRespDTO对象转换为一个Map，其中键是字段名，值是字段对应的值
        // false表示不忽略null值的字段。
        // true表示忽略字段上的transient标记
        Map<String, Object> cacheTargetMap = BeanUtil.beanToMap(actualRespDTO, false, true);
        // 将Map中的值转换为字符串
        Map<String, String> actualCacheTargetMap = cacheTargetMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() != null ? entry.getValue().toString() : ""
                ));
        String couponTemplateCacheKey = String.format(MerchantAdminRedisConstant.COUPON_TEMPLATE_KEY, couponTemplateDO.getId());

        // 通过 LUA 脚本执行设置 Hash 数据以及设置过期时间
        String luaScript = "redis.call('HMSET', KEYS[1], unpack(ARGV, 1, #ARGV - 1)) " +
                "redis.call('EXPIREAT', KEYS[1], ARGV[#ARGV])";

        List<String> keys = Collections.singletonList(couponTemplateCacheKey);
        List<String> args = new ArrayList<>(actualCacheTargetMap.size() * 2 + 1);
        actualCacheTargetMap.forEach((key, value) -> {
            args.add(key);
            args.add(value);
        });

        // 优惠券活动过期时间转换为秒级别的 Unix 时间戳
        args.add(String.valueOf(couponTemplateDO.getValidEndTime().getTime() / 1000));

        // 执行 LUA 脚本
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(luaScript, Long.class),
                keys,
                args.toArray()
        );

        // 添加优惠券模板 ID 到布隆过滤器
        couponTemplateQueryBloomFilter.add(String.valueOf(couponTemplateDO.getId()));
    }

    @Override
    public PageInfo<CouponTemplatePageQueryRespDTO> pageQueryCouponTemplate(CouponTemplatePageQueryReqDTO requestParam, PageParamReqDTO pageParam) {
        // 开启分页查询
        PageHelper.startPage(pageParam.getPageNum(), pageParam.getPageSize());

        // 查询数据库
        CouponTemplateDO couponTemplateDO = BeanUtil.toBean(requestParam, CouponTemplateDO.class);
        couponTemplateDO.setShopNumber(UserContext.getShopNumber());
        List<CouponTemplateDO> couponTemplateDOList = couponTemplateMapper.listCouponTemplate(couponTemplateDO);

        // 转换为响应对象
        List<CouponTemplatePageQueryRespDTO> couponTemplatePageQueryRespDTOList = couponTemplateDOList.stream()
                .map(couponTemplateDOTemp -> BeanUtil.toBean(couponTemplateDO, CouponTemplatePageQueryRespDTO.class))
                .collect(Collectors.toList());

        // 封装分页信息
        return new PageInfo<>(couponTemplatePageQueryRespDTOList);
    }

    @Override
    public CouponTemplateQueryRespDTO findCouponTemplateById(Long couponTemplateId) {
        CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(UserContext.getShopNumber(), couponTemplateId);
        return BeanUtil.toBean(couponTemplateDO, CouponTemplateQueryRespDTO.class);
    }

    @LogRecord(
            success = "结束优惠券",
            type = "CouponTemplate",
            bizNo = "{{#couponTemplateId}}"
    )
    @Override
    public void terminateCouponTemplate(Long couponTemplateId) {
        // 验证是否存在数据横向越权
        CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(UserContext.getShopNumber(), couponTemplateId);
        if (couponTemplateDO == null) {
            // 一旦查询优惠券不存在，基本可判定横向越权，可上报该异常行为，次数多了后执行封号等处理
            throw new ClientException("优惠券模板异常，请检查操作是否正确...");
        }

        // 验证优惠券模板是否正常
        if (ObjectUtil.notEqual(couponTemplateDO.getStatus(), CouponTemplateStatusEnum.ACTIVE.getStatus())) {
            throw new ClientException("优惠券模板已结束");
        }

        // 记录优惠券模板修改前数据
        LogRecordContext.putVariable("originalData", JSON.toJSONString(couponTemplateDO));

        // 修改优惠券模板状态为结束
        couponTemplateMapper.updateCouponTemplateStatus(couponTemplateId, UserContext.getShopNumber(), CouponTemplateStatusEnum.ENDED.getStatus());

        // 修改优惠券模板Redis缓存状态为结束状态
        String couponTemplateCacheKey = String.format(MerchantAdminRedisConstant.COUPON_TEMPLATE_KEY, couponTemplateId);
        stringRedisTemplate.opsForHash().put(couponTemplateCacheKey, "status", String.valueOf(CouponTemplateStatusEnum.ENDED.getStatus()));
    }

    @LogRecord(
            success = "增加发行量：{{#requestParam.number}}",
            type = "CouponTemplate",
            bizNo = "{{#requestParam.couponTemplateId}}"
    )
    @Override
    public void increaseNumberCouponTemplate(CouponTemplateNumberReqDTO requestParam) {
        // 验证是否存在数据横向越权
        CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(UserContext.getShopNumber(), requestParam.getCouponTemplateId());
        if (couponTemplateDO == null) {
            // 一旦查询优惠券不存在，基本可判定横向越权，可上报该异常行为，次数多了后执行封号等处理
            throw new ClientException("优惠券模板异常，请检查操作是否正确...");
        }

        // 验证优惠券模板是否正常
        if (ObjectUtil.notEqual(couponTemplateDO.getStatus(), CouponTemplateStatusEnum.ACTIVE.getStatus())) {
            throw new ClientException("优惠券模板已结束");
        }

        // 记录优惠券模板修改前数据
        LogRecordContext.putVariable("originalData", JSON.toJSONString(couponTemplateDO));

        // 设置数据库优惠券模板增加库存发行量
        int increased = couponTemplateMapper.increaseNumberCouponTemplate(UserContext.getShopNumber(), requestParam.getCouponTemplateId(), requestParam.getNumber());
        if (increased < 1) {
            throw new ServiceException("优惠券模板库存发行量增加失败");
        }

        // 增加优惠券模板缓存库存发行量
        String couponTemplateCacheKey = String.format(MerchantAdminRedisConstant.COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId());
        stringRedisTemplate.opsForHash().increment(couponTemplateCacheKey, "stock", requestParam.getNumber());
    }
}
