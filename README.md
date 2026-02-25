# ⚡ FlashSale 高并发秒杀系统

> 基于 Spring Boot 3 + Vue 3 + Redis + RabbitMQ 构建的分布式秒杀系统，具备完善的安全防护机制、异步订单处理流程与自动化状态同步能力。

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
│  [拦截器层]  JWT → 限流(RateLimit) → 管理员身份校验            │
│  [安全层]    验证码(数学题) → 动态Path → 内存售罄标记           │
│  [核心层]    Redis Lua预减库存 → RabbitMQ异步 → DB乐观锁下单  │
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
   ← 返回动态 path (MD5)             验证验证码答案 (用后即删)
                                      生成 path 存 Redis (1min TTL)
   [限流] 5秒内5次

3. POST /api/seckill/{path}/do/{id}
   [安全校验] 验证并销毁 path
   ─────────── doSeckill() ──────────
   ① 内存标记：Boolean.TRUE → 直接拦截 (无网络IO)
   ② 查询SeckillGoods + 校验时间窗口 (NOT_START/ENDED)
   ③ setIfAbsent(ORDER_KEY, "1", 24h TTL) 去重
   ④ Lua脚本原子预减 Redis 库存
      stock=0 → 标记内存 + 回滚去重key → STOCK_EMPTY
   ⑤ 发送消息到 RabbitMQ (SECKILL_EXCHANGE)
   ← 立即返回 "请求已提交，请等待结果" [限流] 5秒内3次

4. GET /api/seckill/result/{id}   (轮询)
   ← 0=排队中 | orderId=成功 | -1=失败

                            RabbitMQ 消费 (executeSeckill):
                            ① 查SeckillGoods + 校验库存
                            ② 再次校验时间窗口 (防延迟消费)
                            ③ DB查SeckillOrder去重(防并发插入)
                            ④ DB乐观锁减库存 (stock_count > 0)
                            ⑤ 事务中创建 OrderInfo + SeckillOrder
                            ⑥ Redis写结果 (24h TTL)
                            ⑦ 发延迟消息(死信队列 30min超时)
                            ⑧ ACK

5. POST /api/order/{id}/pay        用户30分钟内支付
   ── 超时未支付 ──
   死信队列→ timeoutCancelOrder():
     状态=4(已取消) + DB恢复库存 + Redis库存+1 + 清除去重标记
```

---

## 🛡️ 安全防护层级

| 层级 | 机制 | 说明 |
|------|------|------|
| L1 | **数学验证码** | 分散请求、防机器人，Redis存储2min有效 |
| L2 | **动态秒杀Path** | MD5隐藏真实接口，1min单次有效(用后即删) |
| L3 | **接口限流** `@RateLimit` | 基于Redis计数器，userId维度滑动窗口 |
| L4 | **内存售罄标记** | `ConcurrentHashMap`，避免Redis IO |
| L5 | **Redis去重** `setIfAbsent` | 24h TTL，防止重复提交 |
| L6 | **Lua原子预减** | 库存判断+扣减原子执行，防超卖 |
| L7 | **DB乐观锁** | `WHERE stock_count > 0`，兜底保障 |
| L8 | **事务+DB唯一约束** | SeckillOrder双重防重 |

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
-- 商品表
t_goods (id, goods_name, goods_title, goods_img, goods_detail, goods_price, goods_stock, status, deleted)

-- 秒杀商品表
t_seckill_goods (id, goods_id, seckill_price, stock_count, start_date, end_date, status, deleted)

-- 用户表
t_user (id, phone, password, nickname, salt, deleted)

-- 订单表
t_order_info (id, user_id, goods_id, seckill_goods_id, goods_name, goods_count, goods_price, status, pay_time, deleted)
-- status: 0=未支付 1=已支付 2=已发货 3=已收货 4=已取消

-- 秒杀订单表（去重）
t_seckill_order (id, user_id, order_id, goods_id)
```

---

## 🗝️ Redis Key 设计

| Key | 格式 | TTL | 说明 |
|-----|------|-----|------|
| `seckill:stock:{id}` | Integer | 永久(管理重置) | 预热库存 |
| `seckill:order:{uid}:{gid}` | "1" | **24h** | 去重标记 |
| `seckill:path:{uid}:{sid}` | MD5 | **60s** | 动态路径 |
| `seckill:result:{uid}:{sid}` | Long orderId | **24h** | 秒杀结果 |
| `captcha:seckill:{uid}:{sid}` | Integer | **2min** | 验证码答案 |
| `rate_limit:{uri}:{uid}` | Integer | 限流窗口 | 接口频率 |
| `seckill:goods:list` | List\<Vo\> | **60s** | 商品列表缓存 |
| `seckill:goods:detail:{sid}` | Vo | **60s** | 商品详情缓存 |

---

## 🛠️ 技术栈

| 模块 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5, Spring MVC, MyBatis-Plus 3.5 |
| 安全 | JWT (JJWT), BCrypt密码加密 |
| 缓存 | Redis (Lettuce连接池, Lua脚本) |
| 消息队列 | RabbitMQ (Direct Exchange + 死信队列 TTL延迟) |
| 数据库 | MySQL 8.0 (HikariCP连接池) |
| 工具 | Hutool, Lombok, Swagger(Knife4j) |
| 前端 | Vue 3 (Composition API), Vite, Element Plus, Pinia, Axios |
| 调度 | Spring `@Scheduled` (*60s* 状态同步) |

---

## 🚀 快速启动

### 环境依赖

| 依赖 | 版本 | 地址 |
|------|------|------|
| Java | 17+ | - |
| MySQL | 8.0+ | `192.168.150.103:3306` |
| Redis | 6.0+ | `192.168.150.103:6379` |
| RabbitMQ | 3.x+ | `192.168.150.103:5672` |
| Node.js | 18+ | - |

### 1. 启动后端

```bash
cd seckill-backend
mvn spring-boot:run
# 服务启动于 http://localhost:8080/api
# Swagger UI: http://localhost:8080/api/swagger-ui.html
```

### 2. 启动用户前端

```bash
cd seckill-user
npm install
npm run dev
# 访问 http://localhost:3001
```

### 3. 启动管理前端

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
| 测试用户 | `13900139000` | `123456` |

---

## 📁 项目结构

```
Seckill System/
├── seckill-backend/           # Spring Boot 后端
│   └── src/main/java/com/seckill/
│       ├── annotation/        # @RateLimit 限流注解
│       ├── config/            # Redis/RabbitMQ/JWT/Swagger 配置
│       ├── controller/        # REST控制器（用户/商品/秒杀/订单/管理端）
│       ├── dto/               # 请求DTO
│       ├── entity/            # MyBatis-Plus实体
│       ├── exception/         # 全局异常处理
│       ├── interceptor/       # JWT拦截器/限流拦截器/管理员拦截器
│       ├── mapper/            # MyBatis Mapper
│       ├── mq/                # RabbitMQ消费者(秒杀消费/超时取消)
│       ├── scheduler/         # 定时任务(状态自动同步)
│       ├── service/           # 业务逻辑(商品/秒杀/订单/用户)
│       ├── utils/             # JWT工具/UserContext/雪花ID
│       └── vo/                # 响应VO
├── seckill-user/              # 用户端 Vue3 前端
│   └── src/
│       ├── api/               # Axios API封装
│       ├── router/            # 路由
│       ├── stores/            # Pinia状态管理
│       ├── styles/            # 全局CSS（亮色主题）
│       └── views/             # 页面(首页/详情/登录/订单)
└── seckill-admin/             # 管理端 Vue3 前端
    └── src/
        ├── api/               # 管理端API
        ├── router/            # 路由（管理员权限校验）
        ├── styles/            # 全局CSS（亮色主题+侧边栏深色）
        └── views/             # 仪表盘/商品管理/订单管理
```

---

## 🔍 关键设计决策

### 1. 为何使用 Lua 脚本预减库存？

Redis 的 `DECR` 命令本身是原子的，但"判断库存 > 0 → 再扣减"需要两步操作，中间会产生竞争条件。Lua 脚本在 Redis 单线程模型下保证判断+扣减的原子性，彻底防止超卖。

### 2. 为何需要 DB 乐观锁作为兜底？

Redis 是内存数据，存在宕机风险（即使有持久化，也可能丢失最近数据）。DB 层 `WHERE stock_count > 0` 是最后一道防线，确保即使 Redis 出现异常，库存也不会为负。

### 3. 为何订单超时取消使用死信队列而非定时扫描？

定时扫描全表代价高，且时延不精确。RabbitMQ TTL + 死信队列实现精准 30 分钟超时回调，不需要每分钟扫描整个订单表。

### 4. 活动状态为何不完全依赖 DB status 字段？

数据库 `status` 字段是静态值，活动到期后不会自动变化。系统采用**双保险策略**：

- **展示层**（Dashboard/用户端）：基于当前时间动态计算真实状态
- **定时任务**：每60秒批量将 DB status 同步至最新，保持数据一致性

---

## 📊 性能指标参考

| 场景 | 说明 |
|------|------|
| 大量请求被内存标记拦截 | 无任何网络IO，纳秒级响应 |
| Redis去重+Lua预减 | 微秒级，Redis单机10万+ QPS |
| MQ削峰 | 异步下单，后端不阻塞 |
| DB写入 | 经MQ削峰后，DB仅承受真实成交的写压力 |

---

## 📝 接口文档

启动后端后访问 Swagger UI：
`http://localhost:8080/api/swagger-ui.html`

主要接口模块：

- **用户模块** `/api/user` — 注册/登录/个人信息
- **商品模块** `/api/goods` — 秒杀商品列表/详情
- **验证码模块** `/api/captcha` — 获取算术验证码
- **秒杀模块** `/api/seckill` — 获取路径/执行秒杀/查询结果
- **订单模块** `/api/order` — 订单列表/支付/取消
- **管理模块** `/api/admin` — 仪表盘/商品CRUD/重置库存/订单管理
