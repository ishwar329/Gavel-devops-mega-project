local key = KEYS[1]
local bidsKey = KEYS[2]
local amount = tonumber(ARGV[1])
local bidder = ARGV[2]

local status = redis.call('HGET', key, 'status')
if status == false then
	return {'-1', '', '0'}
end
if status ~= 'OPEN' then
	return {'-2', '', '0'}
end

local maxPrice = tonumber(redis.call('HGET', key, 'max_price')) or 0
if maxPrice > 0 and amount > maxPrice then
	return {'-4', '', tostring(maxPrice)}
end

local current = tonumber(redis.call('HGET', key, 'current_highest')) or 0
local minIncrement = tonumber(redis.call('HGET', key, 'min_increment')) or 0
local quantity = tonumber(redis.call('HGET', key, 'quantity')) or 1
local cardCount = redis.call('ZCARD', bidsKey)

if quantity <= 1 then
	-- Single-winner: standard logic
	if amount <= current then
		return {'-3', '', tostring(current)}
	end
	-- Enforce minimum increment (only when there is already a bid above start_bid)
	if minIncrement > 0 and current > 0 then
		local minNext = current + minIncrement
		if amount < minNext then
			return {'-5', '', tostring(minNext)}
		end
	end
else
	-- Multi-winner slot management
	local existingScore = redis.call('ZSCORE', bidsKey, bidder)
	if existingScore ~= false then
		-- Bidder already holds a slot: must improve on their own bid
		local prevBid = tonumber(existingScore)
		if amount <= prevBid then
			return {'-3', '', tostring(existingScore)}
		end
		if minIncrement > 0 and amount < prevBid + minIncrement then
			return {'-5', '', tostring(prevBid + minIncrement)}
		end
	elseif cardCount >= quantity then
		-- All slots full: must beat the floor (lowest current winner)
		local floor = redis.call('ZRANGE', bidsKey, 0, 0, 'WITHSCORES')
		if #floor >= 2 then
			local floorAmount = tonumber(floor[2])
			if amount <= floorAmount then
				return {'-3', '', tostring(floorAmount)}
			end
			if minIncrement > 0 and amount < floorAmount + minIncrement then
				return {'-5', '', tostring(floorAmount + minIncrement)}
			end
		end
	else
		-- Open slots available: must exceed start_bid (= initial current_highest)
		if amount <= current then
			return {'-3', '', tostring(current)}
		end
	end
end

local version = tonumber(redis.call('HGET', key, 'version')) or 0
local newVersion = version + 1

-- Place the bid in the sorted set
redis.call('ZADD', bidsKey, amount, bidder)

-- For multi-winner: trim excess and identify evicted bidder
local evicted = ''
if quantity > 1 then
	local newCard = redis.call('ZCARD', bidsKey)
	if newCard > quantity then
		local lowest = redis.call('ZRANGE', bidsKey, 0, 0)
		if #lowest >= 1 then
			evicted = lowest[1]
		end
		redis.call('ZREMRANGEBYRANK', bidsKey, 0, 0)
	end
end

-- Calculate the new floor price
local newFloor = amount  -- default for quantity=1: highest bid
if quantity > 1 then
	local finalCard = redis.call('ZCARD', bidsKey)
	if finalCard >= quantity then
		-- Floor = lowest winner's bid
		local floorEntry = redis.call('ZRANGE', bidsKey, 0, 0, 'WITHSCORES')
		if #floorEntry >= 2 then
			newFloor = tonumber(floorEntry[2])
		end
	else
		-- Still open slots: floor stays at start_bid (= current_highest)
		newFloor = current
	end
end

redis.call('HSET', key,
	'current_highest', newFloor,
	'highest_bidder',  bidder,
	'version',         newVersion)
redis.call('HINCRBY', key, 'bid_count', 1)

return {tostring(newVersion), evicted, tostring(newFloor)}
