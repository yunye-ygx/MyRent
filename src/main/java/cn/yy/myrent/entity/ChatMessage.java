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
 * 聊天消息明细记录
 * </p>
 *
 * @author yy
 * @since 2026-03-12
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息主键 (强烈建议代码中使用雪花算法生成全局唯一ID，不要用自增)
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 归属的会话ID (对应 chat_session 表)
     */
    private String sessionId;

    /**
     * 发送者用户ID
     */
    private Long senderId;

    /**
     * 接收者用户ID
     */
    private Long receiverId;

    /**
     * 消息类型: 1-纯文本, 2-图片URL, 3-房源卡片JSON, 4-系统通知
     */
    private Integer msgType;

    /**
     * 消息具体内容
     */
    private String content;

    /**
     * 消息状态: 0-未读, 1-已读, 2-已撤回
     */
    private Integer status;

    /**
     * 消息发送时间 (聊天气泡按此字段正序排列)
     */
    private LocalDateTime createTime;


}
