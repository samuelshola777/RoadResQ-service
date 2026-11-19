local vendors
if ARGV[1] == '' then
    vendors = {ARGV[2]}
else
    vendors = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1], 'LIMIT', 0, 1000)
end

if #vendors > 0 then
    redis.call('ZREM', KEYS[1], unpack(vendors))
    redis.call('ZREM', KEYS[2], unpack(vendors))
    redis.call('HDEL', KEYS[3], unpack(vendors))
end
return #vendors