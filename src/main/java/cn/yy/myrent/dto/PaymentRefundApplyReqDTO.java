package cn.yy.myrent.dto;

import lombok.Data;

@Data
public class PaymentRefundApplyReqDTO {

    private String orderNo;

    private String reasonDetail;
}
