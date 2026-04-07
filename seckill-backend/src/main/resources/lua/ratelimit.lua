-- ratelimit.lua
-- 基于 Redis 的访问频率限制 (固定窗口)
-- KEYS[1] = limit:uri:userId
-- ARGV[1] = expireTime (秒)
-- ARGV[2] = maxCount

local key = KEYS[1]
local expireTime = tonumber(ARGV[1])
local maxCount = tonumber(ARGV[2])

-- 获取当前访问次数
local currentCount = tonumber(redis.call('GET', key) or "0")

if currentCount >= maxCount then
    -- 超过限制
    return 0
end

if currentCount == 0 then
    -- 第一次访问，设置值并设置过期时间
    redis.call('SETEX', key, expireTime, 1)
    return 1
else
    -- 增加次数
    redis.call('INCR', key)
    return 1
end
