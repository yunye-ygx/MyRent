package cn.yy.myrent.service;

import cn.yy.myrent.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
public interface IUserService extends IService<User> {

    User registerByPhone(String phone, String password, String name);

    User loginByPhone(String phone, String password);

}
