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
 * 通用本地消息表/延迟任务表
 * </p>
 *
 * @author yy
 * @since 2026-03-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("local_task")
public class LocalTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 自增主键，表内唯一标识
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 消息唯一ID，用于幂等控制和全链路追踪
     */
    private String messageId;

    /**
     * 业务类型，如 ORDER、HOUSE
     */
    private String bizType;

    /**
     * 业务主键，如订单ID、房源ID
     */
    private String bizId;

    /**
     * 事件类型，如 ORDER_TIMEOUT_RELEASE、HOUSE_SEARCH_UPSERT
     */
    private String eventType;

    /**
     * 消息体，建议只放必要字段，不要塞过大对象
     */
    private String payload;

    /**
     * 状态：0待执行 1执行中 2成功 3待重试 4失败 5取消/死信
     */
    private Integer status;

    /**
     * 最早执行时间；立即消息填当前时间，延迟消息填未来
  时间
     */
    private LocalDateTime executeTime;

    /**
     * 当前已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数，超过后进入失败或死信状态
     */
    private Integer maxRetryCount;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 版本号，用于顺序控制或乐观锁
     */
    private Long version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
