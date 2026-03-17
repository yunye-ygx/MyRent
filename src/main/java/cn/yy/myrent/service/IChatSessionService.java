package cn.yy.myrent.service;

import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 聊天会话列表 服务类
 * </p>
 *
 * @author yy
 * @since 2026-03-12
 */
public interface IChatSessionService extends IService<ChatSession> {

    ChatMessage sendMessage(MessageDTO messageDTO);

}
