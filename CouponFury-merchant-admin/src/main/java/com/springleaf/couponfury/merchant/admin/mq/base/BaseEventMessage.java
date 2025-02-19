package com.springleaf.couponfury.merchant.admin.mq.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BaseEventMessage<T> {
    private String id;
    private Date timestamp;
    private T data;
}
