package cn.yy.myrent.service;

import cn.yy.myrent.dto.MessageDTO;
import cn.yy.myrent.entity.ChatMessage;
import cn.yy.myrent.entity.ChatSession;
import cn.yy.myrent.vo.ChatSessionSummaryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IChatSessionService extends IService<ChatSession> {

    ChatMessage sendMessage(MessageDTO messageDTO);

    Page<ChatSessionSummaryVO> querySessionSummaries(Long userId, Long current, Long size);
}
