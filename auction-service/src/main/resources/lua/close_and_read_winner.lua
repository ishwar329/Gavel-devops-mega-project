local hashKey = KEYS[1]
local bidsKey = KEYS[2]

local status = redis.call('HGET', hashKey, 'status')
if status == false then
	return {'ERR_NOT_FOUND', '', '0'}
end
if status ~= 'OPEN' then
	return {'ERR_NOT_OPEN', '', '0'}
end

redis.call('HSET', hashKey, 'status', 'CLOSED')

local quantity = tonumber(redis.call('HGET', hashKey, 'quantity')) or 1

if quantity <= 1 then
	-- Single-winner: original logic
	local top = redis.call('ZREVRANGE', bidsKey, 0, 0, 'WITHSCORES')
	if #top >= 2 then
		return {'OK', top[1], top[2]}
	end
	local bidder = redis.call('HGET', hashKey, 'highest_bidder') or ''
	local amount = redis.call('HGET', hashKey, 'current_highest') or '0'
	return {'OK', bidder, amount}
end

-- Multi-winner: return top-N from sorted set
local top = redis.call('ZREVRANGE', bidsKey, 0, quantity - 1, 'WITHSCORES')
if #top == 0 then
	return {'OK_MULTI', '', tostring(quantity)}
end

local pairs = {}
for i = 1, #top, 2 do
	pairs[#pairs + 1] = top[i] .. ':' .. top[i + 1]
end

return {'OK_MULTI', table.concat(pairs, ','), tostring(quantity)}
