package cn.yy.myrent.vo;

import cn.yy.myrent.entity.ChatMessage;
import lombok.Data;

import java.util.List;

@Data
public class ChatPullVO {

    private List<ChatMessage> messages;

    private Long nextCursor;

    private Boolean hasMore;
}
