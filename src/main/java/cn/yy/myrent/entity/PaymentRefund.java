package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("payment_refund")
public class PaymentRefund implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private String requestNo;

    private String orderNo;

    private String paymentNo;

    private Long userId;

    private String channel;

    private Integer refundAmount;

    private Integer sourceType;

    private String reasonCode;

    private String reasonDetail;

    private Integer status;

    private String thirdPartyTradeNo;

    private String thirdPartyRefundNo;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime nextRetryTime;

    private String failReason;

    private LocalDateTime applyTime;

    private LocalDateTime successTime;

    private LocalDateTime closeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
