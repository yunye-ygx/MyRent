package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.dto.UserPhoneReqDTO;
import cn.yy.myrent.entity.User;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.vo.LoginVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "手机号+密码注册")
    public Result<User> register(@RequestBody UserPhoneReqDTO reqDTO) {
        if (reqDTO == null) {
            return Result.error("参数不能为空");
        }
        try {
            User user = userService.registerByPhone(reqDTO.getPhone(), reqDTO.getPassword(), reqDTO.getName());
            return Result.success("注册成功", user);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "手机号+密码登录")
    public Result<LoginVO> login(@RequestBody UserPhoneReqDTO reqDTO) {
        if (reqDTO == null) {
            return Result.error("参数不能为空");
        }
        try {
            User user = userService.loginByPhone(reqDTO.getPhone(), reqDTO.getPassword());
            String token = jwtTokenUtil.generateToken(user.getId(), user.getPhone());
            LoginVO loginVO = new LoginVO();
            loginVO.setToken(token);
            loginVO.setUserId(user.getId());
            loginVO.setPhone(user.getPhone());
            loginVO.setName(user.getName());
            return Result.success("登录成功", loginVO);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询用户")
    public Result<User> getById(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询用户")
    public Result<Page<User>> page(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        Page<User> page = userService.lambdaQuery()
                .orderByDesc(User::getId)
                .page(new Page<>(safeCurrent, safeSize));
        page.getRecords().forEach(item -> item.setPassword(null));
        return Result.success(page);
    }

    @PostMapping
    @Operation(summary = "新增用户")
    public Result<Long> create(@RequestBody User user) {
        if (user == null
                || !StringUtils.hasText(user.getPhone())
                || !StringUtils.hasText(user.getPassword())
                || !StringUtils.hasText(user.getName())) {
            return Result.error("手机号、密码和昵称不能为空");
        }
        try {
            User created = userService.registerByPhone(user.getPhone(), user.getPassword(), user.getName());
            return Result.success("新增用户成功", created.getId());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody User user) {
        if (user == null) {
            return Result.error("参数不能为空");
        }
        if (StringUtils.hasText(user.getPassword())) {
            return Result.error("通用更新接口不支持直接修改密码");
        }
        user.setId(id);
        boolean updated = userService.updateById(user);
        if (!updated) {
            return Result.error("更新用户失败或用户不存在");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = userService.removeById(id);
        if (!removed) {
            return Result.error("删除用户失败或用户不存在");
        }
        return Result.success();
    }
}
