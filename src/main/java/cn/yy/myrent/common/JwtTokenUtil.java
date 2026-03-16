package cn.yy.myrent.common;

import cn.yy.myrent.config.JwtProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenUtil {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JwtProperties jwtProperties;

    public String generateToken(Long userId, String phone) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("phone", phone);
        payload.put("iat", now);
        payload.put("exp", now + jwtProperties.getExpireSeconds());

        try {
            String headerPart = URL_ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payloadPart = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerPart + "." + payloadPart;
            String signaturePart = URL_ENCODER.encodeToString(hmacSha256(signingInput));
            return signingInput + "." + signaturePart;
        } catch (Exception e) {
            throw new RuntimeException("生成token失败", e);
        }
    }

    public Long parseUserId(String token) {
        Map<String, Object> claims = parseAndVerify(token);
        Long userId = toLong(claims.get("userId"));
        if (userId == null) {
            throw new RuntimeException("token缺少userId");
        }
        return userId;
    }

    public Map<String, Object> parseAndVerify(String token) {
        if (!StringUtils.hasText(token)) {
            throw new RuntimeException("token不能为空");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new RuntimeException("token格式错误");
        }

        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSignature = hmacSha256(signingInput);
            byte[] actualSignature = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new RuntimeException("token签名无效");
            }

            byte[] payloadBytes = URL_DECODER.decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {
            });

            Long exp = toLong(claims.get("exp"));
            long now = Instant.now().getEpochSecond();
            if (exp == null || exp <= now) {
                throw new RuntimeException("token已过期");
            }
            return claims;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("token解析失败", e);
        }
    }

    private byte[] hmacSha256(String content) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        byte[] secretBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        SecretKeySpec keySpec = new SecretKeySpec(secretBytes, HMAC_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
