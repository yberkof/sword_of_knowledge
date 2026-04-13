/**
 * REST smoke for capacity planning. Targets from slos.env.example (export before run).
 * Usage: BASE_URL=http://localhost:8080 k6 run scripts/load/rest-smoke.k6.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const base = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
  vus: Number(__ENV.K6_REST_VUS || 50),
  duration: __ENV.K6_REST_DURATION || "2m",
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(99)<2000"],
  },
};

export default function () {
  const res = http.get(`${base}/actuator/health`);
  check(res, { "health 200": (r) => r.status === 200 });
  sleep(0.1);
}
