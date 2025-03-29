# 项目亮点

## (1) 创建优惠券模板   
- 通过**责任链模式**构建多级参数校验，实现对创建优惠券模板请求的多维度验证，  
同时基于**AOP + Redis分布式锁实现自定义注解**防止商家重复创建，有效保障优惠券创建的业务稳定性。  
## (2) 优惠券推送任务
- 在创建优惠券分发任务场景中，基于EasyExcel异步解析百万级用户数据，通过**动态线程池+Redisson延迟队列**实现统计补偿双机制，
再结合**RabbitMQ解耦分发流程**，将百万优惠券任务创建响应时间从**5s压缩至500ms**。  
## （3） 用户优惠券分发

## (4) 用户领取优惠券
Lua脚本实现原子化领取操作，保障库存扣减与用户领取记录的强一致性，解决超卖与重复领取问题  
采用RabbitMQ异步削峰填谷，将领取请求与DB操作解耦，接口响应时间压缩至50ms内，支撑3000+/QPS并发领取  
设计双重缓存保障机制：通过ZSet记录用户优惠券列表，配合写后查询重试策略应对Redis极端数据丢失场景  
实现精细化异常处理：定义标准错误码体系（库存不足/活动过期/领取超限），结合事务模板保障最终数据一致性  
通过MySQL行级锁（X锁）与乐观锁（`WHERE stock >= N`）协同机制，保障大促期间百万级并发请求下零超卖  
1.领取规则  
```
JSONObject receiveRule = new JSONObject();
receiveRule.put("limitPerPerson", 1); // 每人限领
receiveRule.put("usageInstructions", "xxx"); // 使用说明
```
2.使用规则  
```
JSONObject consumeRule = new JSONObject();
consumeRule.put("termsOfUse", new BigDecimal("10")); // 使用条件 满 x 元可用
consumeRule.put("maximumDiscountAmount", new BigDecimal("3")); // 最大优惠金额
consumeRule.put("explanationOfUnmetC 3onditions", "xxx"); // 不满足使用条件说明
consumeRule.put("validityPeriod", 48); // 自领取优惠券后有效时间，单位小时
```

面试题：  
线程池和消息队列MQ在异步处理上有什么区别？在什么场景下使用？  
在优惠券管理平台项目中，为何使用线程池和Redis异步队列来实现Excel解析？  
Redis延时队列中存储的是什么？20秒的定时是如何确定的？  
如果Excel解析在20秒内完成，是否会造成时间浪费？有没有更实时的方式？  
在做优惠券模板分表时，有没有考虑数据倾斜的问题？（有的商户创建的优惠券模板少，有的商户创建的优惠券模板多）  
如果我们现在要设计一个后台，用于商家端优惠券管理，应该怎么设计以避免查全表的操作？  