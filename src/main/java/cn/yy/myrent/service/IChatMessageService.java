package cn.yy.myrent.service;

import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.vo.ChatPullVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IChatMessageService extends IService<ChatMessage> {

    ChatPullVO pullNewMessages(Long userId, Long lastMessageId, String sessionId, Integer limit);

    ChatPullVO pullHistoryMessages(Long userId, String sessionId, Long beforeMessageId, Integer limit);

    int markMessagesRead(Long userId, String sessionId, Long upToMessageId);

    void fillUserNames(List<ChatMessage> messages);
}
