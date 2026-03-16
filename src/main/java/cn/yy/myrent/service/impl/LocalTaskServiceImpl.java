package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.LocalTask;
import cn.yy.myrent.mapper.LocalTaskMapper;
import cn.yy.myrent.service.ILocalTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 通用本地消息表/延迟任务表 服务实现类
 * </p>
 *
 * @author yy
 * @since 2026-03-15
 */
@Service
public class LocalTaskServiceImpl extends ServiceImpl<LocalTaskMapper, LocalTask> implements ILocalTaskService {

}
