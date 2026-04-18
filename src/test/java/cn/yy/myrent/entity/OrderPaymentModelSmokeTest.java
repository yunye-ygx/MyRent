package cn.yy.myrent.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderPaymentModelSmokeTest {

    @Test
    void orderShouldExposeClosedLoopFields() {
        Set<String> fieldNames = Arrays.stream(Order.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fieldNames.contains("paidTime"));
        assertTrue(fieldNames.contains("closeTime"));
        assertTrue(fieldNames.contains("updateTime"));
    }

    @Test
    void paymentShouldExposeMockGatewayFields() {
        Set<String> fieldNames = Arrays.stream(Payment.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fieldNames.contains("paymentNo"));
        assertTrue(fieldNames.contains("userId"));
        assertTrue(fieldNames.contains("channel"));
        assertTrue(fieldNames.contains("callbackNo"));
        assertTrue(fieldNames.contains("expireTime"));
        assertTrue(fieldNames.contains("paidTime"));
        assertTrue(fieldNames.contains("callbackTime"));
        assertTrue(fieldNames.contains("failReason"));
        assertTrue(fieldNames.contains("updateTime"));
    }
}
