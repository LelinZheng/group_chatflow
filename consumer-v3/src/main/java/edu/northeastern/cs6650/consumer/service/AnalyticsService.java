package edu.northeastern.cs6650.consumer.service;

import edu.northeastern.cs6650.consumer.dto.IdCount;
import edu.northeastern.cs6650.consumer.dto.MessageRecord;
import edu.northeastern.cs6650.consumer.dto.RoomActivity;
import edu.northeastern.cs6650.consumer.dto.TimeBucketCount;
import edu.northeastern.cs6650.consumer.util.AnalyticsCache;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

  private static final RowMapper<MessageRecord> MESSAGE_MAPPER = (rs, i) -> new MessageRecord(
      rs.getString("message_id"),
      rs.getString("room_id"),
      rs.getString("user_id"),
      rs.getString("username"),
      rs.getString("message"),
      rs.getString("message_type"),
      rs.getString("server_id"),
      rs.getString("client_ip"),
      toInstant(rs.getTimestamp("created_at"))
  );

  private static final RowMapper<RoomActivity> ROOM_ACTIVITY_MAPPER = (rs, i) ->
      new RoomActivity(rs.getString("room_id"), toInstant(rs.getTimestamp("last_activity")));

  private static final RowMapper<IdCount> ID_COUNT_MAPPER = (rs, i) ->
      new IdCount(rs.getString("id"), rs.getLong("cnt"));

  private static final RowMapper<TimeBucketCount> BUCKET_MAPPER = (rs, i) ->
      new TimeBucketCount(toInstant(rs.getTimestamp("bucket")), rs.getLong("cnt"));

  private final JdbcTemplate jdbcTemplate;
  private final AnalyticsCache cache;

  public AnalyticsService(JdbcTemplate jdbcTemplate,
                          @Value("${analytics.cache-ttl-ms:5000}") long ttlMs) {
    this.jdbcTemplate = jdbcTemplate;
    this.cache = new AnalyticsCache(ttlMs);
  }

  public List<MessageRecord> getMessagesForRoom(String roomId, Instant start, Instant end, int limit) {
    String key = "room:" + roomId + ":" + start + ":" + end + ":" + limit;
    return cache.get(key, () -> jdbcTemplate.query("""
        SELECT message_id, room_id, user_id, username, message, message_type, server_id, client_ip, created_at
        FROM messages
        WHERE room_id = ? AND created_at BETWEEN ? AND ?
        ORDER BY created_at
        LIMIT ?
        """, MESSAGE_MAPPER, roomId, ts(start), ts(end), limit));
  }

  public List<MessageRecord> getUserHistory(String userId, Instant start, Instant end, int limit) {
    String key = "user:" + userId + ":" + start + ":" + end + ":" + limit;
    return cache.get(key, () -> jdbcTemplate.query("""
        SELECT message_id, room_id, user_id, username, message, message_type, server_id, client_ip, created_at
        FROM messages
        WHERE user_id = ? AND created_at BETWEEN ? AND ?
        ORDER BY created_at
        LIMIT ?
        """, MESSAGE_MAPPER, userId, ts(start), ts(end), limit));
  }

  public long countActiveUsers(Instant start, Instant end) {
    String key = "activeUsers:" + start + ":" + end;
    Long val = cache.get(key, () -> jdbcTemplate.queryForObject("""
        SELECT COUNT(DISTINCT user_id) AS cnt
        FROM messages
        WHERE created_at BETWEEN ? AND ?
        """, Long.class, ts(start), ts(end)));
    return val == null ? 0 : val;
  }

  public List<RoomActivity> getRoomsForUser(String userId) {
    String key = "roomsForUser:" + userId;
    return cache.get(key, () -> jdbcTemplate.query("""
        SELECT room_id, MAX(created_at) AS last_activity
        FROM messages
        WHERE user_id = ?
        GROUP BY room_id
        ORDER BY last_activity DESC
        """, ROOM_ACTIVITY_MAPPER, userId));
  }

  public List<TimeBucketCount> messagesPerMinute(Instant start, Instant end) {
    String key = "perMinute:" + start + ":" + end;
    return cache.get(key, () -> jdbcTemplate.query("""
        SELECT date_trunc('minute', created_at) AS bucket, COUNT(*) AS cnt
        FROM messages
        WHERE created_at BETWEEN ? AND ?
        GROUP BY bucket
        ORDER BY bucket
        """, BUCKET_MAPPER, ts(start), ts(end)));
  }

  public List<TimeBucketCount> messagesPerSecond(Instant start, Instant end) {
    String key = "perSecond:" + start + ":" + end;
    return cache.get(key, () -> jdbcTemplate.query("""
        SELECT date_trunc('second', created_at) AS bucket, COUNT(*) AS cnt
        FROM messages
        WHERE created_at BETWEEN ? AND ?
        GROUP BY bucket
        ORDER BY bucket
        """, BUCKET_MAPPER, ts(start), ts(end)));
  }

  public List<IdCount> topUsers(Instant start, Instant end, int limit) {
    String key = "topUsers:" + start + ":" + end + ":" + limit;
    return cache.get(key, () -> jdbcTemplate.query("""
        SELECT user_id AS id, COUNT(*) AS cnt
        FROM messages
        WHERE created_at BETWEEN ? AND ?
        GROUP BY user_id
        ORDER BY cnt DESC
        LIMIT ?
        """, ID_COUNT_MAPPER, ts(start), ts(end), limit));
  }

  public List<IdCount> topRooms(Instant start, Instant end, int limit) {
    String key = "topRooms:" + start + ":" + end + ":" + limit;
    return cache.get(key, () -> jdbcTemplate.query("""
        SELECT room_id AS id, COUNT(*) AS cnt
        FROM messages
        WHERE created_at BETWEEN ? AND ?
        GROUP BY room_id
        ORDER BY cnt DESC
        LIMIT ?
        """, ID_COUNT_MAPPER, ts(start), ts(end), limit));
  }

  public List<IdCount> participationByRoom(String userId, Instant start, Instant end) {
    String key = "participation:" + userId + ":" + start + ":" + end;
    return cache.get(key, () -> jdbcTemplate.query("""
        SELECT room_id AS id, COUNT(*) AS cnt
        FROM messages
        WHERE user_id = ? AND created_at BETWEEN ? AND ?
        GROUP BY room_id
        ORDER BY cnt DESC
        """, ID_COUNT_MAPPER, userId, ts(start), ts(end)));
  }

  private static Timestamp ts(Instant instant) {
    return Timestamp.from(instant);
  }

  private static Instant toInstant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
