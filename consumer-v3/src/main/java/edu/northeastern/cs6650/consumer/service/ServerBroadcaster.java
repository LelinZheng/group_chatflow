package edu.northeastern.cs6650.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.northeastern.cs6650.consumer.dto.QueueMessage;
import edu.northeastern.cs6650.consumer.dto.ServerGroup;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ServerBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(ServerBroadcaster.class);

  private final SessionRegistry registry;

  @Value("${internal.secret:secret}")
  private String internalSecret;

  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3))
      .build();

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public final AtomicLong broadcastSuccess = new AtomicLong();
  public final AtomicLong broadcastFailure = new AtomicLong();
  public final AtomicLong broadcastSkipped = new AtomicLong();

  public ServerBroadcaster(SessionRegistry registry) {
    this.registry = registry;
  }

  public void broadcast(QueueMessage msg) {
    broadcastBatch(List.of(msg));
  }

  public void broadcastBatch(List<QueueMessage> msgs) {
    if (msgs.isEmpty()) {
      return;
    }

    String roomId = msgs.get(0).getRoomId();
    Map<String, ServerGroup> groups = registry.getGroupedByServer(roomId);

    if (groups.isEmpty()) {
      broadcastSkipped.addAndGet(msgs.size());
      log.debug("[BROADCAST_SKIP] room={} no sessions", roomId);
      return;
    }

    byte[] body;
    try {
      body = mapper.writeValueAsBytes(msgs);
    } catch (Exception e) {
      log.error("[BROADCAST_FAIL] serialize error: {}", e.getMessage());
      return;
    }

    for (Map.Entry<String, ServerGroup> entry : groups.entrySet()) {
      String url = entry.getValue().getServerUrl() + "/internal/broadcast/batch";
      try {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("X-Internal-Secret", internalSecret)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
          broadcastSuccess.addAndGet(msgs.size());
          log.debug("[BROADCAST_BATCH] room={} server={} msgs={}",
              roomId, entry.getKey(), msgs.size());
        } else {
          broadcastFailure.addAndGet(msgs.size());
          log.warn("[BROADCAST_BATCH_FAIL] room={} server={} status={}",
              roomId, entry.getKey(), resp.statusCode());
        }
      } catch (Exception e) {
        broadcastFailure.addAndGet(msgs.size());
        log.error("[BROADCAST_BATCH_FAIL] room={} server={} error={}",
            roomId, entry.getKey(), e.getMessage());
      }
    }
  }
}