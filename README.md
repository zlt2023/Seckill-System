# 高并发秒杀系统

一个完整的高并发秒杀系统，包含用户端和管理端，采用多层缓存和消息队列削峰设计，可应对高并发场景。

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| **前端** | Vue 3 + Vite + Element Plus + Pinia |
| **后端** | Java 17 + Spring Boot 3.2 + MyBatis-Plus |
| **缓存** | Redis 7 + Lua 脚本 |
| **数据库** | MySQL 8 |
| **消息队列** | RabbitMQ |
| **认证** | JWT |

## 系统架构

### 流量漏斗模型

层层递减，绝不让高并发流量直接打到 MySQL：

```
用户请求 (100%)
    │
    ▼
┌─────────────────────────────────┐
│ 第1层: 前端防护                  │ 按钮防抖 + 倒计时校验
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│ 第2层: 接口限流                  │ Redis + Lua 固定窗口限流
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│ 第3层: 动态路径Token             │ 60秒有效期，防脚本直刷
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│ 第4层: JVM内存标记               │ 已售罄商品直接返回
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│ 第5层: Redis Lua 原子扣减        │ 一次网络往返完成所有校验
└─────────────────────────────────┘
    │ (~1% 请求到达此处)
    ▼
┌─────────────────────────────────┐
│ 第6层: RabbitMQ 异步下单         │ 削峰填谷，保护数据库
└─────────────────────────────────┘
```

## 功能模块

### 用户端
- 秒杀商品列表（实时倒计时、库存展示）
- 商品详情页
- 执行秒杀（动态路径 + 排队轮询）
- 我的订单（查看、支付、取消）
- 登录/注册

### 管理端
- 商品管理
- 秒杀活动管理
- 订单管理
- 用户管理

## 核心特性

### 1. 超卖防护
- **Redis Lua 脚本**：原子性完成库存扣减 + 重复购买检查 + 时间/状态校验
- **MySQL 乐观锁**：`WHERE available_stock > 0` 兜底

### 2. 一人一单
- Redis Set 记录已购买用户
- 取消订单后自动移出 Set，允许重新购买

### 3. 数据一致性
- **订单超时取消**：RabbitMQ 延迟队列（15分钟）+ 定时任务兜底
- **定时对账**：每天凌晨2点对比 Redis 与 MySQL 库存，自动修复
- **完整回滚**：MQ 失败/订单取消时回滚 Redis 状态

### 4. 高可用
- 缓存预热（启动时自动加载）
- 死信队列（处理消费失败的消息）
- 分布式锁（防 MQ 重复消费）

## 数据库设计

### 核心表结构

| 表名 | 说明 |
|------|------|
| t_user | 用户表 |
| t_goods | 商品表 |
| t_seckill_goods | 秒杀商品表 |
| t_seckill_order | 秒杀订单表 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 7.0+
- RabbitMQ 3.12+

### 后端启动

```bash
# 1. 克隆项目
git clone https://github.com/yourname/seckill-system.git
cd seckill-system/seckill-backend

# 2. 修改配置文件
# 编辑 src/main/resources/application.yml
# 配置 MySQL、Redis、RabbitMQ 连接信息

# 3. 初始化数据库
# 执行 sql/schema.sql

# 4. 启动
mvn spring-boot:run
```

### 前端启动

```bash
cd seckill-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

## API 接口

### 用户端 - 秒杀

| 方法 | 接口 | 说明 | 认证 |
|------|------|------|------|
| GET | /api/seckill/goods | 秒杀商品列表 | 否 |
| GET | /api/seckill/goods/{id} | 商品详情 | 否 |
| GET | /api/seckill/time | 获取服务器时间 | 否 |
| GET | /api/seckill/path/{sgid} | 获取动态路径Token | 是 |
| POST | /api/seckill/{path}/execute | 执行秒杀 | 是 |
| GET | /api/seckill/result/{sgid} | 轮询结果 | 是 |

### 用户端 - 订单

| 方法 | 接口 | 说明 | 认证 |
|------|------|------|------|
| GET | /api/order/list | 我的订单列表 | 是 |
| POST | /api/order/{orderNo}/pay | 模拟支付 | 是 |
| POST | /api/order/{orderNo}/cancel | 取消订单 | 是 |

## 项目结构

```
seckill-system/
├── seckill-backend/
│   ├── src/main/java/com/seckill/
│   │   ├── SeckillApplication.java        # 启动类
│   │   ├── config/                         # 配置（Redis/RabbitMQ/Web）
│   │   ├── controller/                     # 控制器
│   │   ├── service/                        # 服务层（含秒杀核心逻辑）
│   │   ├── mq/                             # 消息生产者/消费者
│   │   ├── mapper/                         # MyBatis Mapper
│   │   ├── entity/                         # 实体类
│   │   ├── dto/                            # 数据传输对象
│   │   ├── common/                         # 公共类（Result/ResultCode）
│   │   ├── annotation/                     # 自定义注解（@AccessLimit）
│   │   ├── interceptor/                    # 拦截器（JWT/限流）
│   │   ├── exception/                      # 异常处理
│   │   └── task/                           # 定时任务
│   └── src/main/resources/
│       ├── lua/                            # Lua 脚本
│       │   ├── seckill.lua                 # 秒杀原子扣减脚本
│       │   └── ratelimit.lua               # 限流脚本
│       └── application.yml                 # 配置文件
│
└── seckill-frontend/
    ├── src/
    │   ├── views/                          # 页面
    │   ├── components/                     # 组件
    │   ├── api/                            # 接口定义
    │   ├── store/                          # Pinia 状态
    │   ├── router/                         # 路由
    │   └── utils/                          # 工具函数
    └── package.json
```

## 设计亮点

1. **Lua 脚本原子性**：一次网络往返完成库存扣减、重复购买检查、时间/状态校验
2. **内存标记拦截**：已售罄商品直接在 JVM 层返回，不访问 Redis
3. **动态路径隐藏**：秒杀接口 URL 动态生成，60秒过期，防止脚本刷接口
4. **异步削峰**：RabbitMQ 异步下单，平滑数据库压力
5. **双重超时取消**：延迟队列 + 定时任务兜底，确保订单超时必取消
6. **自动对账修复**：定时检查 Redis 与 MySQL 一致性，自动修复

## 许可证

MIT License

## 作者

欢迎 Star ⭐ 和 Fork！
