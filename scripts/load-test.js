// k6 load test for the question endpoint. Run against a loaded instance:
//
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 \
//     grafana/k6 run - < scripts/load-test.js
//
// The rate limiter must be raised for the run, otherwise the script measures
// 429 responses instead of generation:
//
//   APP_RATE_LIMIT_REQUESTS_PER_MINUTE=1000000 java -jar target/*.jar
//
// Options are overridable per run with -e VUS=20 -e DURATION=60s.

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    questions: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '20s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const payload = JSON.stringify({ question: 'How do I roll back a deployment?' });
const params = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  const response = http.post(`${BASE_URL}/api/ask`, payload, params);
  check(response, {
    'status is 200': (request) => request.status === 200,
    'answer present': (request) => request.json('answer') !== undefined,
  });
}
