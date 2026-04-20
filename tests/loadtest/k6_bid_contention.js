import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const bidLatency = new Trend('bid_latency_ms');
const bidAccepted = new Counter('bids_accepted');
const bidRejected = new Counter('bids_rejected');
const bidErrorRate = new Rate('bid_error_rate');
const doubleAccepts = new Counter('double_accepts');

const USER_SVC = __ENV.USER_SVC || 'http://localhost:8082';
const AUCTION_SVC = __ENV.AUCTION_SVC || 'http://localhost:8081';
const SHOP_SVC = __ENV.SHOP_SVC || 'http://localhost:8083';

const VUS = parseInt(__ENV.VUS || '50');
const DURATION = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    bid_contention: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    'bid_latency_ms': ['p(50)<200', 'p(95)<500', 'p(99)<1000'],
    'bid_error_rate': ['rate<0.01'],
    'double_accepts': ['count==0'],
  },
};

let setupData = null;

export function setup() {
  const runId = Date.now();

  const seller = register(`loadtestseller_${runId}`, 'seller');
  const sellerToken = login(`loadtestseller_${runId}@test.com`);

  const shop = http.post(`${SHOP_SVC}/shops`, JSON.stringify({
    name: 'Load Test Bakery',
    location: '1 Load St',
    lat: -33.8688,
    lng: 151.2093,
  }), { headers: authHeaders(sellerToken) });
  const shopId = JSON.parse(shop.body).shop_id;

  const item = http.post(`${SHOP_SVC}/shops/${shopId}/items`, JSON.stringify({
    title: 'Load Test Pastry',
    description: 'For load testing',
    retail_value: 10000,
  }), { headers: authHeaders(sellerToken) });
  const itemId = JSON.parse(item.body).item_id;

  const pickupStart = new Date(Date.now() + 86400000).toISOString();
  const pickupEnd = new Date(Date.now() + 172800000).toISOString();

  const auction = http.post(`${AUCTION_SVC}/auctions`, JSON.stringify({
    item_id: itemId,
    item_title: 'Load Test Pastry',
    shop_id: shopId,
    shop_name: 'Load Test Bakery',
    shop_lat: -33.8688,
    shop_lng: 151.2093,
    retail_price: 10000,
    description: 'Load test auction',
    image_url: 'https://example.com/img.jpg',
    duration_minutes: 10,
    start_bid: 100,
    pickup_start: pickupStart,
    pickup_end: pickupEnd,
  }), { headers: authHeaders(sellerToken) });
  const auctionId = JSON.parse(auction.body).auction_id;

  const buyers = [];
  for (let i = 0; i < VUS; i++) {
    const username = `loadbuyer_${runId}_${i}`;
    register(username, 'buyer');
    const token = login(`${username}@test.com`);
    buyers.push({ token, index: i });
  }

  return { auctionId, buyers, startBid: 100 };
}

export default function (data) {
  const buyer = data.buyers[__VU % data.buyers.length];
  const bidAmount = data.startBid + (__VU * 1000) + (__ITER * 50) + Math.floor(Math.random() * 20);

  const start = Date.now();
  const res = http.post(
    `${AUCTION_SVC}/auctions/${data.auctionId}/bid`,
    JSON.stringify({ amount: bidAmount }),
    { headers: authHeaders(buyer.token) }
  );
  const elapsed = Date.now() - start;
  bidLatency.add(elapsed);

  const body = JSON.parse(res.body || '{}');

  if (res.status === 201 && body.status === 'ACCEPTED') {
    bidAccepted.add(1);
    bidErrorRate.add(false);
  } else if (res.status === 400 || res.status === 409) {
    bidRejected.add(1);
    bidErrorRate.add(false);
  } else {
    bidErrorRate.add(true);
  }

  sleep(0.1 + Math.random() * 0.2);
}

export function teardown(data) {
  const res = http.get(`${AUCTION_SVC}/auctions/${data.auctionId}`);
  const auction = JSON.parse(res.body);
  console.log(`\n=== Load Test Results ===`);
  console.log(`Auction: ${data.auctionId}`);
  console.log(`Final highest bid: ${auction.current_highest}`);
  console.log(`Total bid count: ${auction.bid_count}`);
  console.log(`Highest bidder: ${auction.highest_bidder}`);
}

function register(username, role) {
  const res = http.post(`${USER_SVC}/users`, JSON.stringify({
    username,
    email: `${username}@test.com`,
    password: 'password123',
    role,
  }), { headers: { 'Content-Type': 'application/json' } });
  return JSON.parse(res.body);
}

function login(email) {
  const res = http.post(`${USER_SVC}/auth/login`, JSON.stringify({
    email,
    password: 'password123',
  }), { headers: { 'Content-Type': 'application/json' } });
  return JSON.parse(res.body).token;
}

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };
}
