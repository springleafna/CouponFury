# 项目亮点

## (1) 创建优惠券模板   
- 通过**责任链模式**构建多级参数校验，实现对创建优惠券模板请求的多维度验证，  
同时基于**AOP + Redis分布式锁实现自定义注解**防止商家重复创建，有效保障优惠券创建的业务稳定性。  
## (2) 优惠券推送任务
- 

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