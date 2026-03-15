package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 聊天会话列表
 * </p>
 *
 * @author yy
 * @since 2026-03-12
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_session")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话业务ID (建议生成规则：较小的userId_较大的userId)
     */
    private String sessionId;

    /**
     * 参与者A的ID (强制规定：存较小的ID)
     */
    @TableField("user_id_1")
    private Long userId1;

    /**
     * 参与者B的ID (强制规定：存较大的ID)
     */
    @TableField("user_id_2")
    private Long userId2;

    /**
     * 关联房源ID (租房业务特色：标记他们是在聊哪套房)
     */
    private Long houseId;

    /**
     * 最后一条消息摘要 (提升列表加载性能)
     */
    private String lastMsgContent;

    /**
     * 首次会话时间
     */
    private LocalDateTime createTime;

    /**
     * 最后活跃时间 (列表根据此字段倒序排列!)
     */
    private LocalDateTime updateTime;


}
