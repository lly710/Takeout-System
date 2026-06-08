package com.example.takeout.rider;

import com.example.takeout.common.db.PlatformDataRepository;
import com.example.takeout.common.dto.DeliveryTrackingSnapshot;
import com.example.takeout.common.dto.RiderLocationUpdateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 配送轨迹存储服务。
 * 它负责把骑手定位结果写入 MySQL，并同步一份到 Redis，供实时轨迹查询和 WebSocket 推送使用。
 */
@Service
public class DeliveryTrackingStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformDataRepository repository;

    public DeliveryTrackingStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, PlatformDataRepository repository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    public DeliveryTrackingSnapshot updateLocation(RiderLocationUpdateRequest request) {
        DeliveryTrackingSnapshot snapshot = repository.saveRiderLocation(request);
        saveToRedis(snapshot);
        return snapshot;
    }

    public DeliveryTrackingSnapshot getTrackingSnapshot(Long orderId) {
        DeliveryTrackingSnapshot snapshot = repository.getTrackingSnapshot(orderId);
        saveToRedis(snapshot);
        return snapshot;
    }

    private void saveToRedis(DeliveryTrackingSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(
                    "rider:location:" + snapshot.riderLocation().riderId(),
                    objectMapper.writeValueAsString(snapshot.riderLocation())
            );
            redisTemplate.opsForValue().set(
                    "order:track:" + snapshot.orderId(),
                    objectMapper.writeValueAsString(snapshot.trackPoints())
            );
        } catch (JsonProcessingException ignored) {
        } catch (Exception ignored) {
            // Keep the MySQL-backed result even if Redis is unavailable.
        }
    }
}
