package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.User;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 32;
    private static final int MAX_NAME_LENGTH = 20;
    private static final String PASSWORD_VERSION = "v1$";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public User registerByPhone(String phone, String password, String name) {
        validatePhone(phone);
        validatePassword(password);
        validateName(name);

        User existed = this.lambdaQuery().eq(User::getPhone, phone).one();
        if (existed != null) {
            log.warn("注册失败：手机号已存在，phone={}", maskPhone(phone));
            throw new RuntimeException("手机号已注册");
        }

        User user = new User()
                .setPhone(phone)
                .setName(name)
                .setPassword(encodePassword(password))
                .setCreateTime(LocalDateTime.now());
        boolean saved = this.save(user);
        if (!saved) {
            log.error("注册失败：用户保存失败，phone={}", maskPhone(phone));
            throw new RuntimeException("注册失败");
        }
        log.info("注册成功，userId={}, phone={}", user.getId(), maskPhone(phone));
        return toSafeUser(user);
    }

    @Override
    public User loginByPhone(String phone, String password) {
        validatePhone(phone);
        validatePassword(password);

        User user = this.lambdaQuery().eq(User::getPhone, phone).one();
        if (user == null || !matchesPassword(password, user.getPassword())) {
            log.warn("登录失败：账号或密码错误，phone={}", maskPhone(phone));
            throw new RuntimeException("手机号或密码错误");
        }
        log.info("登录成功，userId={}, phone={}", user.getId(), maskPhone(phone));
        return toSafeUser(user);
    }

    private void validatePhone(String phone) {
        if (!StringUtils.hasText(phone) || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new RuntimeException("手机号格式不正确");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw new RuntimeException("密码长度需在6到32位之间");
        }
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name) || name.length() > MAX_NAME_LENGTH) {
            throw new RuntimeException("昵称不能为空且长度不能超过20");
        }
    }

    private User toSafeUser(User source) {
        return new User()
                .setId(source.getId())
                .setPhone(source.getPhone())
                .setName(source.getName())
                .setCreateTime(source.getCreateTime());
    }

    private String encodePassword(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt);
        byte[] combined = new byte[SALT_BYTES + HASH_BYTES];
        System.arraycopy(salt, 0, combined, 0, SALT_BYTES);
        System.arraycopy(hash, 0, combined, SALT_BYTES, HASH_BYTES);
        return PASSWORD_VERSION + Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    }

    private boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (!StringUtils.hasText(encodedPassword)) {
            return false;
        }

        if (!encodedPassword.startsWith(PASSWORD_VERSION)) {
            return MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    encodedPassword.getBytes(StandardCharsets.UTF_8));
        }

        String body = encodedPassword.substring(PASSWORD_VERSION.length());
        byte[] combined;
        try {
            combined = Base64.getUrlDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (combined.length != SALT_BYTES + HASH_BYTES) {
            return false;
        }

        byte[] salt = new byte[SALT_BYTES];
        byte[] expectedHash = new byte[HASH_BYTES];
        System.arraycopy(combined, 0, salt, 0, SALT_BYTES);
        System.arraycopy(combined, SALT_BYTES, expectedHash, 0, HASH_BYTES);

        byte[] actualHash = pbkdf2(rawPassword.toCharArray(), salt);
        return MessageDigest.isEqual(actualHash, expectedHash);
    }

    private byte[] pbkdf2(char[] passwordChars, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, HASH_BYTES * 8);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            log.error("密码处理失败，algorithm={}", PBKDF2_ALGORITHM, e);
            throw new RuntimeException("密码处理失败");
        } finally {
            spec.clearPassword();
        }
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
