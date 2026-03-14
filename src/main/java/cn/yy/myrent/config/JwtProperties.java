package cn.yy.myrent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "myrent.jwt")
public class JwtProperties {

    private String secret = "MyRentJwtSecretChangeMe";

    private long expireSeconds = 86400;
}
