package cn.yy.myrent.dto;

import lombok.Data;

@Data
public class PaymentRefundApplyCommand {

    private String orderNo;

    private String paymentNo;

    private Integer sourceType;

    private String reasonCode;

    private String reasonDetail;

    private String requestNo;

    private Long userId;
}
