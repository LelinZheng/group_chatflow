#!/bin/bash
# ==================================================
# Usage: run this script on the respective EC2 instance
# RabbitMQ : 172.31.26.68
# Consumer : 172.31.17.225
# Server1  : 172.31.31.34
# Server2  : 172.31.18.129
# Server3  : 172.31.17.217
# Server4  : 172.31.23.85
# ==================================================


# ==================================================
# Server4 — run on 172.31.23.85
# ==================================================
nohup java -jar ~/server-v2-0.0.1-SNAPSHOT.jar \
  --spring.rabbitmq.host=172.31.26.68 \
  --spring.rabbitmq.port=5672 \
  --spring.rabbitmq.username=admin \
  --spring.rabbitmq.password=admin \
  --server.url=http://172.31.23.85:8080 \
  --consumer.url=http://172.31.17.225:8081 \
  --rabbitmq.pool.connections=2 \
  --rabbitmq.pool.channels-per-connection=25 \
  > server.log 2>&1 &

echo $! > server.pid