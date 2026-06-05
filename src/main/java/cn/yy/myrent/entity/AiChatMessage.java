package cn.yy.myrent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessage {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String toolName;
    private String toolCallId;
    private String toolParams;
    private String toolResult;
    private LocalDateTime createTime;
}
