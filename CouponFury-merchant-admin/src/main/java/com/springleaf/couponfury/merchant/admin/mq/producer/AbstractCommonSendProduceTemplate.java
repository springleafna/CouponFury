package com.springleaf.couponfury.merchant.admin.mq.producer;

import com.springleaf.couponfury.merchant.admin.mq.base.BaseEventMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ 抽象公共发送消息组件
 * TODO: 计划基于模板方法模式重构消息队列发送功能
 */
@Slf4j(topic = "CommonSendProduceTemplate")
public abstract class AbstractCommonSendProduceTemplate<T> {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 构建消息发送事件基础扩充属性实体
     */
    protected abstract T buildMessageSendEvent(BaseEventMessage<T> message);


    /**
     * 消息事件通用发送
     */
    public void sendCommonMessage(String exchange, String routingKey, T message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.info("sendCommonMessage success, exchange:{}, routingKey:{}, message:{}", exchange, routingKey, message);
    }

}
