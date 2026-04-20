package cn.yy.myrent.vo;

import lombok.Data;

@Data
public class PaymentRefundOrderStatusVO {

    private String orderNo;

    private String refundNo;

    private Integer status;

    private Integer sourceType;

    private String reasonCode;
}
