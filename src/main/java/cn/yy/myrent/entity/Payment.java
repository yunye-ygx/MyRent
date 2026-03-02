package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

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

    /**
     * 关联订单号
     */
    private String orderNo;

    /**
     * 支付宝/微信流水号 - 【核心：唯一索引防重】
     */
    private String thirdPartyTradeNo;

    /**
     * 实际支付金额(分) - 【新增：防金额篡改漏洞】
     */
    private Integer payAmount;

    /**
     * 状态: 0-处理中, 1-成功
     */
    private Integer status;

    private LocalDateTime createTime;


}
