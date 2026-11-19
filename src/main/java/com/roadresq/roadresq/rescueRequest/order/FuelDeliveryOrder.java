package com.roadresq.roadresq.rescueRequest.order;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

public record FuelDeliveryOrder(String capacityId, @Field(targetType = FieldType.DECIMAL128) BigDecimal litres) {}