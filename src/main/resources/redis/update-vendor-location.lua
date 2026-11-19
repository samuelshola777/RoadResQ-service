local previous = redis.call('HGET', KEYS[3], ARGV[1])
if previous and tonumber(previous) >= tonumber(ARGV[4]) then
    return 0
end

local seen = redis.call('ZSCORE', KEYS[2], ARGV[1])
if seen and tonumber(ARGV[5]) - tonumber(seen) < 4000 then
    return 0
end

redis.call('GEOADD', KEYS[1], ARGV[2], ARGV[3], ARGV[1])
redis.call('ZADD', KEYS[2], ARGV[5], ARGV[1])
redis.call('HSET', KEYS[3], ARGV[1], ARGV[4])
return 1