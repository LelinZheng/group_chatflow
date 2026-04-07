package edu.northeastern.cs6650.server.controller;

import edu.northeastern.cs6650.server.dto.QueueMessage;
import edu.northeastern.cs6650.server.handler.ChatWebSocketHandler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@RestController
@RequestMapping("/internal")
public class BroadcastController {

  private static final Logger log = LoggerFactory.getLogger(BroadcastController.class);
  private static final String SECRET_HEADER = "X-Internal-Secret";

  private final ChatWebSocketHandler handler;
  private final ObjectMapper mapper;
  private final ExecutorService broadcastPool = Executors.newFixedThreadPool(20);

  @Value("${internal.secret:secret}")
  private String internalSecret;

  public BroadcastController(ChatWebSocketHandler handler) {
    this.handler = handler;
    this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @PostMapping("/broadcast")
  public ResponseEntity<String> broadcast(
      @RequestHeader(SECRET_HEADER) String secret,
      @RequestBody QueueMessage msg) {

    if (!internalSecret.equals(secret)) {
      return ResponseEntity.status(403).body("Forbidden");
    }

    return sendToSessions(List.of(msg));
  }

  @PostMapping("/broadcast/batch")
  public ResponseEntity<String> broadcastBatch(
      @RequestHeader(SECRET_HEADER) String secret,
      @RequestBody List<QueueMessage> msgs) {

    if (!internalSecret.equals(secret)) {
      return ResponseEntity.status(403).body("Forbidden");
    }

    return sendToSessions(msgs);
  }

  private ResponseEntity<String> sendToSessions(List<QueueMessage> msgs) {
    AtomicInteger total = new AtomicInteger();

    for (QueueMessage msg : msgs) {
      CopyOnWriteArrayList<WebSocketSession> sessions =
          handler.getSessionsForRoom(msg.getRoomId());
      if (sessions.isEmpty()) {
        continue;
      }

      String json;
      try {
        json = mapper.writeValueAsString(msg);
      } catch (Exception e) {
        log.warn("[BROADCAST_FAIL] serialize error room={}", msg.getRoomId());
        continue;
      }

      TextMessage frame = new TextMessage(json);
      for (WebSocketSession session : sessions) {
        broadcastPool.submit(() -> {
          try {
            if (session.isOpen()) {
              synchronized (session) {
                session.sendMessage(frame);
              }
            }
          } catch (Exception e) {
            log.warn("[BROADCAST_FAIL] session={} error={}", session.getId(), e.getMessage());
          }
        });
        total.incrementAndGet();
      }
    }

    log.info("[BROADCAST_BATCH] msgs={} queued={}", msgs.size(), total.get());
    return ResponseEntity.ok("queued=" + total.get());
  }
}