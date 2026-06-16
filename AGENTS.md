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

不要再新增其它命名风格的 Controller，例如 `UserController`、`AuthController`、`CaptchaController` 等。具体业务含义通过方法名、接口路径、`@Tag` 和 `@Operation` 表达。

## 配置中心规则

所有微服务必须通过 Nacos 引入公共配置：

```yaml
spring:
  config:
    import:
      - nacos:common-dev.yml?group=ElE&refreshEnabled=true
```

Nacos 公共配置约定：

- Data ID：`common-dev.yml`
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
- 内部访问令牌：`internal.token`


配置中心 `common-dev.yml` 格式参考，真实值必须只放在 Nacos 中，文档中一律使用占位符：

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
  rabbitmq:
    host: <rabbitmq-host>
    port: <rabbitmq-port>
    username: <rabbitmq-username>
    password: <rabbitmq-password>
    listener:
      simple:
        default-requeue-rejected: false
  elasticsearch:
    uris: http://<elasticsearch-host>:<elasticsearch-port>
    username: <elasticsearch-username>
    password: <elasticsearch-password>
    connection-timeout: 10000
    read-timeout: 10000
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
```

安全规则：

- `AGENTS.md` 只记录配置结构和约定，不记录任何真实密码、Token、AccessKey、PrivateKey、Secret。
- 不要把数据库密码、Redis 密码、RabbitMQ 密码、邮箱授权码、OSS 密钥、支付宝私钥、高德 Key、内部访问令牌写进说明文档或代码注释。
- 本地 `application.yml` 只保留端口、应用名、Nacos 连接信息、网关路由等启动必需配置。
- 如果新增服务依赖公共基础设施配置，优先复用 `common-dev.yml`，不要在服务本地重复配置。
