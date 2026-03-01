# ⚡ FlashSale 高并发秒杀系统

> 基于 Spring Boot 3 + Vue 3 + Redis + RabbitMQ 构建的分布式秒杀系统，具备完善的安全防护机制、异步订单处理流程与自动化状态同步能力。
>
> 已通过全面架构安全审计，所有 P0/P1 级高并发与安全隐患已修复。

---

## 📐 系统架构

```
┌────────────────────────────────────────────────────────────┐
│                      前端层 (Vue3 + Vite)                   │
│   seckill-user (3001)          seckill-admin (3002)        │
└───────────────────────┬────────────────────────────────────┘
                        │ HTTP / JWT
┌───────────────────────▼────────────────────────────────────┐
│              seckill-backend (Spring Boot 3 / :8080)        │
│                                                            │
│  [拦截器层]  JWT → 限流(Lua原子计数) → 管理员身份校验          │
│  [安全层]    验证码(数学题) → 动态Path(原子校验) → 内存标记    │
│  [核心层]    Lua原子脚本(去重+预减库存) → MQ异步 → DB乐观锁   │
│  [调度层]    SeckillStatusScheduler (60s) 同步过期活动状态    │
└──────┬──────────────────────┬─────────────────────────────┘
       │                      │
┌──────▼──────┐         ┌─────▼──────┐
│   MySQL 3306 │         │  Redis 6379 │ ← 库存/去重/限流/路径/结果
└─────────────┘         └─────────────┘
┌─────────────┐
│ RabbitMQ 5672│ ← 秒杀队列 / 订单延迟死信队列(30min超时)
└─────────────┘
```

---

## 🔄 秒杀完整流程

```
用户端                          服务端                         中间件
─────────────────────────────────────────────────────────────────────
1. GET /api/captcha/seckill/{id}
   ← 返回数学算术题图片                存储答案 → Redis (2min TTL)

2. GET /api/seckill/path/{id}?captcha=xx
   ← 返回动态 path (MD5)             验证验证码答案 (CaptchaService, 用后即删)
                                      生成 path 存 Redis (1min TTL)
   [限流] Lua原子计数器, 5秒内5次

3. POST /api/seckill/{path}/do/{id}
   [安全校验] getAndDelete 原子验证并销毁 path
   ─────────── doSeckill() ──────────
   ① 内存标记：Boolean.TRUE → 直接拦截 (无网络IO)
   ② 查询SeckillGoods + 校验时间窗口 (NOT_START/ENDED)
   ③ 【原子Lua脚本】 检查去重 + 预减库存
      result=-1 → 重复秒杀
      result=0  → 标记内存售罄 → STOCK_EMPTY
      result=1  → 成功
   ④ 发送消息到 RabbitMQ (SECKILL_EXCHANGE)
   ← 立即返回 "请求已提交，请等待结果" [限流] 5秒内3次

4. GET /api/seckill/result/{id}   (前端每2秒轮询, 60秒超时)
   ← 0=排队中 | orderId=成功 | -1=失败

                            RabbitMQ 消费 (executeSeckill):
                            ① 查SeckillGoods + 校验库存
                            ② 再次校验时间窗口 (防延迟消费)
                            ③ DB查SeckillOrder去重(防并发插入)
                            ④ DB乐观锁减库存 (stock_count > 0)
                            ⑤ 事务中创建 OrderInfo + SeckillOrder
                            ⑥ Redis写结果 (24h TTL)
                            ⑦ 发延迟消息(死信队列 30min超时)
                            ⑧ 手动ACK

5. POST /api/order/{id}/pay        用户30分钟内支付（原子条件更新）
   ── 超时未支付 ──
   死信队列→ timeoutCancelOrder():
     状态=4(已取消) + DB恢复库存 + Redis库存+1
     + 清除去重标记 + 清除秒杀结果 + 清除内存售罄标记
```

---

## 🛡️ 安全防护层级

| 层级 | 机制 | 说明 |
|------|------|------|
| L1 | **数学验证码** | 分散请求、防机器人，Redis存储2min有效 |
| L2 | **动态秒杀Path** | MD5隐藏真实接口，1min单次有效，`getAndDelete` 原子校验 |
| L3 | **接口限流** `@RateLimit` | Lua 原子脚本计数器，防止 increment+expire 竞态 |
| L4 | **内存售罄标记** | `ConcurrentHashMap`，避免Redis IO |
| L5 | **Lua原子脚本** | 去重判断 + 库存预减合并为单个原子操作，消除中间崩溃风险 |
| L6 | **DB乐观锁** | `WHERE stock_count > 0`，兜底保障 |
| L7 | **事务+DB唯一约束** | `uk_user_goods(user_id,goods_id)` 双重防重 |
| L8 | **缓存穿透防护** | 不存在的商品缓存空值30秒，防止恶意ID穿透DB |
| L9 | **参数校验** | DTO 全字段 JSR-303 校验 + 全局异常处理器 |

---

## ⏱️ 活动状态自动同步

`SeckillStatusScheduler` 每60秒执行一次批量更新：

```sql
-- 将超期活动标记为已结束
UPDATE t_seckill_goods SET status = 2
WHERE status = 1 AND end_date < NOW() AND deleted = 0;
```

**`status` 字段含义：**

| 值 | 含义 | 触发方式 |
|----|------|---------|
| 0 | 未发布 | 管理员手动设置 |
| 1 | 进行中 | 管理员发布 + 在时间窗口内 |
| 2 | 已结束 | 定时任务自动同步 |

---

## 🗄️ 数据库设计

```sql
-- 用户表
t_user (id, username[UNIQUE], phone[UNIQUE], password, salt, nickname, avatar, role, status, deleted)

-- 秒杀商品表 (由原先t_goods及t_seckill_goods合并归一，移除普通库存，以stock_count作为唯一库存源)
t_seckill_goods (id, goods_name, goods_title, goods_img, goods_detail, goods_price, seckill_price, stock_count, start_date, end_date, goods_status, seckill_status, create_time, update_time, deleted)

-- 订单表
t_order_info (id, user_id[IDX], goods_id[IDX], goods_name, goods_count, goods_price, status, pay_time, deleted)
-- status: 0=未支付 1=已支付 2=已发货 3=已收货 4=已取消 5=已退款

-- 秒杀订单表（去重）
t_seckill_order (id, user_id, order_id, goods_id, UNIQUE(user_id, goods_id))
```

---

## 🗝️ Redis Key 设计

| Key | 格式 | TTL | 说明 |
|-----|------|-----|------|
| `seckill:stock:{id}` | Integer | 永久(管理重置) | 预热库存 |
| `seckill:order:{uid}:{gid}` | "1" | **24h** | 去重标记(Lua原子设置) |
| `seckill:path:{uid}:{sid}` | MD5 | **60s** | 动态路径(getAndDelete原子校验) |
| `seckill:result:{uid}:{sid}` | Long orderId | **24h** | 秒杀结果 |
| `captcha:seckill:{uid}:{sid}` | Integer | **2min** | 验证码答案 |
| `rate_limit:{uri}:{uid}` | Integer | 限流窗口 | Lua原子计数器 |
| `seckill:goods:list` | List\<Vo\> | **60s** | 商品列表缓存 |
| `seckill:goods:detail:{sid}` | Vo / "" | **60s / 30s** | 商品详情缓存(空值哨兵防穿透) |
| `seckill:user:token:{uid}` | JWT String | **24h** | 登录Token(支持踢人下线) |

---

## 🛠️ 技术栈

| 模块 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5, Spring MVC, MyBatis-Plus 3.5 |
| 安全 | JWT (JJWT 0.12.5), 两次MD5+随机盐值加密 |
| 缓存 | Redis (Lettuce连接池, Lua脚本原子操作) |
| 消息队列 | RabbitMQ (Direct Exchange + 死信队列 TTL延迟) |
| 数据库 | MySQL 8.0 (HikariCP连接池) |
| 工具 | Hutool, Lombok, Swagger(Knife4j 4.5) |
| 前端 | Vue 3 (Composition API), Vite 5, Element Plus, Pinia, Axios |
| 调度 | Spring `@Scheduled` (*60s* 状态同步) |

---

## 🚀 快速启动

### 环境依赖

| 依赖 | 版本 | 默认地址 |
|------|------|---------|
| Java | 17+ | - |
| MySQL | 8.0+ | `192.168.150.103:3306` |
| Redis | 6.0+ | `192.168.150.103:6379` |
| RabbitMQ | 3.x+ | `192.168.150.103:5672` |
| Node.js | 18+ | - |

### 环境变量配置（可选，覆盖默认值）

```bash
# 数据库
export DB_URL=jdbc:mysql://your-host:3306/seckill?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
export DB_USERNAME=root
export DB_PASSWORD=your_password

# Redis
export REDIS_HOST=your-host
export REDIS_PORT=6379
export REDIS_PASSWORD=your_password

# RabbitMQ
export RABBITMQ_HOST=your-host
export RABBITMQ_USERNAME=admin
export RABBITMQ_PASSWORD=your_password

# JWT (生产环境必须更换!)
export JWT_SECRET=your-production-secret-key-at-least-256-bits
```

### 1. 初始化数据库

```bash
# 执行 SQL 初始化脚本
mysql -h your-host -u root -p < seckill-backend/src/main/resources/db/init.sql
```

### 2. 启动后端

```bash
cd seckill-backend
mvn spring-boot:run
# 服务启动于 http://localhost:8080/api
# Swagger UI: http://localhost:8080/api/doc.html
```

### 3. 启动用户前端

```bash
cd seckill-user
npm install
npm run dev
# 访问 http://localhost:3001
```

### 4. 启动管理前端

```bash
cd seckill-admin
npm install
npm run dev
# 访问 http://localhost:3002
```

### 默认账号

| 角色 | 手机号 | 密码 |
|------|--------|------|
| 管理员 | `13800138000` | `123456` |
| 测试用户 | `13800138001` | `123456` |

---

## 📁 项目结构

```
Seckill System/
├── seckill-backend/           # Spring Boot 后端
│   └── src/main/java/com/seckill/
│       ├── annotation/        # @RateLimit, @AdminOnly 自定义注解
│       ├── common/            # Result统一返回, ResultCode状态码枚举
│       ├── config/            # Redis(Lua脚本)/RabbitMQ/MyBatis-Plus/WebMvc 配置
│       ├── controller/        # REST控制器（用户/商品/秒杀/订单/验证码/管理端）
│       ├── dto/               # 请求DTO（含JSR-303参数校验）
│       ├── entity/            # MyBatis-Plus实体（User/Goods/SeckillGoods/Order）
│       ├── exception/         # BusinessException + 全局异常处理器
│       ├── interceptor/       # JWT拦截器/限流拦截器(Lua)/管理员拦截器
│       ├── mapper/            # MyBatis Mapper（含乐观锁SQL）
│       ├── mq/                # RabbitMQ消费者(秒杀下单/超时取消)
│       ├── scheduler/         # 定时任务(活动状态自动同步)
│       ├── service/           # 业务逻辑(商品/秒杀/订单/用户/验证码)
│       ├── utils/             # JWT工具/UserContext/Md5Utils
│       └── vo/                # 响应VO
├── seckill-user/              # 用户端 Vue3 前端
│   └── src/
│       ├── api/               # Axios API封装
│       ├── layout/            # 主布局
│       ├── router/            # 路由（含登录守卫）
│       ├── stores/            # Pinia状态管理
│       ├── styles/            # 全局CSS
│       └── views/             # 页面(首页/详情/登录/注册/订单/秒杀结果)
└── seckill-admin/             # 管理端 Vue3 前端
    └── src/
        ├── api/               # 管理端API
        ├── router/            # 路由（管理员权限校验）
        ├── styles/            # 全局CSS
        └── views/             # 仪表盘/商品管理/订单管理
```

---

## 🔍 关键设计决策

### 1. 为何使用合并 Lua 脚本（去重+预减库存）？

原方案分两步：先 `setIfAbsent` 标记去重，再 Lua 扣库存。两步之间如果应用崩溃，会导致用户被标记为"已秒杀"但库存未扣——该用户永远无法再参与秒杀（少卖）。合并为单个 Lua 脚本后，`exists + decr + setex` 三个操作在 Redis 单线程中原子执行，彻底消除竞态。

### 2. 为何需要 DB 乐观锁作为兜底？

Redis 是内存数据，存在宕机风险（即使有持久化，也可能丢失最近数据）。DB 层 `WHERE stock_count > 0` 是最后一道防线，确保即使 Redis 出现异常，库存也不会为负。

### 3. 为何订单超时取消使用死信队列而非定时扫描？

定时扫描全表代价高，且时延不精确。RabbitMQ TTL + 死信队列实现精准 30 分钟超时回调，不需要每分钟扫描整个订单表。

### 4. 为何限流使用 Lua 脚本而非分步操作？

原方案 `increment` 和 `expire` 分两步执行，如果 `increment` 成功后应用崩溃导致 `expire` 未执行，该 Key 永不过期，用户被永久限流。Lua 脚本在 Redis 中原子执行 `incr + expire`，完全消除此风险。

### 5. 活动状态为何不完全依赖 DB status 字段？

数据库 `status` 字段是静态值，活动到期后不会自动变化。系统采用**双保险策略**：

- **展示层**（Dashboard/用户端）：基于当前时间动态计算真实状态
- **定时任务**：每60秒批量将 DB status 同步至最新，保持数据一致性

---

## 🔧 生产部署注意事项

1. **密钥安全**: 所有敏感配置已改为环境变量占位符，生产环境务必设置 `JWT_SECRET`、`DB_PASSWORD` 等
2. **CORS 收紧**: `WebMvcConfig` 中 `allowedOriginPatterns("*")` 应改为具体域名
3. **密码加密升级**: 当前使用两次 MD5 + 盐值，生产环境建议升级为 BCrypt 或 Argon2
4. **SQL 日志**: 已切换为 SLF4J，生产环境配合 Logback 输出到文件
5. **Redis 持久化**: 建议开启 AOF 持久化，降低宕机后库存数据不一致的风险

---

## 📊 性能指标参考

| 场景 | 说明 |
|------|------|
| 大量请求被内存标记拦截 | 无任何网络IO，纳秒级响应 |
| Lua原子脚本(去重+预减) | 微秒级，Redis单机10万+ QPS |
| MQ削峰 | 异步下单，后端不阻塞 |
| DB写入 | 经MQ削峰后，DB仅承受真实成交的写压力 |

---

## 📝 接口文档

启动后端后访问 Knife4j UI：
`http://localhost:8080/api/doc.html`

主要接口模块：

- **用户模块** `/api/user` — 注册/登录/退出/个人信息
- **商品模块** `/api/goods` — 秒杀商品列表/详情
- **验证码模块** `/api/captcha` — 获取算术验证码
- **秒杀模块** `/api/seckill` — 获取路径/执行秒杀/查询结果
- **订单模块** `/api/order` — 订单列表/详情/支付/取消/统计
- **管理模块** `/api/admin` — 仪表盘/商品CRUD/重置库存/订单管理

---

## 🔒 审计修复记录 (v1.1.0)

| 优先级 | 编号 | 问题 | 修复方式 |
|--------|------|------|---------|
| 🚨 P0 | P0-1 | 限流 increment+expire 非原子 | Lua 原子脚本 `rateLimitScript` |
| 🚨 P0 | P0-2 | 秒杀路径 get+delete 可重放 | `getAndDelete` 原子操作 |
| 🚨 P0 | P0-3 | 去重+预减库存分离导致少卖 | 合并 Lua 脚本 `seckillScript` |
| 🚨 P0 | P0-4 | 密码/密钥明文硬编码 | 环境变量占位符 `${ENV_VAR:default}` |
| 🚨 P0 | P0-5 | username 缺唯一索引 | `UNIQUE KEY uk_username` |
| ⚠️ P1 | P1-1 | Service 依赖 Controller | 提取 `CaptchaService` |
| ⚠️ P1 | P1-3 | 取消订单未清除内存售罄标记 | `clearStockOverFlag()` + `@Lazy` |
| ⚠️ P1 | P1-4 | DTO 无参数校验 | JSR-303 注解 + `@Valid` |
| ⚠️ P1 | P1-5 | 缺 JSON 解析异常处理 | `HttpMessageNotReadableException` handler |
| ⚠️ P1 | P1-6 | 初始化加载全部秒杀商品 | 过滤 `status=1` |
| ⚠️ P1 | P1-7 | 缓存穿透无防护 | 空值哨兵缓存 30s |
| 📌 P2 | P2-5 | SQL 日志 StdOut 输出 | 改为 SLF4J |
| 📌 P2 | P2-8 | 支付先查再改非原子 | 条件更新 `WHERE status=0` |

---

## 🏗️ 架构重构记录 (v1.2.0)

1. **统一商品信息流**: 彻底废弃分离的 `t_goods` 表，所有商品核心信息、商品上架状态 `goods_status` 和活动状态 `seckill_status` 熔合进 `t_seckill_goods` 单表。原先繁琐的普通库存被淘汰，统一采用 `stock_count`。
2. **清除强干扰操作**: 完全移除后台管理系统的“重置库存”功能，从前端组件到底层逻辑全面剥离，切断管理人员强行改库干扰正常秒杀进度并导致数据混乱的根源。
3. **修复致命的数据一致性回滚**: `executeSeckill` 的 MySQL 回滚现在能自动触发 `handleSeckillFail`，当 MQ 发生执行异常时，将 Redis 中已经被扣减的对应库存进行等量反还，修复了原先异常引起“少卖”的隐患。
4. **追加和清理式的缓存守护**: 将 `SeckillStatusScheduler` 修改为增量追加式 `incrementalInitSeckillStock (SETNX)`，避免覆盖正在秒杀的最新缓存。并自动在活动定时结束时清空释放其残留在 Redis 内的库存、详情凭证以及内存变量。
5. **解除数据库雪崩瓶颈**: 从 `doSeckill` 核心链路最前线中移除了针对商品状态和时间的 `selectById` 数据库主动查询，将时间窗口拦截与合法性彻底委托给 Redis 与 MQ 消费者闭环。极大地提升了系统的并发承载力(QPS)。
