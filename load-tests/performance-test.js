import http from 'k6/http';
import { check, sleep } from 'k6';

// Test configuration optimized for local SAM emulation
export const options = {
  stages: [
    { duration: '20s', target: 2 }, // Small ramp-up
    { duration: '40s', target: 3 }, // Stable low load
    { duration: '10s', target: 0 }, // Ramp-down
  ],
  thresholds: {
    http_req_failed: ['rate<0.10'],   // Allow up to 10% errors due to local environment instability
  },
  setupTimeout: '2m',
};

const BASE_URL = __ENV.API_URL || 'http://host.docker.internal:3001';
const AUTH_HEADERS = {
  headers: {
    'Authorization': 'Bearer allow-me',
    'Content-Type': 'application/json',
  },
};

/**
 * Main performance test scenario.
 */
export default function () {
  // Scenario 1: Get Products (Public)
  const resGet = http.get(`${BASE_URL}/products`);
  check(resGet, {
    'get products status is 200': (r) => r.status === 200,
  });

  sleep(1);

  // Scenario 2: Create Product (Authenticated)
  const payload = JSON.stringify({
    name: `LoadTest Product ${__VU}:${__ITER}`,
    price: 19.99,
    category: 'LoadTest',
    stockQuantity: 100,
  });

  const resPost = http.post(`${BASE_URL}/products`, payload, AUTH_HEADERS);
  check(resPost, {
    'create product status is 201': (r) => r.status === 201,
  });

  sleep(1);
}
