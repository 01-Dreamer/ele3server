# 项目协作规则

## 网关路径规则

所有对外 HTTP 接口统一使用下面的网关路径格式：

- 公开接口：`/api/{service}/public/**`
- 普通用户接口：`/api/{service}/**`
- 管理员接口：`/api/{service}/admin/**`
- 内部服务接口：`/internal/{service}/**`

网关只转发 `/api/{service}/**` 路径。

网关不转发 `/internal/{service}/**`。内部接口只用于服务之间调用，例如 OpenFeign。

## 网关鉴权规则

网关只对 `/api/**` 请求执行鉴权过滤。

- `/api/{service}/public/**`：不需要登录，直接放行。
- `/api/{service}/admin/**`：需要登录，并且 `role = ADMIN`。
- 其它 `/api/{service}/**`：需要登录，但不需要管理员角色。
- 非 `/api/**` 请求：不进入网关鉴权过滤器。

登录 token 使用请求头传递：

```http
Authorization: Bearer <token>
```

网关从 Redis 校验 token：

- `auth:login:{token}`：登录 token 信息，包含 `userId`
- `auth:user:{userId}`：用户信息，包含 `role` 和 `status`

账号状态规则：

- `status = 0`：正常放行
- `status = 1`：账号封禁，拒绝请求
- 其它状态或状态缺失：账号状态异常，拒绝请求

鉴权通过后，网关注入：

```http
X-User-Id: {userId}
```

业务服务只应该信任来自网关或内部服务调用中的 `X-User-Id`。

## 内部访问令牌

网关转发请求时会追加：

```http
X-Internal-Token: ${internal.token}
```

内部服务可以用这个请求头判断请求是否来自网关或可信的服务间调用。

## Controller 路径约定

网关不使用 `StripPrefix`，所以后端 Controller 必须写完整的对外路径前缀。

示例：

- 认证公开登录：`/api/auth/public/login`
- 风控公开验证码：`/api/risk/public/captcha/image`
- 认证普通修改密码：`/api/auth/change-password`
- 店铺管理员接口：`/api/shop/admin/**`
- 风控内部验证码校验：`/internal/risk/captcha/verify`

## Controller 命名规则

每个微服务只使用下面四类 Controller：

- `PublicController`：公共访问接口，不需要登录，对应 `/api/{service}/public/**`
- `ApiController`：登录后才能访问的普通用户接口，对应 `/api/{service}/**`
- `AdminController`：管理员接口，需要 `role = ADMIN`，对应 `/api/{service}/admin/**`
- `InternalController`：内部服务接口，只用于服务间调用，对应 `/internal/{service}/**`

不要再新增其它命名风格的 Controller，例如 `UserController`、`AuthController`、`CaptchaController` 等。具体业务含义通过方法名、接口路径和 `@Operation` 表达。

Knife4j 的 `@Tag` 必须按 Controller 类型统一命名，不要写成具体服务名或业务名：

- `PublicController`：`@Tag(name = "公共接口")`
- `ApiController`：`@Tag(name = "用户接口")`
- `AdminController`：`@Tag(name = "管理员接口")`
- `InternalController`：`@Tag(name = "内部接口")`

具体业务说明放在方法级 `@Operation(summary = "...")`，不要靠 `@Tag` 区分。

## 用户 ID 类型规则

所有 HTTP 接口、OpenFeign 请求 DTO、响应 VO 中的 `userId` 一律使用 `String` 类型接收和返回。

原因：用户 ID 使用雪花 ID，前端 JavaScript `Number` 无法安全表示 64 位整数，使用数字会产生精度误差。

服务内部如果数据库字段是 `BIGINT` / Java `Long`，在 service 层显式校验并转换：

```java
Long userId = Long.valueOf(userIdText);
```

不要在 Controller 或 DTO 中直接暴露 `Long userId`。

## Redis Key 设计规则

Redis Key 统一使用小写冒号分隔格式：

```text
{service}:{domain}:{scene}:{identifier}
```

规则：

- 第一段必须是服务名前缀，例如 `auth`、`risk`、`user`、`shop`、`order`、`payment`。
- 中间段表达业务域和场景，例如 `login`、`email`、`captcha`、`tokens`。
- 最后一段放唯一标识，例如 `token`、`userId`、`email`、`captchaId`。
- 固定字面量使用小写英文和短横线，动态值不要再拼中文。
- 不要使用裸 key，例如 `token:{token}`、`captcha:{id}`；必须带服务前缀。
- 不同微服务不能共用同一前缀，避免 key 冲突。
- 所有临时态 key 必须设置 TTL，例如验证码、登录 token、临时风控状态。
- Redis value 优先存 JSON 字符串或简单标量；集合类关系使用 Redis Set/List/Hash 等原生结构。
- 涉及用户 ID 的 key，前端可能使用字符串，但 Redis key 中直接使用字符串形式即可。
- 写代码时优先用常量前缀和 `buildXxxKey(...)` 方法生成 key，不要在业务逻辑里到处手写拼接。

当前已有 key 约定：

```text
# auth-service 登录 token -> LoginTokenVO，TTL = auth.login.ttl
# 示例 value 包含 userId、loginIp、loginTime
auth:login:{token}

# auth-service 用户登录信息 -> LoginUserVO，TTL = auth.login.ttl
auth:user:{userId}

# auth-service 用户当前 token 集合 -> Set(token)，TTL = auth.login.ttl
auth:login:tokens:{userId}

# auth-service 邮箱验证码 -> code，TTL = auth.email-captcha.ttl
# scene 示例：register、forgot-password、change-password
auth:email:captcha:{scene}:{email}

# risk-service 图形验证码 -> code，TTL = captcha.text.ttl
risk:captcha:text:{captchaId}

# tianai-captcha 滑块验证码相关 key 使用 captcha.prefix 控制，当前前缀为 risk:captcha
risk:captcha:...

# shop-service 店铺详情缓存 -> ShopVO，TTL = shop.cache.ttl + 随机抖动，更新/状态变更时刷新，删除时失效；不存在店铺使用短 TTL 空值缓存防穿透
shop:info:{shopId}

# shop-service 店铺商品列表缓存 -> List<ShopItemVO>，TTL = shop.cache.ttl + 随机抖动，商品新增/删除或店铺删除时失效；查询回源使用互斥锁防击穿
shop:item:list:{shopId}

# shop-service 查询回源互斥锁 -> 简单标量，短 TTL，用于防止热点 key 击穿
shop:lock:{scene}:{identifier}

# shop-service ES索引延迟派发去重锁 -> 简单标量，TTL 与 MQ 延迟时间一致，用于合并评价/销量频繁变更
shop:es:index-delay:{shopId}
```

新增 Redis Key 时先检查是否能复用上面的结构；如果需要新增一类长期 key，要在本节补充用途、value 类型和 TTL 策略。

## 配置中心规则

所有微服务必须通过 Nacos 引入公共配置：

```yaml
spring:
  config:
    import:
      - nacos:common-dev.yaml?group=ElE&refreshEnabled=true
  cloud:
    nacos:
      config:
        group: ElE
        file-extension: yaml
```

Nacos 公共配置约定：

- Data ID：`common-dev.yaml`
- Group：`ElE`
- 格式：`YAML`

下面这些配置统一放在配置中心，不要散落在各微服务本地 `application.yml`：

- MySQL：`spring.datasource`
- Redis：`spring.data.redis`
- RabbitMQ：`spring.rabbitmq`
- 邮箱：`spring.mail`
- Elasticsearch：`spring.elasticsearch`
- 阿里云 OSS：`aliyun.oss`
- 支付宝：`payment.alipay`
- 高德地图：`amap`
- Seata：`seata`
- OpenAI / 大模型：`openai`
- 内部访问令牌：`internal.token`

各微服务自己的运行参数必须显式写在本服务 `application.yml` 中，例如 TTL、延迟时间、文件大小限制、默认过期分钟数、缓存锁时间和随机抖动时间；代码中不要通过 `@Value("${xxx:默认值}")` 或固定常量隐藏这些配置。

配置中心 `common-dev.yaml` 格式参考，真实值必须只放在 Nacos 中，文档中一律使用占位符：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://<mysql-host>:<mysql-port>/<database>?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: <mysql-username>
    password: <mysql-password>
  data:
    redis:
      host: <redis-host>
      port: <redis-port>
      password: <redis-password>
      database: <redis-database>
      timeout: 10s
  elasticsearch:
    uris: http://<elasticsearch-host>:<elasticsearch-port>
    username: <elasticsearch-username>
    password: <elasticsearch-password>
    connection-timeout: 10000
    read-timeout: 10000
  rabbitmq:
    host: <rabbitmq-host>
    port: <rabbitmq-port>
    username: <rabbitmq-username>
    password: <rabbitmq-password>
    listener:
      simple:
        default-requeue-rejected: false
  mail:
    host: <mail-host>
    port: <mail-port>
    username: <mail-username>
    password: <mail-password>
    protocol: smtps
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true

payment:
  alipay:
    gateway-url: <alipay-gateway-url>
    format: json
    charset: UTF-8
    sign-type: RSA2
    app-id: <alipay-app-id>
    private-key: <alipay-private-key>
    public-key: <alipay-public-key>
    notify-url: <alipay-notify-url>

internal:
  token: <internal-token>

aliyun:
  oss:
    endpoint: <aliyun-oss-endpoint>
    accessKeyId: <aliyun-oss-access-key-id>
    accessKeySecret: <aliyun-oss-access-key-secret>
    bucketName: <aliyun-oss-bucket-name>

amap:
  key: <amap-key>

seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: <seata-tx-service-group>
  service:
    vgroup-mapping:
      <seata-tx-service-group>: <seata-cluster-name>
    grouplist:
      <seata-cluster-name>: <seata-server-host>:<seata-server-port>

openai:
  api-key: <openai-api-key>
  base-url: <openai-base-url>
  model: <openai-model>
```

安全规则：

- `AGENTS.md` 只记录配置结构和约定，不记录任何真实密码、Token、AccessKey、PrivateKey、Secret。
- 不要把数据库密码、Redis 密码、RabbitMQ 密码、邮箱授权码、OSS 密钥、支付宝私钥、高德 Key、Seata 地址、OpenAI Key、内部访问令牌写进说明文档或代码注释。
- 本地 `application.yml` 只保留端口、应用名、Nacos 连接信息、网关路由等启动必需配置。
- 如果新增服务依赖公共基础设施配置，优先复用 `common-dev.yaml`，不要在服务本地重复配置。
