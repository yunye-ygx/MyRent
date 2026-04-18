package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 支付流水表
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("payment")
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private String orderNo;

    private Long userId;

    private Integer payAmount;

    private String channel;

    private String thirdPartyTradeNo;

    private String callbackNo;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime paidTime;

    private LocalDateTime callbackTime;

    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
