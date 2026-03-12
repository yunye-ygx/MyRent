package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.service.IChatMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 聊天消息明细记录 服务实现类
 * </p>
 *
 * @author yy
 * @since 2026-03-12
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

}

