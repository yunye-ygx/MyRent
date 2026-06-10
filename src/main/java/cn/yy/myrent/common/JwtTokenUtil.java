package cn.yy.myrent.common;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenUtil {

    public String generateToken(Long userId, String phone) {
        return generateToken(userId, phone, 0);
    }

    public String generateToken(Long userId, String phone, Integer role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        StpUtil.login(userId);
        String token = StpUtil.getTokenValue();
        StpUtil.getSession().set("role", role == null ? 0 : role);
        return token;
    }

    public Long parseUserId(String token) {
        Map<String, Object> claims = parseAndVerify(token);
        Object userId = claims.get("userId");
        return Long.valueOf(String.valueOf(userId));
    }

    public Map<String, Object> parseAndVerify(String token) {
        if (!StringUtils.hasText(token)) {
            throw new RuntimeException("token cannot be empty");
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            throw new RuntimeException("token invalid");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", Long.valueOf(String.valueOf(loginId)));
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        Object role = session == null ? null : session.get("role");
        claims.put("role", role == null ? 0 : role);
        return claims;
    }
}
