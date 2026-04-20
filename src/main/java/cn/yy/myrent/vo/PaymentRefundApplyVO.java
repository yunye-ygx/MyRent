package cn.yy.myrent.vo;

import lombok.Data;

@Data
public class PaymentRefundApplyVO {

    private String refundNo;

    private String orderNo;

    private String paymentNo;

    private Integer status;

    private Integer sourceType;

    private String reasonCode;
}
