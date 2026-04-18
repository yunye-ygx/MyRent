package cn.yy.myrent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateOrderVO {
    private String orderNo;
    private String paymentNo;
    private String mockPayUrl;
    private LocalDateTime expireTime;
}
