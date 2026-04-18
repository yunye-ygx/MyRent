package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MockCheckoutVO {
    private String orderNo;
    private String paymentNo;
    private Integer amount;
    private LocalDateTime expireTime;
    private long remainingSeconds;
}
