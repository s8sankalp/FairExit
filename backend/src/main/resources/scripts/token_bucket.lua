-- Token bucket, run atomically inside Redis (no race between read and write).
-- KEYS[1] = bucket key
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refill rate in tokens per millisecond
-- ARGV[3] = key expiry in milliseconds
-- Returns {allowed (1/0), tokens left (rounded down), ms until the next token}

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

-- Redis server time, so every app instance shares one clock
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local bucket = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(bucket[1])
local ts = tonumber(bucket[2])
if tokens == nil then
    tokens = capacity
    ts = now
end

-- Add the tokens earned since the last request, never above capacity
tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate)

local allowed = 0
local retry_ms = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
else
    retry_ms = math.ceil((1 - tokens) / rate)
end

redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now))
-- An idle bucket refills completely within ttl, so an expired key means "full"
redis.call('PEXPIRE', key, ttl)

return {allowed, math.floor(tokens), retry_ms}
