# cs6650-assignment2

## Local Development

### Prerequisites
- Java 17+
- Gradle
- Docker & Docker Compose

---

### Step 1: Install Docker

```bash
cd deployment
chmod +x install-docker.sh
./install-docker.sh
```

---

### Step 2: Start RabbitMQ + Postgres

```bash
# still in deployment/
docker-compose up -d
```

RabbitMQ Management Console: http://localhost:15672 (admin / admin)
Postgres: localhost:5432 (chatflow / chatflow)

Initialize schema:
```bash
cd database
./setup.sh
```

---

### Step 3: Start Server

```bash
cd server-v2
./gradlew clean build -x test
./gradlew bootRun \
  --args='--spring.rabbitmq.host=localhost --spring.rabbitmq.port=5672 --spring.rabbitmq.username=admin --spring.rabbitmq.password=admin --server.url=http://localhost:8080 --consumer.url=http://localhost:8081'
```

Verify: http://localhost:8080/health

---

### Step 4: Start Consumer (v3)

```bash
cd consumer-v3
./gradlew clean build -x test
./gradlew bootRun \
  --args='--spring.rabbitmq.host=localhost --spring.rabbitmq.port=5672 --spring.rabbitmq.username=admin --spring.rabbitmq.password=admin --consumer.thread-count=20'
```

Verify: http://localhost:8081/health
Analytics API: http://localhost:8081/metrics/analytics

---

### Step 5: Build & Run Client

```bash
cd client
./gradlew clean build -x test
java -jar build/libs/client-0.1.0.jar
```

Results will be saved to `client/results/`
Metrics API response will be saved to `client/results/metrics_api_response.json`

---

### Monitoring

```bash
# one-time snapshot
cd monitoring
./check-metrics-local.sh

# live (requires: brew install watch)
./watch-metrics-local.sh
```

### Query Profiling

Use PostgreSQL `EXPLAIN ANALYZE` to compare raw-table analytics queries
against the materialized-view-backed versions:

```bash
cd database
psql "postgresql://admin:admin@localhost:5432/chatflow" -f explain_analytics.sql
```

This script profiles:
- messages per minute over the last 24 hours
- top users over the last 24 hours
- top rooms over the last 24 hours

Compare execution time, scanned rows, and whether the query plan is using
the expected indexes or pre-aggregated materialized views.

### Before/After Measurement

For a repeatable process to measure analytics query latency and throughput
before and after the optimization, see:

- `database/measurement_guide.md`

---

### Stop Services

Press `Ctrl+C` in each terminal to stop the server and consumer.

To stop RabbitMQ:
```bash
cd deployment
docker-compose down -v
```

---

## EC2 Deployment

### Prerequisites
Replace the following placeholders with actual values:
- `<path-to-your-pem-key>` — path to your EC2 key file
- `<Server1-Public-IP>` — Server 1
- `<Server2-Public-IP>` — Server 2
- `<Server3-Public-IP>` — Server 3
- `<Server4-Public-IP>` — Server 4
- `<Consumer-Public-IP>` — Consumer
- `<Postgres-Public-IP>` — PostgresSQL
- `<RabbitMQ-Public-IP>` — RabbitMQ

---

### Step 1: Build & Deploy RabbitMQ and PostgreSQL

```bash
scp -i <path-to-your-pem-key> deployment/install-docker.sh ubuntu@<RabbitMQ-Public-IP>:~/
scp -i <path-to-your-pem-key> deployment/docker-compose.yml ubuntu@<RabbitMQ-Public-IP>:~/

# First time only: install Docker
ssh -i <path-to-your-pem-key> ubuntu@<RabbitMQ-Public-IP> "chmod +x install-docker.sh && ./install-docker.sh"

# Start RabbitMQ
ssh -i <path-to-your-pem-key> ubuntu@<RabbitMQ-Public-IP> "docker-compose up rabbitmq -d"
```

```bash
scp -i <path-to-your-pem-key> deployment/install-docker.sh ubuntu@<Postgres-Public-IP>:~/
scp -i <path-to-your-pem-key> deployment/docker-compose.yml ubuntu@<Postgres-Public-IP>:~/

# First time only: install Docker
ssh -i <path-to-your-pem-key> ubuntu@<Postgres-Public-IP> "chmod +x install-docker.sh && ./install-docker.sh"

# Start PostgresSQL
ssh -i <path-to-your-pem-key> ubuntu@<RabbitMQ-Public-IP> "docker-compose up postgres -d"
```

---

### Step 2: Build & Deploy & Start Servers

```bash
cd server-v2
./gradlew clean build -x test
cd ..

# Server 1
scp -i <path-to-your-pem-key> server-v2/build/libs/server-v2-0.0.1-SNAPSHOT.jar ubuntu@<Server1-Public-IP>:~/
scp -i <path-to-your-pem-key> deployment/install-java.sh deployment/start-server1.sh deployment/stop-server.sh ubuntu@<Server1-Public-IP>:~/
# Step 1: Install Java
ssh -i <path-to-your-pem-key> ubuntu@<Server1-Public-IP> "chmod +x install-java.sh start-server1.sh stop-server.sh && ./install-java.sh"
# Step 2: Start Server
ssh -i <path-to-your-pem-key> ubuntu@<Server1-Public-IP> "./start-server1.sh"

# Server 2
scp -i <path-to-your-pem-key> server-v2/build/libs/server-v2-0.0.1-SNAPSHOT.jar ubuntu@<Server2-Public-IP>:~/
scp -i <path-to-your-pem-key> deployment/install-java.sh deployment/start-server2.sh deployment/stop-server.sh ubuntu@<Server2-Public-IP>:~/
# Step 1: Install Java
ssh -i <path-to-your-pem-key> ubuntu@<Server2-Public-IP> "chmod +x install-java.sh start-server2.sh stop-server.sh && ./install-java.sh"
# Step 2: Start Server
ssh -i <path-to-your-pem-key> ubuntu@<Server2-Public-IP> "./start-server2.sh"

# Server 3
scp -i <path-to-your-pem-key> server-v2/build/libs/server-v2-0.0.1-SNAPSHOT.jar ubuntu@<Server3-Public-IP>:~/
scp -i <path-to-your-pem-key> deployment/install-java.sh deployment/start-server3.sh deployment/stop-server.sh ubuntu@<Server3-Public-IP>:~/
# Step 1: Install Java
ssh -i <path-to-your-pem-key> ubuntu@<Server3-Public-IP> "chmod +x install-java.sh start-server3.sh stop-server.sh && ./install-java.sh"
# Step 2: Start Server
ssh -i <path-to-your-pem-key> ubuntu@<Server3-Public-IP> "./start-server3.sh"

# Server 4
scp -i <path-to-your-pem-key> server-v2/build/libs/server-v2-0.0.1-SNAPSHOT.jar ubuntu@<Server4-Public-IP>:~/
scp -i <path-to-your-pem-key> deployment/install-java.sh deployment/start-server4.sh deployment/stop-server.sh ubuntu@<Server4-Public-IP>:~/
# Step 1: Install Java
ssh -i <path-to-your-pem-key> ubuntu@<Server4-Public-IP> "chmod +x install-java.sh start-server4.sh stop-server.sh && ./install-java.sh"
# Step 2: Start Server
ssh -i <path-to-your-pem-key> ubuntu@<Server4-Public-IP> "./start-server4.sh"
```

---

### Step 3: Build & Deploy & Start Consumer

```bash
cd consumer-v3
./gradlew clean build -x test
cd ..

scp -i <path-to-your-pem-key> consumer-v3/build/libs/consumer-v3-0.0.1-SNAPSHOT.jar ubuntu@<Consumer-Public-IP>:~/
scp -i <path-to-your-pem-key> deployment/install-java.sh deployment/start-consumer.sh deployment/stop-consumer.sh ubuntu@<Consumer-Public-IP>:~/
# Step 1: Install Java
ssh -i <path-to-your-pem-key> ubuntu@<Consumer-Public-IP> "chmod +x install-java.sh start-consumer.sh stop-consumer.sh && ./install-java.sh"
# Step 2: Start Consumer
ssh -i <path-to-your-pem-key> ubuntu@<Consumer-Public-IP> "./start-consumer.sh"
```

---

### Step 4: Verify

```bash
curl http://<Server1-Public-IP>:8080/health
curl http://<Server2-Public-IP>:8080/health
curl http://<Server3-Public-IP>:8080/health
curl http://<Server4-Public-IP>:8080/health
curl http://<Consumer-Public-IP>:8081/health
```

Postgres Connection: jdbc:postgresql://54.245.187.34:5432/chatflow (User: admin / PWD: admin / DB: chatflow)
RabbitMQ Management Console: http://\<RabbitMQ-Public-IP\>:15672 (User: admin / PWD: admin)

---

### Monitoring

```bash
# one-time snapshot
cd monitoring
./check-metrics.sh

# live (requires: brew install watch)
./watch-metrics.sh
```

---

### Stop All Services

```bash
# Server 1
ssh -i <path-to-your-pem-key> ubuntu@<Server1-Public-IP> "./stop-server.sh"
# Server 2
ssh -i <path-to-your-pem-key> ubuntu@<Server2-Public-IP> "./stop-server.sh"
# Server 3
ssh -i <path-to-your-pem-key> ubuntu@<Server3-Public-IP> "./stop-server.sh"
# Server 4
ssh -i <path-to-your-pem-key> ubuntu@<Server4-Public-IP> "./stop-server.sh"
# Consumer
ssh -i <path-to-your-pem-key> ubuntu@<Consumer-Public-IP> "./stop-consumer.sh"
# Postgres
ssh -i <path-to-your-pem-key> ubuntu@<Postgres-Public-IP> "docker-compose down -v"
# RabbitMQ
ssh -i <path-to-your-pem-key> ubuntu@<RabbitMQ-Public-IP> "docker-compose down -v"
```
