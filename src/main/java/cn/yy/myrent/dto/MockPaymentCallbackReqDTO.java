package cn.yy.myrent.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MockPaymentCallbackReqDTO {
    private String orderNo;
    private String paymentNo;
    private String thirdPartyTradeNo;
    private String callbackNo;
    private String payStatus;
    private Integer payAmount;
    private LocalDateTime callbackTime;
}
