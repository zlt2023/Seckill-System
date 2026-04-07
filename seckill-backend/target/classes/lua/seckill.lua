-- seckill.lua
-- 秒杀原子扣减脚本 (聚合 Hash 完整版)
-- KEYS[1] = seckill:info:{seckillGoodsId}    信息 Hash Key (包含 stock, startTime, endTime, status)
-- KEYS[2] = seckill:bought:{seckillGoodsId}  已购买集合 Key
-- ARGV[1] = userId
-- ARGV[2] = nowTime (当前服务器毫秒时间戳)

-- 1. 批量获取活动核心元数据
local info = redis.call('HMGET', KEYS[1], 'status', 'startTime', 'endTime', 'stock')
local status = tonumber(info[1])
local startTime = tonumber(info[2])
local endTime = tonumber(info[3])
local stock = tonumber(info[4])
local nowTime = tonumber(ARGV[2])

-- 2. 检查行政状态是否为上架(1)
if status == nil or status ~= 1 then
    return -3
end

-- 3. 校验时间视窗
if startTime == nil or endTime == nil or nowTime < startTime or nowTime > endTime then
    return -4
end

-- 4. 检查库存是否已售罄
if stock == nil or stock <= 0 then
    -- 如果库存不足或不存在，直接返回 -1
    return -1
end

-- 5. 检查是否重复购买
local bought = redis.call('SISMEMBER', KEYS[2], ARGV[1])
if bought == 1 then
    return -2
end

-- 6. 扣减库存 (使用 HINCRBY 实现 Hash 原子自减)
redis.call('HINCRBY', KEYS[1], 'stock', -1)

-- 7. 记录已购买用户
redis.call('SADD', KEYS[2], ARGV[1])

return 1
