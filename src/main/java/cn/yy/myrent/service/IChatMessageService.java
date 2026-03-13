package cn.yy.myrent.service;

import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.vo.ChatPullVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IChatMessageService extends IService<ChatMessage> {

    ChatPullVO pullNewMessages(Long userId, Long lastMessageId, String sessionId, Integer limit);

    int markMessagesRead(Long userId, String sessionId, Long upToMessageId);
}
