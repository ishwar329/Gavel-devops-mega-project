local hashKey = KEYS[1]
local status = redis.call('HGET', hashKey, 'status')
if status == 'CLOSED' then
	redis.call('HSET', hashKey, 'status', 'OPEN')
	return 1
end
return 0
