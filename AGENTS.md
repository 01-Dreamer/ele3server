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

- `status = 1`：正常放行
- `status = 2`：账号封禁，拒绝请求
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
