package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}
