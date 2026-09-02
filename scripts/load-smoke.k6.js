import http from 'k6/http';
import { check, sleep } from 'k6';
export const options = {
  scenarios: { smoke: { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '30s', preAllocatedVUs: 10, maxVUs: 50 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<750', 'p(99)<1500'] }
};
export default function () {
  const base = __ENV.BASE_URL || 'http://localhost:8080';
  const res = http.get(`${base}/actuator/health`);
  check(res, { 'health endpoint is 2xx': r => r.status >= 200 && r.status < 300 });
  sleep(0.1);
}
