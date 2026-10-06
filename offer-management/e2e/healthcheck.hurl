# Healthcheck e2e — smoke test for generated microservice
# Usage: hurl --test --variable BASE_URL=http://localhost:8080 e2e/healthcheck.hurl

# 1. Health endpoint — unauthenticated
GET {{BASE_URL}}/actuator/health
HTTP 200
[Asserts]
jsonpath "$.status" == "UP"

# 2. Secured endpoint returns 401 without token
GET {{BASE_URL}}/actuator/info
HTTP 401

# 3. Secured endpoint returns 401 with invalid token
GET {{BASE_URL}}/actuator/info
Authorization: Bearer invalid-token
HTTP 401
