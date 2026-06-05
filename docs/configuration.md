# 配置说明

配置文件位于：

```text
src/main/resources/application.yml
```

## 需要修改的配置项

当前仓库中的配置包含个人本地环境地址，启动前需要按自己的环境修改：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.rabbitmq.host`
- `spring.rabbitmq.username`
- `spring.rabbitmq.password`
- `spring.data.redis.host`
- `spring.data.redis.password`
- `spring.elasticsearch.uris`
- `spring.ai.openai.api-key`

AI 推荐默认读取环境变量 `BAILIAN_API_KEY`，建议使用环境变量，不要把真实 key 写死在配置文件里。

## 本地开发参考配置

```yaml
server:
  port: 8084

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rent?useSSL=false&serverTimezone=UTC
    username: root
    password: your-password

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true

  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0

  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 5s
    socket-timeout: 30s

myrent:
  jwt:
    secret: MyRentJwtSecretChangeMe
    expire-seconds: 86400
```
