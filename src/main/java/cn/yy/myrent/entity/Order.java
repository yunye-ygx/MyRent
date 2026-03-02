package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 定金订单表
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("order")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分布式订单ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 业务订单号(全局唯一)
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 房源ID
     */
    private Long houseId;

    /**
     * 订单金额(应付定金)
     */
    private Integer amount;

    /**
     * 状态: 0-待支付, 1-已支付锁房, 2-超时取消
     */
    private Integer status;

    /**
     * 过期时间 - 【核心：MQ延迟队列比对基准】
     */
    private LocalDateTime expireTime;

    private LocalDateTime createTime;


}
