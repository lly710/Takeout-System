package com.example.takeout.common.db;

import com.example.takeout.common.cache.RedisCacheService;
import com.example.takeout.common.auth.UserRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.takeout.common.dto.Coordinate;
import com.example.takeout.common.dto.DeliveryTrackPoint;
import com.example.takeout.common.dto.DeliveryTrackingSnapshot;
import com.example.takeout.common.dto.MenuItem;
import com.example.takeout.common.dto.MerchantSummary;
import com.example.takeout.common.dto.OrderSummary;
import com.example.takeout.common.dto.RiderLocationSnapshot;
import com.example.takeout.common.dto.RiderLocationUpdateRequest;
import com.example.takeout.common.enums.OrderStatus;
import com.example.takeout.common.exception.ServiceException;
import com.example.takeout.common.mock.MockPlatformData;
import com.example.takeout.common.util.PasswordUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 平台核心数据仓库，统一承接用户、商家、订单、骑手和配送轨迹的数据库读写。
 */
@Repository
public class PlatformDataRepository {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TRACK_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Duration MERCHANT_CACHE_TTL = Duration.ofMinutes(10);
    private static final String MERCHANT_CACHE_PREFIX = "takeout:merchant:v2:";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;
    private final RedisCacheService redisCacheService;

    public PlatformDataRepository(JdbcTemplate jdbcTemplate, RedisCacheService redisCacheService) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisCacheService = redisCacheService;
    }

    public Map<String, Object> registerUser(Map<String, Object> body) {
        String phone = requiredText(body.get("phone"), "请输入手机号");
        String username = stringValue(body.get("username"), phone);
        ensureUserDoesNotExist(phone, username);

        String name = stringValue(firstNonNull(body.get("nickname"), body.get("name")), "外卖用户");
        String password = requiredText(body.get("password"), "请输入密码");
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into t_user(
                        name, username, phone, password_hash, avatar_url, balance,
                        current_longitude, current_latitude, address, status, created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, username);
            statement.setString(3, phone);
            statement.setString(4, PasswordUtils.hash(password));
            statement.setString(5, stringValue(body.get("avatarUrl"), ""));
            statement.setBigDecimal(6, new BigDecimal("100.00"));
            statement.setDouble(7, doubleValue(body.get("longitude"), 116.410800));
            statement.setDouble(8, doubleValue(body.get("latitude"), 39.920200));
            statement.setString(9, stringValue(body.get("address"), "北京市朝阳区"));
            statement.setString(10, "ACTIVE");
            statement.setTimestamp(11, Timestamp.valueOf(now));
            statement.setTimestamp(12, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);

        Long userId = longValue(keyHolder.getKey(), 0L);
        upsertAddress(userId, Map.of(
                "receiverName", name,
                "receiverPhone", phone,
                "tag", "家",
                "detailAddress", stringValue(body.get("address"), "北京朝阳"),
                "houseNumber", stringValue(body.get("houseNumber"), "A座"),
                "longitude", doubleValue(body.get("longitude"), 116.410800),
                "latitude", doubleValue(body.get("latitude"), 39.920200),
                "isDefault", true
        ));
        return getUserProfile(userId);
    }

    public Map<String, Object> loginUser(Map<String, Object> body) {
        String account = stringValue(firstNonNull(body.get("account"), body.get("phone"), body.get("username")), "");
        String password = requiredText(body.get("password"), "请输入密码");
        Map<String, Object> row = queryForSingle("""
                select id, name, username, phone, password_hash, avatar_url, current_longitude, current_latitude, address
                from t_user
                where phone = ? or username = ?
                """, account, account);
        if (row == null || !PasswordUtils.matches(password, stringValue(row.get("password_hash"), ""))) {
            throw new ServiceException(401, "账号或密码不正确");
        }
        return toUserProfile(row);
    }

    public Map<String, Object> loginUserByPhone(String phone) {
        String account = requiredText(phone, "请输入手机号").trim();
        Map<String, Object> row = queryForSingle("""
                select id, name, username, phone, password_hash, avatar_url, current_longitude, current_latitude, address
                from t_user
                where phone = ?
                """, account);
        if (row == null) {
            throw new ServiceException(404, "用户不存在");
        }
        return toUserProfile(row);
    }

    public Map<String, Object> resetUserPassword(String phone, String password) {
        String normalizedPhone = requiredText(phone, "请输入手机号").trim();
        String normalizedPassword = requiredText(password, "请输入密码");
        int affected = jdbcTemplate.update("""
                update t_user
                set password_hash = ?, updated_at = ?
                where phone = ?
                """, PasswordUtils.hash(normalizedPassword), Timestamp.valueOf(LocalDateTime.now()), normalizedPhone);
        if (affected == 0) {
            throw new ServiceException(404, "用户不存在");
        }
        return loginUserByPhone(normalizedPhone);
    }

    public Map<String, Object> loginMerchant(Map<String, Object> body) {
        String account = stringValue(firstNonNull(body.get("account"), body.get("phone")), "");
        String password = requiredText(body.get("password"), "请输入密码");
        Map<String, Object> row;
        try {
            row = queryForSingle("""
                    select id, name, phone, password_hash, category, status
                    from t_merchant
                    where phone = ?
                    """, account);
        } catch (DataAccessException exception) {
            return loginMerchantFromLegacySchema(account, password);
        }
        if (row == null || !PasswordUtils.matches(password, stringValue(row.get("password_hash"), ""))) {
            throw new ServiceException(401, "商家账号或密码不正确");
        }
        return Map.of(
                "merchantId", longValue(row.get("id"), 0L),
                "name", stringValue(row.get("name"), ""),
                "phone", stringValue(row.get("phone"), ""),
                "category", stringValue(row.get("category"), ""),
                "status", stringValue(row.get("status"), "")
        );
    }

    public Map<String, Object> loginRider(Map<String, Object> body) {
        String phone = requiredText(firstNonNull(body.get("account"), body.get("phone")), "请输入手机号").trim();
        String password = requiredText(body.get("password"), "请输入密码");
        Map<String, Object> row;
        try {
            row = queryForSingle("""
                    select id, name, phone, password_hash, status, current_longitude, current_latitude
                    from t_rider
                    where phone = ?
                    """, phone);
        } catch (DataAccessException exception) {
            return loginRiderFromLegacySchema(phone, password);
        }
        if (row == null || !PasswordUtils.matches(password, stringValue(row.get("password_hash"), ""))) {
            throw new ServiceException(401, "骑手账号或密码不正确");
        }
        return linkedMap(
                "riderId", longValue(row.get("id"), 0L),
                "name", stringValue(row.get("name"), ""),
                "phone", stringValue(row.get("phone"), ""),
                "status", stringValue(row.get("status"), ""),
                "longitude", doubleValue(row.get("current_longitude"), 0.0),
                "latitude", doubleValue(row.get("current_latitude"), 0.0)
        );
    }

    public Map<String, Object> loginAdmin(Map<String, Object> body) {
        String username = requiredText(firstNonNull(body.get("account"), body.get("username")), "请输入用户名").trim();
        String password = requiredText(body.get("password"), "请输入密码");
        Map<String, Object> row = queryForSingle("""
                select id, username, display_name, password_hash, status
                from t_admin_user
                where username = ?
                """, username);
        if (row == null || !PasswordUtils.matches(password, stringValue(row.get("password_hash"), ""))) {
            throw new ServiceException(401, "管理员账号或密码不正确");
        }
        return Map.of(
                "adminId", longValue(row.get("id"), 0L),
                "username", stringValue(row.get("username"), ""),
                "displayName", stringValue(row.get("display_name"), ""),
                "status", stringValue(row.get("status"), "")
        );
    }

    public UserRole roleOf(String scope) {
        return switch (scope) {
            case "merchant" -> UserRole.MERCHANT;
            case "rider" -> UserRole.RIDER;
            case "admin" -> UserRole.ADMIN;
            default -> UserRole.USER;
        };
    }

    public Map<String, Object> getUserProfile(Long userId) {
        Map<String, Object> row = queryForSingle("""
                select id, name, username, phone, avatar_url, balance,
                       current_longitude, current_latitude, address, status
                from t_user
                where id = ?
                """, userId);
        if (row == null) {
            throw new ServiceException(404, "用户不存在");
        }
        return toUserProfile(row);
    }

    public Map<String, Object> updateUserProfile(Long userId, Map<String, Object> body) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                update t_user
                set name = ?,
                    username = ?,
                    phone = ?,
                    address = ?,
                    updated_at = ?
                where id = ?
                """,
                stringValue(firstNonNull(body.get("nickname"), body.get("name")), stringValue(body.get("name"), "外卖用户")),
                stringValue(body.get("username"), "用户" + userId),
                stringValue(body.get("phone"), "13800000000"),
                stringValue(body.get("address"), "北京市"),
                Timestamp.valueOf(now),
                userId);
        return getUserProfile(userId);
    }

    public Map<String, Object> updateUserAvatar(Long userId, String avatarUrl) {
        jdbcTemplate.update("""
                update t_user
                set avatar_url = ?, updated_at = ?
                where id = ?
                """, avatarUrl, Timestamp.valueOf(LocalDateTime.now()), userId);
        return getUserProfile(userId);
    }

    public List<Map<String, Object>> listUserAddresses(Long userId) {
        return jdbcTemplate.query("""
                select id, receiver_name, receiver_phone, tag, detail_address, house_number,
                       longitude, latitude, is_default
                from t_user_address
                where user_id = ?
                order by is_default desc, id desc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "receiverName", rs.getString("receiver_name"),
                        "receiverPhone", rs.getString("receiver_phone"),
                        "tag", stringValue(rs.getString("tag"), ""),
                        "detailAddress", rs.getString("detail_address"),
                        "houseNumber", stringValue(rs.getString("house_number"), ""),
                        "longitude", rs.getDouble("longitude"),
                        "latitude", rs.getDouble("latitude"),
                        "isDefault", rs.getBoolean("is_default")
                ),
                userId);
    }

    public Map<String, Object> getDefaultAddress(Long userId) {
        Map<String, Object> row = queryForSingle("""
                select id, receiver_name, receiver_phone, tag, detail_address, house_number,
                       longitude, latitude, is_default
                from t_user_address
                where user_id = ? and is_default = true
                limit 1
                """, userId);
        if (row == null) {
            List<Map<String, Object>> addresses = listUserAddresses(userId);
            return addresses.isEmpty() ? null : addresses.get(0);
        }
        return Map.of(
                "id", longValue(row.get("id"), 0L),
                "receiverName", stringValue(row.get("receiver_name"), ""),
                "receiverPhone", stringValue(row.get("receiver_phone"), ""),
                "tag", stringValue(row.get("tag"), ""),
                "detailAddress", stringValue(row.get("detail_address"), ""),
                "houseNumber", stringValue(row.get("house_number"), ""),
                "longitude", doubleValue(row.get("longitude"), 0.0),
                "latitude", doubleValue(row.get("latitude"), 0.0),
                "isDefault", Boolean.TRUE.equals(row.get("is_default")) || "1".equals(String.valueOf(row.get("is_default")))
        );
    }

    public Map<String, Object> upsertAddress(Long userId, Map<String, Object> body) {
        boolean makeDefault = booleanValue(body.get("isDefault"), false);
        Long addressId = longValue(body.get("id"), 0L);
        boolean firstAddress = addressId <= 0 && countUserAddresses(userId) == 0;
        LocalDateTime now = LocalDateTime.now();
        if (makeDefault) {
            jdbcTemplate.update("update t_user_address set is_default = false where user_id = ?", userId);
        }
        if (addressId > 0) {
            jdbcTemplate.update("""
                    update t_user_address
                    set receiver_name = ?, receiver_phone = ?, tag = ?, detail_address = ?,
                        house_number = ?, longitude = ?, latitude = ?, is_default = ?, updated_at = ?
                    where id = ? and user_id = ?
                    """,
                    stringValue(body.get("receiverName"), "收货人"),
                    stringValue(body.get("receiverPhone"), ""),
                    stringValue(body.get("tag"), "家"),
                    stringValue(body.get("detailAddress"), ""),
                    stringValue(body.get("houseNumber"), ""),
                    doubleValue(body.get("longitude"), 116.410800),
                    doubleValue(body.get("latitude"), 39.920200),
                    makeDefault,
                    Timestamp.valueOf(now),
                    addressId,
                    userId);
        } else {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        insert into t_user_address(
                            user_id, receiver_name, receiver_phone, tag, detail_address, house_number,
                            longitude, latitude, is_default, created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, userId);
                statement.setString(2, stringValue(body.get("receiverName"), "收货人"));
                statement.setString(3, stringValue(body.get("receiverPhone"), ""));
                statement.setString(4, stringValue(body.get("tag"), "家"));
                statement.setString(5, stringValue(body.get("detailAddress"), ""));
                statement.setString(6, stringValue(body.get("houseNumber"), ""));
                statement.setDouble(7, doubleValue(body.get("longitude"), 116.410800));
                statement.setDouble(8, doubleValue(body.get("latitude"), 39.920200));
                statement.setBoolean(9, makeDefault || firstAddress);
                statement.setTimestamp(10, Timestamp.valueOf(now));
                statement.setTimestamp(11, Timestamp.valueOf(now));
                return statement;
            }, keyHolder);
            addressId = longValue(keyHolder.getKey(), 0L);
        }
        if (makeDefault || firstAddress) {
            updateUserAddressSnapshot(userId, addressId);
        }
        Long savedAddressId = addressId;
        return listUserAddresses(userId).stream()
                .filter(address -> Objects.equals(longValue(address.get("id"), 0L), savedAddressId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(404, "地址保存失败"));
    }

    public void deleteUserAddress(Long userId, Long addressId) {
        Map<String, Object> row = queryForSingle("""
                select is_default from t_user_address where id = ? and user_id = ?
                """, addressId, userId);
        if (row == null) {
            return;
        }
        jdbcTemplate.update("delete from t_user_address where id = ? and user_id = ?", addressId, userId);
        if (booleanValue(row.get("is_default"), false)) {
            List<Map<String, Object>> remaining = listUserAddresses(userId);
            if (!remaining.isEmpty()) {
                setDefaultAddress(userId, longValue(remaining.get(0).get("id"), 0L));
            }
        }
    }

    public Map<String, Object> setDefaultAddress(Long userId, Long addressId) {
        jdbcTemplate.update("update t_user_address set is_default = false where user_id = ?", userId);
        jdbcTemplate.update("update t_user_address set is_default = true, updated_at = ? where id = ? and user_id = ?",
                Timestamp.valueOf(LocalDateTime.now()), addressId, userId);
        updateUserAddressSnapshot(userId, addressId);
        return getDefaultAddress(userId);
    }

    public Map<String, Object> updateUserLocation(Map<String, Object> body) {
        Long userId = longValue(body.get("userId"), 1L);
        double longitude = doubleValue(body.get("longitude"), 116.410800);
        double latitude = doubleValue(body.get("latitude"), 39.920200);
        String address = stringValue(body.get("address"), "北京市");
        jdbcTemplate.update("""
                update t_user
                set current_longitude = ?, current_latitude = ?, address = ?, updated_at = ?
                where id = ?
                """, longitude, latitude, address, Timestamp.valueOf(LocalDateTime.now()), userId);
        return Map.of(
                "userId", userId,
                "longitude", longitude,
                "latitude", latitude,
                "address", address
        );
    }

    public List<Map<String, Object>> listMerchantCategories() {
        return redisCacheService.getOrLoad(
                MERCHANT_CACHE_PREFIX + "categories",
                new TypeReference<>() {
                },
                MERCHANT_CACHE_TTL,
                () -> jdbcTemplate.query("""
                select distinct category
                from t_merchant
                where status = 'ACTIVE'
                order by category asc
                """, (rs, rowNum) -> Map.of("name", rs.getString("category"))));
    }

    public List<MerchantSummary> listNearbyMerchants() {
        return redisCacheService.getOrLoad(
                MERCHANT_CACHE_PREFIX + "nearby:summary",
                new TypeReference<>() {
                },
                MERCHANT_CACHE_TTL,
                () -> {
                    List<Map<String, Object>> merchants = listNearbyMerchantRows(null, null, null, null, null);
                    if (merchants.isEmpty()) {
                        return MockPlatformData.merchants();
                    }
                    List<MerchantSummary> result = new ArrayList<>();
                    for (Map<String, Object> row : merchants) {
                        result.add(toMerchantSummary(row));
                    }
                    return result;
                });
    }

    public List<Map<String, Object>> listNearbyMerchants(String keyword, String category) {
        return listNearbyMerchants(keyword, category, null, null, null);
    }

    public List<Map<String, Object>> listNearbyMerchants(String keyword, String category, Double longitude, Double latitude, Double radiusKm) {
        String key = MERCHANT_CACHE_PREFIX + "nearby:list:"
                + cachePart(keyword) + ":" + cachePart(category) + ":"
                + cachePart(roundCoordinate(longitude)) + ":" + cachePart(roundCoordinate(latitude)) + ":"
                + cachePart(radiusKm == null ? null : String.format("%.1f", radiusKm));
        return redisCacheService.getOrLoad(
                key,
                new TypeReference<>() {
                },
                MERCHANT_CACHE_TTL,
                () -> listNearbyMerchantsFromDb(keyword, category, longitude, latitude, radiusKm));
    }

    private List<Map<String, Object>> listNearbyMerchantsFromDb(String keyword, String category, Double longitude, Double latitude, Double radiusKm) {
        List<Map<String, Object>> merchants = listNearbyMerchantRows(keyword, category, longitude, latitude, radiusKm);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : merchants) {
            Long merchantId = longValue(row.get("id"), 0L);
            result.add(linkedMap(
                    "id", merchantId,
                    "name", stringValue(row.get("name"), ""),
                    "category", stringValue(row.get("category"), ""),
                    "address", stringValue(row.get("address"), ""),
                    "distanceKm", doubleValue(row.get("distance_km"), 0.0),
                    "status", stringValue(row.get("status"), ""),
                    "minOrderAmount", decimalValue(row.get("min_order_amount"), BigDecimal.ZERO),
                    "deliveryFee", decimalValue(row.get("delivery_fee"), BigDecimal.ZERO),
                    "deliveryMode", stringValue(row.get("delivery_mode"), "FAST"),
                    "deliveryAreaType", stringValue(row.get("delivery_area_type"), "RADIUS"),
                    "deliveryRadiusKm", merchantDeliveryRadiusKm(row),
                    "estimatedDeliveryMinutes", intValue(row.get("estimated_delivery_minutes"), 0),
                    "avgScore", decimalValue(row.get("avg_score"), BigDecimal.ZERO),
                    "monthlySales", intValue(row.get("monthly_sales"), 0),
                    "notice", stringValue(row.get("notice"), ""),
                    "imageUrl", stringValue(row.get("image_url"), ""),
                    "coordinate", new Coordinate(
                            doubleValue(row.get("longitude"), 0.0),
                            doubleValue(row.get("latitude"), 0.0),
                            stringValue(row.get("name"), "")
                    ),
                    "menu", listMerchantMenuDetails(merchantId)
            ));
        }
        return result;
    }

    public MerchantSummary firstMerchant() {
        return listNearbyMerchants().stream().findFirst().orElse(MockPlatformData.firstMerchant());
    }

    public Map<String, Object> getMerchantDetail(Long merchantId) {
        return redisCacheService.getOrLoad(
                MERCHANT_CACHE_PREFIX + "detail:" + merchantId,
                new TypeReference<>() {
                },
                MERCHANT_CACHE_TTL,
                () -> getMerchantDetailFromDb(merchantId));
    }

    private Map<String, Object> getMerchantDetailFromDb(Long merchantId) {
        Map<String, Object> row = queryForSingle("""
                select id, name, owner_name, phone, business_hours, category, address, notice, image_url,
                       longitude, latitude, distance_km, min_order_amount, delivery_fee,
                       delivery_mode, delivery_area_type, delivery_radius_km, delivery_polygon,
                       estimated_delivery_minutes, avg_score, monthly_sales, status
                from t_merchant
                where id = ?
                """, merchantId);
        if (row == null) {
            throw new ServiceException(404, "商家不存在");
        }
        return linkedMap(
                "id", merchantId,
                "name", stringValue(row.get("name"), ""),
                "ownerName", stringValue(row.get("owner_name"), ""),
                "phone", stringValue(row.get("phone"), ""),
                "businessHours", stringValue(row.get("business_hours"), ""),
                "category", stringValue(row.get("category"), ""),
                "address", stringValue(row.get("address"), ""),
                "notice", stringValue(row.get("notice"), ""),
                "imageUrl", stringValue(row.get("image_url"), ""),
                "distanceKm", doubleValue(row.get("distance_km"), 0.0),
                "longitude", doubleValue(row.get("longitude"), 0.0),
                "latitude", doubleValue(row.get("latitude"), 0.0),
                "status", stringValue(row.get("status"), ""),
                "minOrderAmount", decimalValue(row.get("min_order_amount"), BigDecimal.ZERO),
                "deliveryFee", decimalValue(row.get("delivery_fee"), BigDecimal.ZERO),
                "deliveryMode", stringValue(row.get("delivery_mode"), "FAST"),
                "deliveryAreaType", stringValue(row.get("delivery_area_type"), "RADIUS"),
                "deliveryRadiusKm", merchantDeliveryRadiusKm(row),
                "deliveryPolygon", stringValue(row.get("delivery_polygon"), ""),
                "estimatedDeliveryMinutes", intValue(row.get("estimated_delivery_minutes"), 0),
                "avgScore", decimalValue(row.get("avg_score"), BigDecimal.ZERO),
                "monthlySales", intValue(row.get("monthly_sales"), 0),
                "coordinate", new Coordinate(
                        doubleValue(row.get("longitude"), 0.0),
                        doubleValue(row.get("latitude"), 0.0),
                        stringValue(row.get("name"), "")
                ),
                "menu", listMerchantMenuDetails(merchantId)
        );
    }

    public List<MenuItem> listMenuItemsByMerchantId(Long merchantId) {
        return redisCacheService.getOrLoad(
                MERCHANT_CACHE_PREFIX + "menu-items:" + merchantId,
                new TypeReference<>() {
                },
                MERCHANT_CACHE_TTL,
                () -> jdbcTemplate.query("""
                select id, name, price, monthly_sales
                from t_menu_item
                where merchant_id = ? and status = 'ON_SALE'
                order by monthly_sales desc, id asc
                """,
                (rs, rowNum) -> new MenuItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getInt("monthly_sales")
                ),
                merchantId));
    }

    public List<Map<String, Object>> listMerchantMenuDetails(Long merchantId) {
        return redisCacheService.getOrLoad(
                MERCHANT_CACHE_PREFIX + "menu-details:" + merchantId,
                new TypeReference<>() {
                },
                MERCHANT_CACHE_TTL,
                () -> jdbcTemplate.query("""
                select id, name, category_name, description, image_url, spec_name,
                       price, stock, monthly_sales, status
                from t_menu_item
                where merchant_id = ?
                order by category_name asc, monthly_sales desc, id asc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "name", rs.getString("name"),
                        "categoryName", stringValue(rs.getString("category_name"), ""),
                        "description", stringValue(rs.getString("description"), ""),
                        "imageUrl", stringValue(rs.getString("image_url"), ""),
                        "specName", stringValue(rs.getString("spec_name"), ""),
                        "price", rs.getBigDecimal("price"),
                        "stock", rs.getInt("stock"),
                        "monthlySales", rs.getInt("monthly_sales"),
                        "status", rs.getString("status")
                ),
                merchantId));
    }

    public Map<String, Object> getCart(Long userId, Long merchantId) {
        Long targetMerchantId = merchantId;
        if (targetMerchantId == null || targetMerchantId <= 0) {
            Map<String, Object> merchantRow = queryForSingle("""
                    select merchant_id
                    from t_cart_item
                    where user_id = ?
                    order by updated_at desc, id desc
                    limit 1
                    """, userId);
            targetMerchantId = merchantRow == null ? 0L : longValue(merchantRow.get("merchant_id"), 0L);
        }
        List<Map<String, Object>> items = jdbcTemplate.query("""
                select id, merchant_id, menu_item_id, item_name, image_url, spec_name, price, quantity, selected
                from t_cart_item
                where user_id = ? and merchant_id = ?
                order by id asc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "merchantId", rs.getLong("merchant_id"),
                        "menuItemId", rs.getLong("menu_item_id"),
                        "name", rs.getString("item_name"),
                        "imageUrl", stringValue(rs.getString("image_url"), ""),
                        "specName", stringValue(rs.getString("spec_name"), ""),
                        "price", rs.getBigDecimal("price"),
                        "quantity", rs.getInt("quantity"),
                        "selected", rs.getBoolean("selected")
                ),
                userId, targetMerchantId);
        BigDecimal itemAmount = BigDecimal.ZERO;
        BigDecimal selectedAmount = BigDecimal.ZERO;
        int totalQuantity = 0;
        for (Map<String, Object> item : items) {
            BigDecimal lineAmount = decimalValue(item.get("price"), BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(intValue(item.get("quantity"), 0)));
            itemAmount = itemAmount.add(lineAmount);
            totalQuantity += intValue(item.get("quantity"), 0);
            if (booleanValue(item.get("selected"), false)) {
                selectedAmount = selectedAmount.add(lineAmount);
            }
        }
        return Map.of(
                "merchantId", targetMerchantId,
                "items", items,
                "totalQuantity", totalQuantity,
                "itemAmount", itemAmount.setScale(2, RoundingMode.HALF_UP),
                "selectedAmount", selectedAmount.setScale(2, RoundingMode.HALF_UP)
        );
    }

    public Map<String, Object> addCartItem(Long userId, Map<String, Object> body) {
        Long menuItemId = longValue(body.get("menuItemId"), 0L);
        int quantity = Math.max(1, intValue(body.get("quantity"), 1));
        Map<String, Object> menu = queryForSingle("""
                select id, merchant_id, name, image_url, spec_name, price
                from t_menu_item
                where id = ?
                """, menuItemId);
        if (menu == null) {
            throw new ServiceException(404, "菜品不存在");
        }
        Long merchantId = longValue(menu.get("merchant_id"), 0L);
        Map<String, Object> existing = queryForSingle("""
                select id, quantity
                from t_cart_item
                where user_id = ? and merchant_id = ? and menu_item_id = ?
                """, userId, merchantId, menuItemId);
        if (existing == null) {
            jdbcTemplate.update("""
                    insert into t_cart_item(
                        user_id, merchant_id, menu_item_id, item_name, image_url, spec_name,
                        price, quantity, selected, created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    userId, merchantId, menuItemId,
                    stringValue(menu.get("name"), ""),
                    stringValue(menu.get("image_url"), ""),
                    stringValue(menu.get("spec_name"), ""),
                    decimalValue(menu.get("price"), BigDecimal.ZERO),
                    quantity,
                    true,
                    Timestamp.valueOf(LocalDateTime.now()),
                    Timestamp.valueOf(LocalDateTime.now()));
        } else {
            jdbcTemplate.update("""
                    update t_cart_item
                    set quantity = ?, selected = true, updated_at = ?
                    where id = ?
                    """,
                    intValue(existing.get("quantity"), 0) + quantity,
                    Timestamp.valueOf(LocalDateTime.now()),
                    longValue(existing.get("id"), 0L));
        }
        return getCart(userId, merchantId);
    }

    public Map<String, Object> updateCartItem(Long userId, Long cartItemId, Map<String, Object> body) {
        int quantity = Math.max(1, intValue(body.get("quantity"), 1));
        boolean selected = booleanValue(body.get("selected"), true);
        jdbcTemplate.update("""
                update t_cart_item
                set quantity = ?, selected = ?, updated_at = ?
                where id = ? and user_id = ?
                """,
                quantity,
                selected,
                Timestamp.valueOf(LocalDateTime.now()),
                cartItemId,
                userId);
        Map<String, Object> row = queryForSingle("select merchant_id from t_cart_item where id = ? and user_id = ?", cartItemId, userId);
        return getCart(userId, row == null ? null : longValue(row.get("merchant_id"), 0L));
    }

    public Map<String, Object> toggleCart(Long userId, Map<String, Object> body) {
        boolean selected = booleanValue(body.get("selected"), true);
        Long merchantId = longValue(body.get("merchantId"), 0L);
        jdbcTemplate.update("""
                update t_cart_item
                set selected = ?, updated_at = ?
                where user_id = ? and merchant_id = ?
                """, selected, Timestamp.valueOf(LocalDateTime.now()), userId, merchantId);
        return getCart(userId, merchantId);
    }

    public Map<String, Object> deleteCartItem(Long userId, Long cartItemId) {
        Map<String, Object> row = queryForSingle("select merchant_id from t_cart_item where id = ? and user_id = ?", cartItemId, userId);
        Long merchantId = row == null ? null : longValue(row.get("merchant_id"), 0L);
        jdbcTemplate.update("delete from t_cart_item where id = ? and user_id = ?", cartItemId, userId);
        return getCart(userId, merchantId);
    }

    public void clearCart(Long userId, Long merchantId) {
        jdbcTemplate.update("delete from t_cart_item where user_id = ? and merchant_id = ?", userId, merchantId);
    }

    public List<Map<String, Object>> listUserCoupons(Long userId, Long merchantId) {
        return jdbcTemplate.query("""
                select uc.id as user_coupon_id, c.id as coupon_id, c.name, c.discount_amount, c.min_amount, c.description
                from t_user_coupon uc
                join t_coupon c on c.id = uc.coupon_id
                where uc.user_id = ? and uc.status = 'UNUSED'
                  and (c.merchant_id is null or c.merchant_id = ?)
                  and c.status = 'ACTIVE'
                order by c.discount_amount desc, c.id asc
                """,
                (rs, rowNum) -> Map.of(
                        "userCouponId", rs.getLong("user_coupon_id"),
                        "couponId", rs.getLong("coupon_id"),
                        "name", rs.getString("name"),
                        "discountAmount", rs.getBigDecimal("discount_amount"),
                        "minAmount", rs.getBigDecimal("min_amount"),
                        "description", stringValue(rs.getString("description"), "")
                ),
                userId, merchantId);
    }

    public List<Map<String, Object>> listUserCouponWallet(Long userId) {
        return jdbcTemplate.query("""
                select uc.id as user_coupon_id, uc.status, uc.received_at, uc.used_at, uc.order_id,
                       c.id as coupon_id, c.name, c.discount_amount, c.min_amount, c.description, c.status as coupon_status, c.merchant_id
                from t_user_coupon uc
                join t_coupon c on c.id = uc.coupon_id
                where uc.user_id = ?
                order by uc.id desc
                """,
                (rs, rowNum) -> linkedMap(
                        "userCouponId", rs.getLong("user_coupon_id"),
                        "status", stringValue(rs.getString("status"), "UNUSED"),
                        "receivedAt", stringValue(rs.getString("received_at"), ""),
                        "usedAt", stringValue(rs.getString("used_at"), ""),
                        "orderId", rs.getObject("order_id") == null ? null : rs.getLong("order_id"),
                        "couponId", rs.getLong("coupon_id"),
                        "merchantId", rs.getObject("merchant_id") == null ? null : rs.getLong("merchant_id"),
                        "name", rs.getString("name"),
                        "discountAmount", rs.getBigDecimal("discount_amount"),
                        "minAmount", rs.getBigDecimal("min_amount"),
                        "description", stringValue(rs.getString("description"), ""),
                        "couponStatus", stringValue(rs.getString("coupon_status"), "")
                ),
                userId);
    }

    public List<Map<String, Object>> listAvailableCoupons(Long userId, Long merchantId) {
        return jdbcTemplate.query("""
                select c.id as coupon_id, c.name, c.discount_amount, c.min_amount, c.description,
                       c.merchant_id, c.status as coupon_status
                from t_coupon c
                where c.status = 'ACTIVE'
                  and (c.merchant_id is null or c.merchant_id = ?)
                  and not exists (
                      select 1 from t_user_coupon uc
                      where uc.user_id = ? and uc.coupon_id = c.id
                  )
                order by c.discount_amount desc, c.id asc
                """,
                (rs, rowNum) -> linkedMap(
                        "couponId", rs.getLong("coupon_id"),
                        "merchantId", rs.getObject("merchant_id") == null ? null : rs.getLong("merchant_id"),
                        "name", rs.getString("name"),
                        "discountAmount", rs.getBigDecimal("discount_amount"),
                        "minAmount", rs.getBigDecimal("min_amount"),
                        "description", stringValue(rs.getString("description"), ""),
                        "couponStatus", stringValue(rs.getString("coupon_status"), "")
                ),
                merchantId == null ? 0L : merchantId, userId);
    }

    public Map<String, Object> claimCoupon(Long userId, Long couponId) {
        Map<String, Object> coupon = queryForSingle("""
                select id, merchant_id, name, discount_amount, min_amount, description, status
                from t_coupon
                where id = ?
                """, couponId);
        if (coupon == null) {
            throw new ServiceException(404, "优惠券不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(stringValue(coupon.get("status"), ""))) {
            throw new ServiceException(400, "优惠券当前不可领取");
        }
        Map<String, Object> already = queryForSingle("""
                select id
                from t_user_coupon
                where user_id = ? and coupon_id = ?
                """, userId, couponId);
        if (already != null) {
            return Map.of("claimed", false, "couponId", couponId, "message", "优惠券已领取");
        }
        jdbcTemplate.update("""
                insert into t_user_coupon(user_id, coupon_id, status, received_at)
                values (?, ?, 'UNUSED', ?)
                """, userId, couponId, Timestamp.valueOf(LocalDateTime.now()));
        return Map.of(
                "claimed", true,
                "couponId", couponId,
                "name", stringValue(coupon.get("name"), ""),
                "discountAmount", decimalValue(coupon.get("discount_amount"), BigDecimal.ZERO),
                "minAmount", decimalValue(coupon.get("min_amount"), BigDecimal.ZERO)
        );
    }

    public Map<String, Object> previewOrder(Long userId, Map<String, Object> body) {
        Long merchantId = longValue(body.get("merchantId"), 0L);
        List<Map<String, Object>> items = selectedCartItems(userId, merchantId);
        if (items.isEmpty()) {
            throw new ServiceException(400, "请先选择购物车商品");
        }
        Long addressId = resolveAddressId(userId, body);
        Map<String, Object> address = queryAddress(userId, addressId);
        if (address == null) {
            throw new ServiceException(400, "请选择收货地址");
        }
        Map<String, Object> merchant = getMerchantDetail(merchantId);
        ensureUserAddressInMerchantDeliveryRange(merchant, address);
        BigDecimal itemAmount = sumCartAmount(items);
        Long couponId = longValue(body.get("couponId"), 0L);
        BigDecimal deliveryFee = decimalValue(merchant.get("deliveryFee"), BigDecimal.ZERO);
        BigDecimal couponDiscount = calculateCouponDiscount(userId, couponId, itemAmount);
        BigDecimal payableAmount = itemAmount.add(deliveryFee).subtract(couponDiscount).max(BigDecimal.ZERO);
        return Map.of(
                "merchant", merchant,
                "address", address,
                "items", items,
                "availableCoupons", listUserCoupons(userId, merchantId),
                "pricing", Map.of(
                        "itemAmount", itemAmount,
                        "deliveryFee", deliveryFee,
                        "couponDiscount", couponDiscount,
                        "payableAmount", payableAmount.setScale(2, RoundingMode.HALF_UP)
                ),
                "remark", stringValue(body.get("remark"), "")
        );
    }

    public Map<String, Object> createOrder(Long userId, Map<String, Object> body) {
        Long merchantId = longValue(body.get("merchantId"), 0L);
        Map<String, Object> preview = previewOrder(userId, body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) preview.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> merchant = (Map<String, Object>) preview.get("merchant");
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) preview.get("address");
        @SuppressWarnings("unchecked")
        Map<String, Object> pricing = (Map<String, Object>) preview.get("pricing");
        Long couponId = longValue(body.get("couponId"), 0L);
        LocalDateTime now = LocalDateTime.now();
        String orderNo = "WM" + now.format(ORDER_NO_FORMATTER);
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into t_order(
                        order_no, user_id, merchant_id, rider_id, address_id, status,
                        amount, item_amount, delivery_fee, coupon_id, coupon_discount,
                        paid, payment_time, remark, receiver_name, receiver_phone,
                        delivery_address, user_longitude, user_latitude, estimated_delivery_time,
                        created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, orderNo);
            statement.setLong(2, userId);
            statement.setLong(3, merchantId);
            statement.setObject(4, null);
            statement.setLong(5, longValue(address.get("id"), 0L));
            statement.setString(6, OrderStatus.CREATED.name());
            statement.setBigDecimal(7, decimalValue(pricing.get("payableAmount"), BigDecimal.ZERO));
            statement.setBigDecimal(8, decimalValue(pricing.get("itemAmount"), BigDecimal.ZERO));
            statement.setBigDecimal(9, decimalValue(pricing.get("deliveryFee"), BigDecimal.ZERO));
            statement.setObject(10, couponId > 0 ? couponId : null);
            statement.setBigDecimal(11, decimalValue(pricing.get("couponDiscount"), BigDecimal.ZERO));
            statement.setBoolean(12, false);
            statement.setObject(13, null);
            statement.setString(14, stringValue(body.get("remark"), ""));
            statement.setString(15, stringValue(address.get("receiverName"), ""));
            statement.setString(16, stringValue(address.get("receiverPhone"), ""));
            statement.setString(17, stringValue(address.get("detailAddress"), "") + " " + stringValue(address.get("houseNumber"), ""));
            statement.setDouble(18, doubleValue(address.get("longitude"), 0.0));
            statement.setDouble(19, doubleValue(address.get("latitude"), 0.0));
            statement.setTimestamp(20, Timestamp.valueOf(now.plusMinutes(intValue(merchant.get("estimatedDeliveryMinutes"), 30))));
            statement.setTimestamp(21, Timestamp.valueOf(now));
            statement.setTimestamp(22, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);

        Long orderId = longValue(keyHolder.getKey(), 0L);
        for (Map<String, Object> item : items) {
            jdbcTemplate.update("""
                    insert into t_order_item(order_id, menu_item_id, item_name, image_url, spec_name, price, quantity)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    orderId,
                    longValue(item.get("menuItemId"), 0L),
                    stringValue(item.get("name"), ""),
                    stringValue(item.get("imageUrl"), ""),
                    stringValue(item.get("specName"), ""),
                    decimalValue(item.get("price"), BigDecimal.ZERO),
                    intValue(item.get("quantity"), 0));
        }
        if (couponId > 0) {
            jdbcTemplate.update("""
                    update t_user_coupon
                    set status = 'LOCKED', order_id = ?, used_at = ?
                    where user_id = ? and coupon_id = ? and status = 'UNUSED'
                    order by id asc
                    limit 1
                    """, orderId, Timestamp.valueOf(now), userId, couponId);
        }
        clearCart(userId, merchantId);
        return Map.of(
                "orderId", orderId,
                "orderNo", orderNo,
                "status", OrderStatus.CREATED.name(),
                "paid", false
        );
    }

    public List<OrderSummary> listUserOrders(Long userId) {
        return listOrdersBySql("""
                select id
                from t_order
                where user_id = ?
                order by created_at desc
                """, userId);
    }

    public Map<String, Object> payOrder(Long orderId) {
        jdbcTemplate.update("""
                update t_order
                set paid = true, status = ?, payment_time = ?, updated_at = ?
                where id = ?
                """, OrderStatus.PAID.name(), Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()), orderId);
        insertTrack(orderId, 0L, "支付完成", 0.0, 0.0);
        return linkedMap("orderId", orderId, "paid", true, "status", OrderStatus.PAID.name());
    }

    public Map<String, Object> cancelOrder(Long userId, Long orderId, String reason) {
        Map<String, Object> row = queryForSingle("""
                select user_id, paid, status, coupon_id
                from t_order
                where id = ?
                """, orderId);
        if (row == null || !Objects.equals(longValue(row.get("user_id"), 0L), userId)) {
            throw new ServiceException(404, "订单不存在");
        }
        if (booleanValue(row.get("paid"), false)) {
            throw new ServiceException(400, "已支付订单不能在用户端取消");
        }
        if (!OrderStatus.CREATED.name().equals(stringValue(row.get("status"), ""))) {
            throw new ServiceException(400, "只有待付款订单可以取消");
        }
        jdbcTemplate.update("""
                update t_order
                set status = ?, cancel_reason = ?, cancel_time = ?, updated_at = ?
                where id = ?
                """,
                OrderStatus.CANCELLED.name(),
                stringValue(reason, "用户取消订单"),
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                orderId);
        Long couponId = longValue(row.get("coupon_id"), 0L);
        if (couponId > 0) {
            jdbcTemplate.update("""
                    update t_user_coupon
                    set status = 'UNUSED', order_id = null
                    where order_id = ? and coupon_id = ?
                    """, orderId, couponId);
        }
        return Map.of("orderId", orderId, "status", OrderStatus.CANCELLED.name());
    }

    public List<OrderSummary> listOrdersForMerchant(Long merchantId) {
        return listOrdersBySql("""
                select id
                from t_order
                where merchant_id = ?
                order by created_at desc
                """, merchantId);
    }

    public List<OrderSummary> listAvailableOrders() {
        return listAvailableOrders(null);
    }

    public List<OrderSummary> listAvailableOrders(Long riderId) {
        String riderFilter = riderId == null ? "" : "  and (rider_id is null or rider_id = ?)\n";
        List<OrderSummary> orders = listOrdersBySql("""
                select id
                from t_order
                where paid = true
                %s
                  and status in ('PAID', 'MERCHANT_ACCEPTED', 'RIDER_ACCEPTED', 'MERCHANT_PREPARED', 'ARRIVED_STORE', 'DELIVERING')
                order by created_at desc
                """.formatted(riderFilter), riderId == null ? new Object[]{} : new Object[]{riderId});
        return riderId == null ? orders : filterEligibleRiderOrders(orders, riderId);
    }

    public List<OrderSummary> listAllOrders() {
        return listOrdersBySql("select id from t_order order by created_at desc");
    }

    public List<Map<String, Object>> listAllUsers() {
        return jdbcTemplate.query("""
                select u.id, u.name, u.phone, u.address, u.status, u.created_at,
                       count(o.id) as order_count,
                       max(o.created_at) as last_order_time
                from t_user u
                left join t_order o on o.user_id = u.id
                group by u.id, u.name, u.phone, u.address, u.status, u.created_at
                order by u.id asc
                """,
                (rs, rowNum) -> linkedMap(
                        "id", rs.getLong("id"),
                        "userId", "U" + rs.getLong("id"),
                        "name", rs.getString("name"),
                        "phone", rs.getString("phone"),
                        "address", stringValue(rs.getString("address"), ""),
                        "status", rs.getString("status"),
                        "level", userLevel(rs.getInt("order_count")),
                        "orderCount", rs.getInt("order_count"),
                        "tags", List.of(rs.getInt("order_count") > 3 ? "高频下单" : "普通用户"),
                        "violations", "BANNED".equals(rs.getString("status")) ? 1 : 0,
                        "registerTime", rs.getTimestamp("created_at") == null ? "" : rs.getTimestamp("created_at").toLocalDateTime().toString().replace("T", " "),
                        "lastOrderTime", rs.getTimestamp("last_order_time") == null ? "暂无订单" : rs.getTimestamp("last_order_time").toLocalDateTime().toString().replace("T", " ")
                ));
    }

    public List<Map<String, Object>> adminAccounts() {
        return jdbcTemplate.query("""
                select id, username, display_name, status, created_at, updated_at
                from t_admin_user
                order by id asc
                """,
                (rs, rowNum) -> linkedMap(
                        "id", rs.getLong("id"),
                        "name", stringValue(rs.getString("display_name"), rs.getString("username")),
                        "username", rs.getString("username"),
                        "role", rs.getLong("id") == 1L ? "超级管理员" : "子管理员",
                        "scope", rs.getLong("id") == 1L ? "全部模块" : "按角色授权",
                        "status", "ACTIVE".equals(rs.getString("status")) ? "启用" : "停用",
                        "lastLogin", rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toLocalDateTime().toString().replace("T", " ")
                ));
    }

    public OrderSummary getOrderSummary(Long orderId) {
        Map<String, Object> row = queryOrderBase(orderId);
        if (row == null) {
            return MockPlatformData.demoOrder();
        }
        Long merchantId = longValue(row.get("merchant_id"), 0L);
        MerchantSummary merchant = new MerchantSummary(
                merchantId,
                stringValue(row.get("merchant_name"), ""),
                stringValue(row.get("category"), ""),
                doubleValue(row.get("distance_km"), 0.0),
                new Coordinate(
                        doubleValue(row.get("merchant_longitude"), 0.0),
                        doubleValue(row.get("merchant_latitude"), 0.0),
                        stringValue(row.get("merchant_name"), "")
                ),
                listMenuItemsByMerchantId(merchantId)
        );
        Coordinate userCoordinate = new Coordinate(
                doubleValue(row.get("user_longitude"), 0.0),
                doubleValue(row.get("user_latitude"), 0.0),
                stringValue(row.get("receiver_name"), stringValue(row.get("user_name"), "User"))
        );
        Coordinate riderCoordinate = new Coordinate(
                doubleValue(row.get("rider_longitude"), merchant.coordinate().longitude()),
                doubleValue(row.get("rider_latitude"), merchant.coordinate().latitude()),
                stringValue(row.get("rider_name"), "Waiting for rider")
        );
        return new OrderSummary(
                orderId,
                stringValue(row.get("order_no"), ""),
                stringValue(row.get("user_name"), ""),
                merchant,
                userCoordinate,
                riderCoordinate,
                stringValue(row.get("rider_name"), "Waiting for rider"),
                decimalValue(row.get("amount"), BigDecimal.ZERO),
                parseOrderStatus(stringValue(row.get("status"), OrderStatus.CREATED.name())),
                listTrackPoints(orderId)
        );
    }

    public Map<String, Object> getOrderDetail(Long orderId) {
        Map<String, Object> row = queryOrderBase(orderId);
        if (row == null) {
            throw new ServiceException(404, "订单不存在");
        }
        return linkedMap(
                "summary", getOrderSummary(orderId),
                "items", listOrderItems(orderId),
                "tracking", getTrackingSnapshot(orderId),
                "pricing", Map.of(
                        "amount", decimalValue(row.get("amount"), BigDecimal.ZERO),
                        "itemAmount", decimalValue(row.get("item_amount"), BigDecimal.ZERO),
                        "deliveryFee", decimalValue(row.get("delivery_fee"), BigDecimal.ZERO),
                        "couponDiscount", decimalValue(row.get("coupon_discount"), BigDecimal.ZERO)
                ),
                "receiverName", stringValue(row.get("receiver_name"), ""),
                "receiverPhone", stringValue(row.get("receiver_phone"), ""),
                "deliveryAddress", stringValue(row.get("delivery_address"), ""),
                "remark", stringValue(row.get("remark"), ""),
                "paymentTime", timestampText(row.get("payment_time")),
                "estimatedDeliveryTime", timestampText(row.get("estimated_delivery_time")),
                "cancelReason", stringValue(row.get("cancel_reason"), "")
        );
    }

    public List<Map<String, Object>> getOrderFlow(Long orderId) {
        Map<String, Object> order = queryForSingle("""
                select created_at, payment_time, status, cancel_time
                from t_order
                where id = ?
                """, orderId);
        if (order == null) {
            return List.of();
        }
        List<Map<String, Object>> flow = new ArrayList<>();
        flow.add(Map.of(
                "code", "ORDER_CREATED",
                "label", "订单提交",
                "time", timestampText(order.get("created_at"))
        ));
        if (order.get("payment_time") != null) {
            flow.add(Map.of(
                    "code", "ORDER_PAID",
                    "label", "支付完成",
                    "time", timestampText(order.get("payment_time"))
            ));
        }
        for (DeliveryTrackPoint point : listTrackPoints(orderId)) {
            flow.add(Map.of(
                    "code", point.stage(),
                    "label", point.stage(),
                    "time", point.time()
            ));
        }
        if (parseOrderStatus(stringValue(order.get("status"), "")).equals(OrderStatus.CANCELLED)) {
            flow.add(Map.of(
                    "code", "ORDER_CANCELLED",
                    "label", "订单取消",
                    "time", timestampText(order.get("cancel_time"))
            ));
        }
        return flow;
    }

    public DeliveryTrackingSnapshot getTrackingSnapshot(Long orderId) {
        Map<String, Object> row = queryOrderBase(orderId);
        if (row == null) {
            OrderSummary demoOrder = MockPlatformData.demoOrder();
            return new DeliveryTrackingSnapshot(
                    orderId,
                    demoOrder.orderNo(),
                    demoOrder.status().name(),
                    demoOrder.riderName(),
                    demoOrder.merchant().coordinate(),
                    demoOrder.userCoordinate(),
                    new RiderLocationSnapshot(7001L, orderId,
                            demoOrder.riderCoordinate().longitude(),
                            demoOrder.riderCoordinate().latitude(),
                            demoOrder.status().name(),
                            LocalDateTime.now().toString()),
                    demoOrder.trackPoints()
            );
        }
        List<DeliveryTrackPoint> trackPoints = listTrackPoints(orderId);
        String latestStage = trackPoints.isEmpty()
                ? stringValue(row.get("status"), OrderStatus.CREATED.name())
                : trackPoints.get(trackPoints.size() - 1).stage();
        return new DeliveryTrackingSnapshot(
                orderId,
                stringValue(row.get("order_no"), ""),
                stringValue(row.get("status"), OrderStatus.CREATED.name()),
                stringValue(row.get("rider_name"), "Waiting for rider"),
                new Coordinate(
                        doubleValue(row.get("merchant_longitude"), 0.0),
                        doubleValue(row.get("merchant_latitude"), 0.0),
                        stringValue(row.get("merchant_name"), "")
                ),
                new Coordinate(
                        doubleValue(row.get("user_longitude"), 0.0),
                        doubleValue(row.get("user_latitude"), 0.0),
                        stringValue(row.get("receiver_name"), stringValue(row.get("user_name"), "User"))
                ),
                new RiderLocationSnapshot(
                        longValue(row.get("rider_id"), 7001L),
                        orderId,
                        doubleValue(row.get("rider_longitude"), 0.0),
                        doubleValue(row.get("rider_latitude"), 0.0),
                        latestStage,
                        timestampText(row.get("updated_at"))
                ),
                trackPoints
        );
    }

    public Map<String, Object> acceptMerchantOrder(Long merchantId, Long orderId, Map<String, Object> body) {
        jdbcTemplate.update("""
                update t_order
                set status = ?, updated_at = ?
                where id = ? and merchant_id = ?
                """, OrderStatus.MERCHANT_ACCEPTED.name(), Timestamp.valueOf(LocalDateTime.now()), orderId, merchantId);
        Map<String, Object> base = queryOrderBase(orderId);
        if (base != null) {
            insertTrack(orderId, longValue(base.get("rider_id"), 0L), "商家接单",
                    doubleValue(base.get("merchant_longitude"), 0.0),
                    doubleValue(base.get("merchant_latitude"), 0.0));
        }
        return Map.of("orderId", orderId, "action", stringValue(body.get("action"), "accept"));
    }

    public Map<String, Object> prepareMerchantOrder(Long merchantId, Long orderId, Map<String, Object> body) {
        jdbcTemplate.update("""
                update t_order
                set status = ?, updated_at = ?
                where id = ?
                  and merchant_id = ?
                """,
                OrderStatus.MERCHANT_PREPARED.name(),
                Timestamp.valueOf(LocalDateTime.now()),
                orderId,
                merchantId);
        Map<String, Object> base = queryOrderBase(orderId);
        if (base != null) {
            insertTrack(orderId, longValue(base.get("rider_id"), 0L), "MERCHANT_PREPARED",
                    doubleValue(base.get("merchant_longitude"), 0.0),
                    doubleValue(base.get("merchant_latitude"), 0.0));
        }
        return Map.of("orderId", orderId, "action", stringValue(body.get("action"), "prepare"));
    }

    public Map<String, Object> remindMerchantOrder(Long orderId) {
        jdbcTemplate.update("update t_order set updated_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now()), orderId);
        return Map.of("orderId", orderId, "message", "Merchant has been reminded");
    }

    public Map<String, Object> merchantShopProfile(Long merchantId) {
        return getMerchantDetail(merchantId);
    }

    public Map<String, Object> updateMerchantProfile(Long merchantId, Map<String, Object> body) {
        jdbcTemplate.update("""
                update t_merchant
                set name = ?, owner_name = ?, phone = ?, business_hours = ?, category = ?, address = ?, notice = ?,
                    min_order_amount = ?, delivery_fee = ?, estimated_delivery_minutes = ?,
                    delivery_mode = ?, delivery_area_type = 'RADIUS', delivery_radius_km = ?,
                    image_url = ?, updated_at = ?
                where id = ?
                """,
                stringValue(body.get("name"), "Merchant"),
                stringValue(body.get("ownerName"), ""),
                stringValue(body.get("phone"), ""),
                stringValue(body.get("businessHours"), ""),
                stringValue(body.get("category"), "Food"),
                stringValue(body.get("address"), ""),
                stringValue(body.get("notice"), ""),
                decimalValue(body.get("minOrderAmount"), BigDecimal.ZERO),
                decimalValue(body.get("deliveryFee"), BigDecimal.ZERO),
                intValue(body.get("estimatedDeliveryMinutes"), 30),
                stringValue(body.get("deliveryMode"), "FAST").toUpperCase(),
                decimalValue(body.get("deliveryRadiusKm"), BigDecimal.valueOf(defaultMerchantDeliveryRadiusKm(stringValue(body.get("deliveryMode"), "FAST")))),
                stringValue(body.get("imageUrl"), ""),
                Timestamp.valueOf(LocalDateTime.now()),
                merchantId);
        evictMerchantCaches(merchantId);
        return merchantShopProfile(merchantId);
    }

    public Map<String, Object> updateMerchantResolvedLocation(Long merchantId, String address, double longitude, double latitude) {
        if (longitude < -180 || longitude > 180) {
            throw new ServiceException(400, "经度不合法");
        }
        if (latitude < -90 || latitude > 90) {
            throw new ServiceException(400, "纬度不合法");
        }
        jdbcTemplate.update("""
                update t_merchant
                set address = ?, longitude = ?, latitude = ?, updated_at = ?
                where id = ?
                """,
                stringValue(address, ""),
                longitude,
                latitude,
                Timestamp.valueOf(LocalDateTime.now()),
                merchantId);
        evictMerchantCaches(merchantId);
        return merchantShopProfile(merchantId);
    }

    public Map<String, Object> updateMerchantDeliveryArea(Long merchantId, Map<String, Object> body) {
        String deliveryMode = stringValue(body.get("deliveryMode"), "FAST").toUpperCase();
        double radiusKm = doubleValue(body.get("deliveryRadiusKm"), defaultMerchantDeliveryRadiusKm(deliveryMode));
        if (radiusKm <= 0) {
            radiusKm = defaultMerchantDeliveryRadiusKm(deliveryMode);
        }
        jdbcTemplate.update("""
                update t_merchant
                set delivery_mode = ?, delivery_area_type = 'RADIUS', delivery_radius_km = ?, updated_at = ?
                where id = ?
                """,
                deliveryMode,
                radiusKm,
                Timestamp.valueOf(LocalDateTime.now()),
                merchantId);
        evictMerchantCaches(merchantId);
        return merchantShopProfile(merchantId);
    }

    public Map<String, Object> createMenuItem(Long merchantId, Map<String, Object> body) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into t_menu_item(
                        merchant_id, category_name, name, description, image_url, spec_name,
                        price, stock, monthly_sales, status, created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, merchantId);
            statement.setString(2, stringValue(body.get("categoryName"), "默认分类"));
            statement.setString(3, stringValue(body.get("name"), "新菜品"));
            statement.setString(4, stringValue(body.get("description"), ""));
            statement.setString(5, stringValue(body.get("imageUrl"), ""));
            statement.setString(6, stringValue(body.get("specName"), "标准规格"));
            statement.setBigDecimal(7, decimalValue(body.get("price"), BigDecimal.ZERO));
            statement.setInt(8, intValue(body.get("stock"), 99));
            statement.setInt(9, intValue(body.get("monthlySales"), 0));
            statement.setString(10, stringValue(body.get("status"), "ON_SALE"));
            statement.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
            return statement;
        }, keyHolder);
        evictMerchantCaches(merchantId);
        return findMenuItem(longValue(keyHolder.getKey(), 0L));
    }

    public Map<String, Object> updateMenuItem(Long merchantId, Long menuItemId, Map<String, Object> body) {
        jdbcTemplate.update("""
                update t_menu_item
                set category_name = ?, name = ?, description = ?, image_url = ?, spec_name = ?,
                    price = ?, stock = ?, status = ?, updated_at = ?
                where id = ? and merchant_id = ?
                """,
                stringValue(body.get("categoryName"), "默认分类"),
                stringValue(body.get("name"), "新菜品"),
                stringValue(body.get("description"), ""),
                stringValue(body.get("imageUrl"), ""),
                stringValue(body.get("specName"), "标准规格"),
                decimalValue(body.get("price"), BigDecimal.ZERO),
                intValue(body.get("stock"), 99),
                stringValue(body.get("status"), "ON_SALE"),
                Timestamp.valueOf(LocalDateTime.now()),
                menuItemId,
                merchantId);
        evictMerchantCaches(merchantId);
        return findMenuItem(menuItemId);
    }

    public void deleteMenuItem(Long merchantId, Long menuItemId) {
        jdbcTemplate.update("delete from t_menu_item where id = ? and merchant_id = ?", menuItemId, merchantId);
        evictMerchantCaches(merchantId);
    }

    public Map<String, Object> getRiderProfile(Long riderId) {
        Map<String, Object> row = queryForSingle("""
                select id, name, phone, status, rider_type, current_longitude, current_latitude, avatar_url,
                       service_address, service_longitude, service_latitude, service_area_type, service_radius_km, service_polygon
                from t_rider
                where id = ?
                """, riderId);
        if (row == null) {
            throw new ServiceException(404, "骑手不存在");
        }
        return linkedMap(
                "riderId", longValue(row.get("id"), 0L),
                "name", stringValue(row.get("name"), ""),
                "phone", stringValue(row.get("phone"), ""),
                "status", stringValue(row.get("status"), ""),
                "riderType", stringValue(row.get("rider_type"), "CROWDSOURCE"),
                "longitude", doubleValue(row.get("current_longitude"), 0.0),
                "latitude", doubleValue(row.get("current_latitude"), 0.0),
                "avatarUrl", stringValue(row.get("avatar_url"), ""),
                "serviceAddress", stringValue(row.get("service_address"), ""),
                "serviceLongitude", doubleValue(row.get("service_longitude"), doubleValue(row.get("current_longitude"), 0.0)),
                "serviceLatitude", doubleValue(row.get("service_latitude"), doubleValue(row.get("current_latitude"), 0.0)),
                "serviceAreaType", stringValue(row.get("service_area_type"), "RADIUS"),
                "serviceRadiusKm", riderServiceRadiusKm(row),
                "servicePolygon", stringValue(row.get("service_polygon"), "")
        );
    }

    public Map<String, Object> updateRiderManualLocation(Long riderId, Map<String, Object> body) {
        double longitude = doubleValue(body.get("longitude"), Double.NaN);
        double latitude = doubleValue(body.get("latitude"), Double.NaN);
        if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
            throw new ServiceException(400, "请输入有效的经度");
        }
        if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
            throw new ServiceException(400, "请输入有效的纬度");
        }
        jdbcTemplate.update("""
                update t_rider
                set current_longitude = ?, current_latitude = ?, updated_at = ?
                where id = ?
                """,
                longitude,
                latitude,
                Timestamp.valueOf(LocalDateTime.now()),
                riderId);
        return getRiderProfile(riderId);
    }

    public Map<String, Object> updateRiderResolvedLocation(Long riderId, double longitude, double latitude) {
        if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
            throw new ServiceException(400, "请输入有效的位置名称");
        }
        if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
            throw new ServiceException(400, "请输入有效的位置名称");
        }
        jdbcTemplate.update("""
                update t_rider
                set current_longitude = ?, current_latitude = ?,
                    service_longitude = ?, service_latitude = ?, updated_at = ?
                where id = ?
                """,
                longitude,
                latitude,
                longitude,
                latitude,
                Timestamp.valueOf(LocalDateTime.now()),
                riderId);
        return getRiderProfile(riderId);
    }

    public Map<String, Object> updateRiderServiceArea(Long riderId, Map<String, Object> body) {
        double longitude = doubleValue(body.get("longitude"), Double.NaN);
        double latitude = doubleValue(body.get("latitude"), Double.NaN);
        if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
            throw new ServiceException(400, "请输入有效的常驻地");
        }
        if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
            throw new ServiceException(400, "请输入有效的常驻地");
        }
        String riderType = stringValue(body.get("riderType"), "CROWDSOURCE").toUpperCase();
        double radiusKm = doubleValue(body.get("serviceRadiusKm"), defaultRiderServiceRadiusKm(riderType));
        if (radiusKm <= 0) {
            radiusKm = defaultRiderServiceRadiusKm(riderType);
        }
        jdbcTemplate.update("""
                update t_rider
                set rider_type = ?, service_address = ?, service_longitude = ?, service_latitude = ?,
                    service_area_type = 'RADIUS', service_radius_km = ?, updated_at = ?
                where id = ?
                """,
                riderType,
                stringValue(body.get("address"), "常驻地"),
                longitude,
                latitude,
                radiusKm,
                Timestamp.valueOf(LocalDateTime.now()),
                riderId);
        return getRiderProfile(riderId);
    }

    public Map<String, Object> updateRiderStatus(Long riderId, String status) {
        String normalized = stringValue(status, "ONLINE").trim().toUpperCase();
        if (!List.of("ONLINE", "OFFLINE", "BUSY", "SUSPENDED").contains(normalized)) {
            throw new ServiceException(400, "骑手状态不正确");
        }
        jdbcTemplate.update("""
                update t_rider
                set status = ?, updated_at = ?
                where id = ?
                """,
                normalized,
                Timestamp.valueOf(LocalDateTime.now()),
                riderId);
        return getRiderProfile(riderId);
    }

    public Map<String, Object> assignRider(Long orderId, Long riderId) {
        Map<String, Object> base = queryOrderBase(orderId);
        ensureRiderCanServeOrder(base, riderId);
        String currentStatus = stringValue(base == null ? null : base.get("status"), "");
        OrderStatus nextStatus = Objects.equals(currentStatus, OrderStatus.MERCHANT_PREPARED.name())
                ? OrderStatus.MERCHANT_PREPARED
                : OrderStatus.RIDER_ACCEPTED;
        jdbcTemplate.update("""
                update t_order
                set rider_id = ?, status = ?, updated_at = ?
                where id = ?
                """, riderId, nextStatus.name(), Timestamp.valueOf(LocalDateTime.now()), orderId);
        Map<String, Object> rider = queryForSingle("""
                select current_longitude, current_latitude
                from t_rider
                where id = ?
                """, riderId);
        if (rider != null) {
            insertTrack(orderId, riderId, "骑手接单",
                    doubleValue(rider.get("current_longitude"), 0.0),
                    doubleValue(rider.get("current_latitude"), 0.0));
        }
        return Map.of("orderId", orderId, "riderId", riderId);
    }

    public Map<String, Object> releaseRiderAssignment(Long orderId, Long riderId, String reason) {
        Map<String, Object> base = queryOrderBase(orderId);
        if (base == null) {
            throw new ServiceException(404, "订单不存在");
        }
        Long assignedRiderId = longValue(base.get("rider_id"), 0L);
        if (!Objects.equals(assignedRiderId, riderId)) {
            throw new ServiceException(403, "只能取消自己当前负责的订单");
        }
        String currentStatus = stringValue(base.get("status"), "");
        if (!List.of(OrderStatus.RIDER_ACCEPTED.name(), OrderStatus.MERCHANT_PREPARED.name()).contains(currentStatus)) {
            throw new ServiceException(400, "订单已取货或配送中，不能取消接单");
        }
        OrderStatus nextStatus = Objects.equals(currentStatus, OrderStatus.MERCHANT_PREPARED.name())
                ? OrderStatus.MERCHANT_PREPARED
                : OrderStatus.MERCHANT_ACCEPTED;
        jdbcTemplate.update("""
                update t_order
                set rider_id = null, status = ?, cancel_reason = ?, updated_at = ?
                where id = ?
                """,
                nextStatus.name(),
                stringValue(reason, "骑手取消接单，订单重新等待分配"),
                Timestamp.valueOf(LocalDateTime.now()),
                orderId);
        insertTrack(orderId, riderId, "骑手取消接单",
                doubleValue(base.get("rider_longitude"), 0.0),
                doubleValue(base.get("rider_latitude"), 0.0));
        return linkedMap(
                "orderId", orderId,
                "oldRiderId", riderId,
                "status", nextStatus.name(),
                "reason", stringValue(reason, "骑手取消接单，订单重新等待分配")
        );
    }

    public DeliveryTrackingSnapshot saveRiderLocation(RiderLocationUpdateRequest request) {
        OrderStatus targetStatus = mapStageToStatus(request.stage());
        Map<String, Object> base = queryOrderBase(request.orderId());
        String currentStatus = stringValue(base == null ? null : base.get("status"), "");
        if (targetStatus == OrderStatus.ARRIVED_STORE
                && !Objects.equals(currentStatus, OrderStatus.MERCHANT_PREPARED.name())) {
            throw new ServiceException(400, "商家出餐后骑手才能取货");
        }
        if (targetStatus == OrderStatus.DELIVERING
                && !List.of(OrderStatus.ARRIVED_STORE.name(), OrderStatus.DELIVERING.name()).contains(currentStatus)) {
            throw new ServiceException(400, "骑手取货后才能开始配送");
        }
        jdbcTemplate.update("""
                update t_rider
                set current_longitude = ?, current_latitude = ?, status = ?, updated_at = ?
                where id = ?
                """,
                request.longitude(),
                request.latitude(),
                "BUSY",
                Timestamp.valueOf(LocalDateTime.now()),
                request.riderId());
        jdbcTemplate.update("""
                update t_order
                set rider_id = ?, status = ?, updated_at = ?
                where id = ?
                """,
                request.riderId(),
                targetStatus.name(),
                Timestamp.valueOf(LocalDateTime.now()),
                request.orderId());
        insertTrack(request.orderId(), request.riderId(), stringValue(request.stage(), "配送中"), request.longitude(), request.latitude());
        return getTrackingSnapshot(request.orderId());
    }

    public Map<String, Object> completeOrder(Long orderId) {
        Map<String, Object> base = queryOrderBase(orderId);
        String currentStatus = stringValue(base == null ? null : base.get("status"), "");
        if (!List.of(OrderStatus.ARRIVED_STORE.name(), OrderStatus.DELIVERING.name()).contains(currentStatus)) {
            throw new ServiceException(400, "骑手取货后才能确认送达");
        }
        jdbcTemplate.update("""
                update t_order
                set status = ?, updated_at = ?
                where id = ?
                """, OrderStatus.COMPLETED.name(), Timestamp.valueOf(LocalDateTime.now()), orderId);
        if (base != null) {
            insertTrack(orderId, longValue(base.get("rider_id"), 7001L), "订单送达",
                    doubleValue(base.get("user_longitude"), 0.0),
                    doubleValue(base.get("user_latitude"), 0.0));
        }
        return Map.of("orderId", orderId, "status", OrderStatus.COMPLETED.name());
    }

    public Map<String, Object> riderDashboard(Long riderId) {
        Map<String, Object> profile = getRiderProfile(riderId);
        Integer todayCompleted = jdbcTemplate.queryForObject("""
                select count(*)
                from t_order
                where rider_id = ?
                  and status = 'COMPLETED'
                  and date(updated_at) = current_date()
                """, Integer.class, riderId);
        BigDecimal todayIncome = jdbcTemplate.queryForObject("""
                select coalesce(sum(amount), 0)
                from t_order
                where rider_id = ?
                  and status = 'COMPLETED'
                  and date(updated_at) = current_date()
                """, BigDecimal.class, riderId);
        Integer totalCompleted = jdbcTemplate.queryForObject("""
                select count(*)
                from t_order
                where rider_id = ?
                  and status = 'COMPLETED'
                """, Integer.class, riderId);
        BigDecimal totalIncome = jdbcTemplate.queryForObject("""
                select coalesce(sum(amount), 0)
                from t_order
                where rider_id = ?
                  and status = 'COMPLETED'
                """, BigDecimal.class, riderId);
        Integer waitPickupCount = jdbcTemplate.queryForObject("""
                select count(*)
                from t_order
                where rider_id = ?
                  and status in ('RIDER_ACCEPTED', 'MERCHANT_PREPARED', 'ARRIVED_STORE')
                """, Integer.class, riderId);
        Integer deliveringCount = jdbcTemplate.queryForObject("""
                select count(*)
                from t_order
                where rider_id = ?
                  and status = 'DELIVERING'
                """, Integer.class, riderId);
        int availableCount = filterEligibleRiderOrders(listOrdersBySql("""
                select id
                from t_order
                where paid = true
                  and (rider_id is null or rider_id = ?)
                  and status in ('PAID', 'MERCHANT_ACCEPTED', 'RIDER_ACCEPTED', 'MERCHANT_PREPARED', 'ARRIVED_STORE', 'DELIVERING')
                """, riderId), riderId).size();
        return linkedMap(
                "profile", profile,
                "stats", linkedMap(
                        "todayIncome", decimalValue(todayIncome, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                        "todayCompleted", intValue(todayCompleted, 0),
                        "totalIncome", decimalValue(totalIncome, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                        "totalCompleted", intValue(totalCompleted, 0),
                        "waitPickupCount", intValue(waitPickupCount, 0),
                        "deliveringCount", intValue(deliveringCount, 0),
                        "availableCount", availableCount,
                        "starLevel", String.format("%.1f", 4.6 + Math.min(intValue(totalCompleted, 0), 20) * 0.01)
                ),
                "taskBuckets", riderTaskBuckets(riderId)
        );
    }

    public Map<String, Object> riderTaskBuckets(Long riderId) {
        List<OrderSummary> nearbyPendingOrders = filterEligibleRiderOrders(listOrdersBySql("""
                select id
                from t_order
                where paid = true
                  and rider_id is null
                  and status in ('PAID', 'MERCHANT_ACCEPTED', 'MERCHANT_PREPARED')
                order by created_at desc
                """), riderId);
        return linkedMap(
                "pending", nearbyPendingOrders,
                "pickup", listOrdersBySql("""
                        select id
                        from t_order
                        where rider_id = ?
                          and status in ('RIDER_ACCEPTED', 'MERCHANT_PREPARED', 'ARRIVED_STORE')
                        order by updated_at desc
                        """, riderId),
                "delivering", listOrdersBySql("""
                        select id
                        from t_order
                        where rider_id = ?
                          and status = 'DELIVERING'
                        order by updated_at desc
                        """, riderId),
                "completed", listOrdersBySql("""
                        select id
                        from t_order
                        where rider_id = ?
                          and status = 'COMPLETED'
                        order by updated_at desc
                        limit 20
                        """, riderId)
        );
    }

    private List<OrderSummary> filterEligibleRiderOrders(List<OrderSummary> orders, Long riderId) {
        Map<String, Object> rider = getRiderProfile(riderId);
        double riderLongitude = doubleValue(rider.get("serviceLongitude"), doubleValue(rider.get("longitude"), 0.0));
        double riderLatitude = doubleValue(rider.get("serviceLatitude"), doubleValue(rider.get("latitude"), 0.0));
        double maxDistanceKm = riderServiceRadiusKm(rider);
        return orders.stream()
                .filter(order -> {
                    Coordinate merchant = order.merchant().coordinate();
                    double merchantDistanceKm = haversineDistanceKm(riderLatitude, riderLongitude, merchant.latitude(), merchant.longitude());
                    Map<String, Object> base = queryOrderBase(order.orderId());
                    return merchantDistanceKm <= maxDistanceKm && userAddressInMerchantDeliveryRange(base);
                })
                .sorted((left, right) -> {
                    Coordinate leftMerchant = left.merchant().coordinate();
                    Coordinate rightMerchant = right.merchant().coordinate();
                    double leftDistanceKm = haversineDistanceKm(riderLatitude, riderLongitude, leftMerchant.latitude(), leftMerchant.longitude());
                    double rightDistanceKm = haversineDistanceKm(riderLatitude, riderLongitude, rightMerchant.latitude(), rightMerchant.longitude());
                    return Double.compare(leftDistanceKm, rightDistanceKm);
                })
                .toList();
    }

    public List<Map<String, Object>> riderHistory(Long riderId, String filter) {
        String sql = """
                select id
                from t_order
                where rider_id = ?
                  and status = 'COMPLETED'
                """;
        if ("TODAY".equalsIgnoreCase(filter)) {
            sql += " and date(updated_at) = current_date()";
        } else if ("WEEK".equalsIgnoreCase(filter)) {
            sql += " and updated_at >= date_sub(current_timestamp(), interval 7 day)";
        }
        sql += " order by updated_at desc";
        List<OrderSummary> orders = listOrdersBySql(sql, riderId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderSummary order : orders) {
            Map<String, Object> detail = getOrderDetail(order.orderId());
            result.add(linkedMap(
                    "summary", order,
                    "detail", detail,
                    "flow", getOrderFlow(order.orderId())
            ));
        }
        return result;
    }

    public Map<String, Object> riderDispatchBoard(Long riderId) {
        List<OrderSummary> pickup = listOrdersBySql("""
                select id
                from t_order
                where rider_id = ?
                  and status in ('RIDER_ACCEPTED', 'MERCHANT_PREPARED', 'ARRIVED_STORE')
                order by updated_at desc
                """, riderId);
        List<OrderSummary> delivering = listOrdersBySql("""
                select id
                from t_order
                where rider_id = ?
                  and status = 'DELIVERING'
                order by updated_at desc
                """, riderId);
        List<OrderSummary> completed = listOrdersBySql("""
                select id
                from t_order
                where rider_id = ?
                  and status = 'COMPLETED'
                order by updated_at desc
                limit 10
                """, riderId);
        List<Map<String, Object>> points = new ArrayList<>();
        points.add(linkedMap("key", "pickup", "label", "待取货", "count", pickup.size()));
        points.add(linkedMap("key", "delivering", "label", "配送中", "count", delivering.size()));
        points.add(linkedMap("key", "completed", "label", "已完成", "count", completed.size()));
        return linkedMap(
                "points", points,
                "groups", linkedMap(
                        "pickup", pickup,
                        "delivering", delivering,
                        "completed", completed
                )
        );
    }

    public void saveRiderRoutePlan(Long riderId,
                                   String preferredPointKey,
                                   String navigationPointKey,
                                   int totalDistanceMeters,
                                   int totalDurationSeconds,
                                   String planJson) {
        ensureRiderRouteTables();
        jdbcTemplate.update("""
                insert into t_rider_route_plan(
                    rider_id, preferred_point_key, navigation_point_key,
                    total_distance_meters, total_duration_seconds, plan_json, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                riderId,
                stringValue(preferredPointKey, ""),
                stringValue(navigationPointKey, ""),
                totalDistanceMeters,
                totalDurationSeconds,
                planJson,
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()));
    }

    public void recordRiderRouteEvent(Long riderId, Long orderId, String pointType, String eventType) {
        ensureRiderRouteTables();
        jdbcTemplate.update("""
                insert into t_rider_route_event(rider_id, order_id, point_type, event_type, created_at)
                values (?, ?, ?, ?, ?)
                """,
                riderId,
                orderId,
                stringValue(pointType, ""),
                stringValue(eventType, ""),
                Timestamp.valueOf(LocalDateTime.now()));
    }

    private void ensureRiderRouteTables() {
        jdbcTemplate.execute("""
                create table if not exists t_rider_route_plan (
                    id bigint primary key auto_increment,
                    rider_id bigint not null,
                    preferred_point_key varchar(64),
                    navigation_point_key varchar(64),
                    total_distance_meters int not null default 0,
                    total_duration_seconds int not null default 0,
                    plan_json text,
                    created_at timestamp,
                    updated_at timestamp
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists t_rider_route_event (
                    id bigint primary key auto_increment,
                    rider_id bigint not null,
                    order_id bigint,
                    point_type varchar(24),
                    event_type varchar(32),
                    created_at timestamp
                )
                """);
    }

    public Map<String, Object> riderOrderDetail(Long orderId) {
        return linkedMap(
                "detail", getOrderDetail(orderId),
                "flow", getOrderFlow(orderId),
                "tracking", getTrackingSnapshot(orderId)
        );
    }

    public Map<String, Object> riderStats() {
        Integer total = jdbcTemplate.queryForObject("select count(*) from t_rider", Integer.class);
        Integer online = jdbcTemplate.queryForObject("select count(*) from t_rider where status in ('ONLINE', 'BUSY')", Integer.class);
        Integer busy = jdbcTemplate.queryForObject("select count(*) from t_rider where status = 'BUSY'", Integer.class);
        return Map.of(
                "total", intValue(total, 0),
                "online", intValue(online, 0),
                "busy", intValue(busy, 0)
        );
    }

    public List<Map<String, Object>> riderList() {
        return jdbcTemplate.query("""
                select r.id, r.name, r.phone, r.status, r.current_longitude, r.current_latitude, r.avatar_url, r.updated_at,
                       sum(case when o.status in ('RIDER_ACCEPTED', 'MERCHANT_PREPARED', 'ARRIVED_STORE', 'DELIVERING') then 1 else 0 end) as active_orders,
                       sum(case when o.status = 'COMPLETED' and date(o.updated_at) = current_date() then 1 else 0 end) as completed_today,
                       sum(case when o.status = 'COMPLETED' then 1 else 0 end) as completed_total
                from t_rider r
                left join t_order o on o.rider_id = r.id
                group by r.id, r.name, r.phone, r.status, r.current_longitude, r.current_latitude, r.avatar_url, r.updated_at
                order by r.id asc
                """,
                (rs, rowNum) -> linkedMap(
                        "id", rs.getLong("id"),
                        "riderId", "R" + rs.getLong("id"),
                        "name", rs.getString("name"),
                        "phone", rs.getString("phone"),
                        "status", rs.getString("status"),
                        "city", "北京",
                        "zone", coordinateZone(rs.getDouble("current_longitude"), rs.getDouble("current_latitude")),
                        "longitude", rs.getDouble("current_longitude"),
                        "latitude", rs.getDouble("current_latitude"),
                        "activeOrders", rs.getInt("active_orders"),
                        "completedToday", rs.getInt("completed_today"),
                        "onTimeRate", Math.max(80, Math.min(99, 92 + rs.getInt("completed_today"))),
                        "rating", BigDecimal.valueOf(4.5 + Math.min(rs.getInt("completed_total"), 20) * 0.01).setScale(1, RoundingMode.HALF_UP),
                        "violationCount", "SUSPENDED".equals(rs.getString("status")) ? 1 : 0,
                        "lastActive", rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toLocalDateTime().toString().replace("T", " "),
                        "avatarUrl", stringValue(rs.getString("avatar_url"), "")
                ));
    }

    public Map<String, Object> adminStats() {
        Integer todayOrders = jdbcTemplate.queryForObject("select count(*) from t_order", Integer.class);
        BigDecimal revenue = jdbcTemplate.queryForObject("select coalesce(sum(amount), 0) from t_order where paid = true", BigDecimal.class);
        Integer activeMerchants = jdbcTemplate.queryForObject("select count(*) from t_merchant where status = 'ACTIVE'", Integer.class);
        Integer completedOrders = jdbcTemplate.queryForObject("select count(*) from t_order where status = 'COMPLETED'", Integer.class);
        return Map.of(
                "todayOrders", intValue(todayOrders, 0),
                "todayRevenue", decimalValue(revenue, BigDecimal.ZERO),
                "avgDeliveryMinutes", 28,
                "activeMerchants", intValue(activeMerchants, 0),
                "completedOrders", intValue(completedOrders, 0)
        );
    }

    public List<Map<String, Object>> merchantList() {
        return jdbcTemplate.query("""
                select id, name, category, address, longitude, latitude, distance_km, status,
                       min_order_amount, delivery_fee, estimated_delivery_minutes, avg_score, monthly_sales
                from t_merchant
                order by id asc
                """,
                (rs, rowNum) -> linkedMap(
                        "id", rs.getLong("id"),
                        "name", rs.getString("name"),
                        "category", rs.getString("category"),
                        "address", stringValue(rs.getString("address"), ""),
                        "longitude", rs.getDouble("longitude"),
                        "latitude", rs.getDouble("latitude"),
                        "distanceKm", rs.getDouble("distance_km"),
                        "status", rs.getString("status"),
                        "minOrderAmount", rs.getBigDecimal("min_order_amount"),
                        "deliveryFee", rs.getBigDecimal("delivery_fee"),
                        "estimatedDeliveryMinutes", rs.getInt("estimated_delivery_minutes"),
                        "avgScore", rs.getBigDecimal("avg_score"),
                        "monthlySales", rs.getInt("monthly_sales"),
                        "auditStatus", "ACTIVE".equals(rs.getString("status")) ? "已通过" : "待复核",
                        "violationCount", "ACTIVE".equals(rs.getString("status")) ? 0 : 1
                ));
    }

    public List<Map<String, Object>> adminMerchantAudits() {
        return jdbcTemplate.query("""
                select id, name, category, address, status, updated_at
                from t_merchant
                where status <> 'ACTIVE'
                order by updated_at desc, id asc
                """,
                (rs, rowNum) -> linkedMap(
                        "id", rs.getLong("id"),
                        "name", rs.getString("name"),
                        "category", rs.getString("category"),
                        "address", stringValue(rs.getString("address"), ""),
                        "submittedAt", rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toLocalDateTime().toString().replace("T", " "),
                        "status", "待复核",
                        "reason", "商家当前状态为 " + rs.getString("status") + "，需要运营审核"
                ));
    }

    public List<Map<String, Object>> adminCategories() {
        return jdbcTemplate.query("""
                select category, count(*) as related_merchants,
                       sum(case when status = 'ACTIVE' then 1 else 0 end) as active_count
                from t_merchant
                group by category
                order by related_merchants desc, category asc
                """,
                (rs, rowNum) -> linkedMap(
                        "id", rowNum + 1,
                        "name", stringValue(rs.getString("category"), "未分类"),
                        "sort", rowNum + 1,
                        "status", rs.getInt("active_count") > 0 ? "上架" : "下架",
                        "relatedMerchants", rs.getInt("related_merchants")
                ));
    }

    public Map<String, Object> adminMarketing() {
        List<Map<String, Object>> campaigns = jdbcTemplate.query("""
                select id, name, discount_amount, min_amount, status
                from t_coupon
                order by id asc
                """,
                (rs, rowNum) -> linkedMap(
                        "id", rs.getLong("id"),
                        "name", rs.getString("name"),
                        "type", "优惠券",
                        "status", couponStatusText(rs.getString("status")),
                        "budget", "¥" + decimalValue(rs.getBigDecimal("discount_amount"), BigDecimal.ZERO),
                        "progress", Math.min(100, Math.max(5, decimalValue(rs.getBigDecimal("min_amount"), BigDecimal.ZERO).intValue())),
                        "owner", "平台运营"
                ));
        List<Map<String, Object>> strategies = jdbcTemplate.query("""
                select category, count(*) as merchant_count, coalesce(sum(monthly_sales), 0) as sales
                from t_merchant
                group by category
                order by sales desc
                limit 3
                """,
                (rs, rowNum) -> linkedMap(
                        "title", stringValue(rs.getString("category"), "未分类") + "运营建议",
                        "detail", "当前关联商家 " + rs.getInt("merchant_count") + " 家，月销量 " + rs.getInt("sales") + "，建议结合真实销量配置活动。"
                ));
        return linkedMap("campaigns", campaigns, "strategies", strategies);
    }

    public Map<String, Object> adminRisk() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(jdbcTemplate.query("""
                select order_no, status, amount
                from t_order
                where status in ('CANCELLED', 'REFUNDING')
                order by updated_at desc
                limit 20
                """,
                (rs, rowNum) -> linkedMap(
                        "id", "ORDER-" + rowNum,
                        "target", rs.getString("order_no"),
                        "targetType", "订单",
                        "level", "REFUNDING".equals(rs.getString("status")) ? "高" : "中",
                        "rule", "异常订单状态",
                        "status", "待处理",
                        "evidence", "订单状态=" + rs.getString("status") + "，金额=" + rs.getBigDecimal("amount"),
                        "summary", "订单处于取消或退款流程，需要运营复核。"
                )));
        rows.addAll(jdbcTemplate.query("""
                select id, name, status
                from t_rider
                where status = 'SUSPENDED'
                order by id asc
                """,
                (rs, rowNum) -> linkedMap(
                        "id", "RIDER-" + rs.getLong("id"),
                        "target", "R" + rs.getLong("id"),
                        "targetType", "骑手",
                        "level", "高",
                        "rule", "骑手账号停用",
                        "status", "待复核",
                        "evidence", "骑手姓名=" + rs.getString("name") + "，状态=" + rs.getString("status"),
                        "summary", "骑手账号已停用，需要确认停用原因。"
                )));
        List<Map<String, Object>> blacklist = List.of(
                linkedMap("title", "用户黑名单", "detail", "当前封禁用户 " + countBySql("select count(*) from t_user where status = 'BANNED'") + " 人。"),
                linkedMap("title", "骑手黑名单", "detail", "当前停用骑手 " + countBySql("select count(*) from t_rider where status = 'SUSPENDED'") + " 人。")
        );
        return linkedMap("rows", rows, "blacklist", blacklist);
    }

    public List<Map<String, Object>> adminHealth() {
        return List.of(
                linkedMap("label", "订单服务", "value", countBySql("select count(*) from t_order") > 0 ? 99 : 75, "status", "success"),
                linkedMap("label", "商家服务", "value", countBySql("select count(*) from t_merchant") > 0 ? 98 : 75, "status", "success"),
                linkedMap("label", "骑手调度", "value", countBySql("select count(*) from t_rider where status in ('ONLINE', 'BUSY')") > 0 ? 96 : 70, "status", "success"),
                linkedMap("label", "通知队列", "value", countBySql("select count(*) from t_notification where read_flag = false") > 20 ? 82 : 97, "status", countBySql("select count(*) from t_notification where read_flag = false") > 20 ? "warning" : "success")
        );
    }

    public List<Map<String, Object>> adminOperationLogs() {
        List<Map<String, Object>> logs = new ArrayList<>();
        logs.addAll(jdbcTemplate.query("""
                select id, display_name, status, updated_at
                from t_admin_user
                order by updated_at desc, id desc
                limit 10
                """,
                (rs, rowNum) -> linkedMap(
                        "id", "ADMIN-" + rs.getLong("id"),
                        "operator", rs.getString("display_name"),
                        "time", rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toLocalDateTime().toString().replace("T", " "),
                        "action", "管理员账号状态同步",
                        "target", "账号状态=" + rs.getString("status"),
                        "result", "成功"
                )));
        logs.addAll(jdbcTemplate.query("""
                select id, title, target_role, created_at
                from t_notification
                where target_role = 'ADMIN'
                order by created_at desc, id desc
                limit 10
                """,
                (rs, rowNum) -> linkedMap(
                        "id", "NOTICE-" + rs.getLong("id"),
                        "operator", "系统通知",
                        "time", rs.getTimestamp("created_at") == null ? "" : rs.getTimestamp("created_at").toLocalDateTime().toString().replace("T", " "),
                        "action", "生成通知",
                        "target", rs.getString("title"),
                        "result", "成功"
                )));
        return logs;
    }

    public List<Map<String, Object>> adminPermissions() {
        return List.of(
                linkedMap("label", "平台总览", "children", List.of(
                        linkedMap("label", "查看看板"),
                        linkedMap("label", "导出日报"),
                        linkedMap("label", "发送公告")
                )),
                linkedMap("label", "用户管理", "children", List.of(
                        linkedMap("label", "查看用户"),
                        linkedMap("label", "封禁用户"),
                        linkedMap("label", "导出用户")
                )),
                linkedMap("label", "商家管理", "children", List.of(
                        linkedMap("label", "入驻审核"),
                        linkedMap("label", "商品管理"),
                        linkedMap("label", "上下线门店")
                )),
                linkedMap("label", "骑手管理", "children", List.of(
                        linkedMap("label", "查看骑手"),
                        linkedMap("label", "调度骑手"),
                        linkedMap("label", "停用骑手")
                )),
                linkedMap("label", "订单管理", "children", List.of(
                        linkedMap("label", "查看订单"),
                        linkedMap("label", "退款处理"),
                        linkedMap("label", "批量导出")
                )),
                linkedMap("label", "系统设置", "children", List.of(
                        linkedMap("label", "管理员管理"),
                        linkedMap("label", "权限配置"),
                        linkedMap("label", "查看日志")
                ))
        );
    }

    public Map<String, Object> adminSettings() {
        return linkedMap(
                "paymentProvider", "微信支付 / 支付宝 / 余额支付",
                "noticeChannel", "站内消息 / 企业微信 / 短信",
                "timeoutMinutes", 15,
                "enableRiskPopup", countBySql("select count(*) from t_order where status in ('CANCELLED', 'REFUNDING')") > 0,
                "enableOperationReport", countBySql("select count(*) from t_admin_user") > 0
        );
    }

    private void ensureUserDoesNotExist(String phone, String username) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from t_user
                where phone = ? or username = ?
                """, Integer.class, phone, username);
        if (count != null && count > 0) {
            throw new ServiceException(400, "手机号或用户名已注册");
        }
    }

    private MerchantSummary toMerchantSummary(Map<String, Object> row) {
        Long merchantId = longValue(row.get("id"), 0L);
        return new MerchantSummary(
                merchantId,
                stringValue(row.get("name"), ""),
                stringValue(row.get("category"), ""),
                doubleValue(row.get("distance_km"), 0.0),
                new Coordinate(
                        doubleValue(row.get("longitude"), 0.0),
                        doubleValue(row.get("latitude"), 0.0),
                        stringValue(row.get("name"), "")
                ),
                listMenuItemsByMerchantId(merchantId)
        );
    }

    private List<Map<String, Object>> listNearbyMerchantRows(String keyword, String category, Double longitude, Double latitude, Double radiusKm) {
        StringBuilder sql = new StringBuilder("""
                select id, name, category, address, notice, image_url,
                       longitude, latitude, distance_km, min_order_amount, delivery_fee,
                       delivery_mode, delivery_area_type, delivery_radius_km, delivery_polygon,
                       estimated_delivery_minutes, avg_score, monthly_sales, status
                from t_merchant
                where status = 'ACTIVE'
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (name like ? or category like ? or address like ?)");
            String value = "%" + keyword + "%";
            args.add(value);
            args.add(value);
            args.add(value);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" and category = ?");
            args.add(category);
        }
        sql.append(" order by id asc");

        Double normalizedLongitude = normalizeCoordinate(longitude, -180.0, 180.0);
        Double normalizedLatitude = normalizeCoordinate(latitude, -90.0, 90.0);
        double maxRadiusKm = radiusKm == null || radiusKm <= 0 ? 5.0 : radiusKm;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LinkedHashMap<String, Object> enriched = new LinkedHashMap<>(row);
            double merchantLongitude = doubleValue(row.get("longitude"), 0.0);
            double merchantLatitude = doubleValue(row.get("latitude"), 0.0);
            double distanceKm = doubleValue(row.get("distance_km"), 0.0);
            if (normalizedLongitude != null && normalizedLatitude != null) {
                distanceKm = haversineDistanceKm(normalizedLatitude, normalizedLongitude, merchantLatitude, merchantLongitude);
                double deliveryRadiusKm = merchantDeliveryRadiusKm(row);
                if (distanceKm > maxRadiusKm || distanceKm > deliveryRadiusKm) {
                    continue;
                }
            }
            enriched.put("distance_km", distanceKm);
            filtered.add(enriched);
        }
        filtered.sort((left, right) -> {
            int byDistance = Double.compare(
                    doubleValue(left.get("distance_km"), Double.MAX_VALUE),
                    doubleValue(right.get("distance_km"), Double.MAX_VALUE));
            if (byDistance != 0) {
                return byDistance;
            }
            return Long.compare(longValue(left.get("id"), 0L), longValue(right.get("id"), 0L));
        });
        return filtered;
    }

    private Double normalizeCoordinate(Double value, double min, double max) {
        if (value == null || Double.isNaN(value) || value < min || value > max) {
            return null;
        }
        return value;
    }

    private int countBySql(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return intValue(count, 0);
    }

    private String userLevel(int orderCount) {
        if (orderCount >= 10) {
            return "铂金会员";
        }
        if (orderCount >= 5) {
            return "黄金会员";
        }
        if (orderCount >= 2) {
            return "白银会员";
        }
        return "普通会员";
    }

    private String couponStatusText(String status) {
        return switch (stringValue(status, "").toUpperCase()) {
            case "ACTIVE" -> "进行中";
            case "PENDING" -> "待上线";
            case "EXPIRED", "DISABLED" -> "已完成";
            default -> "进行中";
        };
    }

    private String coordinateZone(double longitude, double latitude) {
        if (latitude > 39.98 && longitude > 116.45) {
            return "望京";
        }
        if (latitude > 39.94 && longitude > 116.45) {
            return "酒仙桥";
        }
        if (latitude > 39.95 && longitude > 116.30) {
            return "中关村";
        }
        return "平台调度区";
    }

    private String roundCoordinate(Double value) {
        if (value == null || Double.isNaN(value)) {
            return null;
        }
        return String.format("%.3f", value);
    }

    private double haversineDistanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double earthRadiusKm = 6371.0;
        double deltaLatitude = Math.toRadians(latitude2 - latitude1);
        double deltaLongitude = Math.toRadians(longitude2 - longitude1);
        double startLatitude = Math.toRadians(latitude1);
        double endLatitude = Math.toRadians(latitude2);
        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude) * Math.pow(Math.sin(deltaLongitude / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(earthRadiusKm * c).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private void ensureUserAddressInMerchantDeliveryRange(Map<String, Object> merchant, Map<String, Object> address) {
        double userLongitude = doubleValue(address.get("longitude"), 0.0);
        double userLatitude = doubleValue(address.get("latitude"), 0.0);
        double merchantLongitude = doubleValue(merchant.get("longitude"), 0.0);
        double merchantLatitude = doubleValue(merchant.get("latitude"), 0.0);
        if (!isPointInMerchantDeliveryArea(merchant, userLongitude, userLatitude, merchantLongitude, merchantLatitude)) {
            throw new ServiceException(400, "当前收货地址超出商家配送范围，无法下单");
        }
    }

    private void ensureRiderCanServeOrder(Map<String, Object> order, Long riderId) {
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        if (!userAddressInMerchantDeliveryRange(order)) {
            throw new ServiceException(400, "用户地址超出商家配送范围，骑手不能接单");
        }
        Map<String, Object> rider = getRiderProfile(riderId);
        double serviceLongitude = doubleValue(rider.get("serviceLongitude"), doubleValue(rider.get("longitude"), 0.0));
        double serviceLatitude = doubleValue(rider.get("serviceLatitude"), doubleValue(rider.get("latitude"), 0.0));
        double merchantLongitude = doubleValue(order.get("merchant_longitude"), 0.0);
        double merchantLatitude = doubleValue(order.get("merchant_latitude"), 0.0);
        if (!isPointInRiderServiceArea(rider, merchantLongitude, merchantLatitude, serviceLongitude, serviceLatitude)) {
            throw new ServiceException(400, "商家不在骑手服务区域内，不能接单");
        }
    }

    private boolean userAddressInMerchantDeliveryRange(Map<String, Object> order) {
        if (order == null) {
            return false;
        }
        double userLongitude = doubleValue(order.get("user_longitude"), 0.0);
        double userLatitude = doubleValue(order.get("user_latitude"), 0.0);
        double merchantLongitude = doubleValue(order.get("merchant_longitude"), 0.0);
        double merchantLatitude = doubleValue(order.get("merchant_latitude"), 0.0);
        return isPointInMerchantDeliveryArea(order, userLongitude, userLatitude, merchantLongitude, merchantLatitude);
    }

    private boolean isPointInMerchantDeliveryArea(Map<String, Object> values,
                                                  double pointLongitude,
                                                  double pointLatitude,
                                                  double merchantLongitude,
                                                  double merchantLatitude) {
        String areaType = stringValue(firstNonNull(values.get("deliveryAreaType"), values.get("delivery_area_type")), "RADIUS");
        String polygon = stringValue(firstNonNull(values.get("deliveryPolygon"), values.get("delivery_polygon")), "");
        if ("POLYGON".equalsIgnoreCase(areaType) && !polygon.isBlank()) {
            return pointInPolygon(pointLongitude, pointLatitude, polygon);
        }
        double distanceKm = haversineDistanceKm(pointLatitude, pointLongitude, merchantLatitude, merchantLongitude);
        return distanceKm <= merchantDeliveryRadiusKm(values);
    }

    private boolean isPointInRiderServiceArea(Map<String, Object> values,
                                              double pointLongitude,
                                              double pointLatitude,
                                              double serviceLongitude,
                                              double serviceLatitude) {
        String areaType = stringValue(firstNonNull(values.get("serviceAreaType"), values.get("service_area_type")), "RADIUS");
        String polygon = stringValue(firstNonNull(values.get("servicePolygon"), values.get("service_polygon")), "");
        if ("POLYGON".equalsIgnoreCase(areaType) && !polygon.isBlank()) {
            return pointInPolygon(pointLongitude, pointLatitude, polygon);
        }
        double distanceKm = haversineDistanceKm(serviceLatitude, serviceLongitude, pointLatitude, pointLongitude);
        return distanceKm <= riderServiceRadiusKm(values);
    }

    private boolean pointInPolygon(double longitude, double latitude, String polygonText) {
        List<double[]> points = parsePolygonPoints(polygonText);
        if (points.size() < 3) {
            return false;
        }
        boolean inside = false;
        for (int current = 0, previous = points.size() - 1; current < points.size(); previous = current++) {
            double currentLongitude = points.get(current)[0];
            double currentLatitude = points.get(current)[1];
            double previousLongitude = points.get(previous)[0];
            double previousLatitude = points.get(previous)[1];
            boolean crossesLatitude = (currentLatitude > latitude) != (previousLatitude > latitude);
            double intersectLongitude = (previousLongitude - currentLongitude)
                    * (latitude - currentLatitude)
                    / (previousLatitude - currentLatitude + 0.0000000001)
                    + currentLongitude;
            if (crossesLatitude && longitude < intersectLongitude) {
                inside = !inside;
            }
        }
        return inside;
    }

    private List<double[]> parsePolygonPoints(String polygonText) {
        List<double[]> points = new ArrayList<>();
        if (polygonText == null || polygonText.isBlank()) {
            return points;
        }
        try {
            Object raw = JSON_MAPPER.readValue(polygonText, Object.class);
            collectPolygonPoints(raw, points);
        } catch (Exception ignored) {
            for (String pair : polygonText.split(";")) {
                String[] values = pair.split(",");
                if (values.length >= 2) {
                    try {
                        points.add(new double[]{Double.parseDouble(values[0].trim()), Double.parseDouble(values[1].trim())});
                    } catch (NumberFormatException ignoredNumber) {
                        // Ignore invalid coordinate pairs and fall back to radius matching at the caller if needed.
                    }
                }
            }
        }
        return points;
    }

    @SuppressWarnings("unchecked")
    private void collectPolygonPoints(Object raw, List<double[]> points) {
        if (raw instanceof Map<?, ?> map) {
            Double longitude = numberValue(firstNonNull(map.get("longitude"), map.get("lng"), map.get("lon")));
            Double latitude = numberValue(firstNonNull(map.get("latitude"), map.get("lat")));
            if (longitude != null && latitude != null) {
                points.add(new double[]{longitude, latitude});
            }
            return;
        }
        if (raw instanceof List<?> list) {
            if (list.size() >= 2) {
                Double longitude = numberValue(list.get(0));
                Double latitude = numberValue(list.get(1));
                if (longitude != null && latitude != null) {
                    points.add(new double[]{longitude, latitude});
                    return;
                }
            }
            for (Object item : list) {
                collectPolygonPoints(item, points);
            }
        }
    }

    private Double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double merchantDeliveryRadiusKm(Map<String, Object> values) {
        double configured = doubleValue(firstNonNull(values.get("deliveryRadiusKm"), values.get("delivery_radius_km")), 0.0);
        if (configured > 0) {
            return configured;
        }
        return defaultMerchantDeliveryRadiusKm(stringValue(firstNonNull(values.get("deliveryMode"), values.get("delivery_mode")), "FAST"));
    }

    private double riderServiceRadiusKm(Map<String, Object> values) {
        double configured = doubleValue(firstNonNull(values.get("serviceRadiusKm"), values.get("service_radius_km")), 0.0);
        if (configured > 0) {
            return configured;
        }
        return defaultRiderServiceRadiusKm(stringValue(firstNonNull(values.get("riderType"), values.get("rider_type")), "CROWDSOURCE"));
    }

    private double defaultMerchantDeliveryRadiusKm(String deliveryMode) {
        return "DEDICATED".equalsIgnoreCase(deliveryMode) ? 3.0 : 5.0;
    }

    private double defaultRiderServiceRadiusKm(String riderType) {
        return "DEDICATED".equalsIgnoreCase(riderType) ? 3.0 : 5.0;
    }

    private Map<String, Object> toUserProfile(Map<String, Object> row) {
        return linkedMap(
                "userId", longValue(row.get("id"), 0L),
                "name", stringValue(row.get("name"), ""),
                "username", stringValue(row.get("username"), ""),
                "phone", stringValue(row.get("phone"), ""),
                "avatarUrl", stringValue(row.get("avatar_url"), ""),
                "balance", decimalValue(row.get("balance"), BigDecimal.ZERO),
                "longitude", doubleValue(row.get("current_longitude"), 0.0),
                "latitude", doubleValue(row.get("current_latitude"), 0.0),
                "address", stringValue(row.get("address"), ""),
                "defaultAddress", getDefaultAddress(longValue(row.get("id"), 0L))
        );
    }

    private void updateUserAddressSnapshot(Long userId, Long addressId) {
        Map<String, Object> address = queryAddress(userId, addressId);
        if (address == null) {
            return;
        }
        jdbcTemplate.update("""
                update t_user
                set address = ?, current_longitude = ?, current_latitude = ?, updated_at = ?
                where id = ?
                """,
                stringValue(address.get("detailAddress"), "") + " " + stringValue(address.get("houseNumber"), ""),
                doubleValue(address.get("longitude"), 0.0),
                doubleValue(address.get("latitude"), 0.0),
                Timestamp.valueOf(LocalDateTime.now()),
                userId);
    }

    private int countUserAddresses(Long userId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from t_user_address where user_id = ?", Integer.class, userId);
        return intValue(count, 0);
    }

    private Long resolveAddressId(Long userId, Map<String, Object> body) {
        Long addressId = longValue(body.get("addressId"), 0L);
        if (addressId > 0) {
            return addressId;
        }
        Map<String, Object> defaultAddress = getDefaultAddress(userId);
        return defaultAddress == null ? 0L : longValue(defaultAddress.get("id"), 0L);
    }

    private Map<String, Object> queryAddress(Long userId, Long addressId) {
        if (addressId == null || addressId <= 0) {
            return null;
        }
        Map<String, Object> row = queryForSingle("""
                select id, receiver_name, receiver_phone, tag, detail_address, house_number,
                       longitude, latitude, is_default
                from t_user_address
                where id = ? and user_id = ?
                """, addressId, userId);
        if (row == null) {
            return null;
        }
        return Map.of(
                "id", longValue(row.get("id"), 0L),
                "receiverName", stringValue(row.get("receiver_name"), ""),
                "receiverPhone", stringValue(row.get("receiver_phone"), ""),
                "tag", stringValue(row.get("tag"), ""),
                "detailAddress", stringValue(row.get("detail_address"), ""),
                "houseNumber", stringValue(row.get("house_number"), ""),
                "longitude", doubleValue(row.get("longitude"), 0.0),
                "latitude", doubleValue(row.get("latitude"), 0.0),
                "isDefault", booleanValue(row.get("is_default"), false)
        );
    }

    private List<Map<String, Object>> selectedCartItems(Long userId, Long merchantId) {
        return jdbcTemplate.query("""
                select id, menu_item_id, item_name, image_url, spec_name, price, quantity, merchant_id
                from t_cart_item
                where user_id = ? and merchant_id = ? and selected = true
                order by id asc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "menuItemId", rs.getLong("menu_item_id"),
                        "name", rs.getString("item_name"),
                        "imageUrl", stringValue(rs.getString("image_url"), ""),
                        "specName", stringValue(rs.getString("spec_name"), ""),
                        "price", rs.getBigDecimal("price"),
                        "quantity", rs.getInt("quantity"),
                        "merchantId", rs.getLong("merchant_id")
                ),
                userId, merchantId);
    }

    private BigDecimal sumCartAmount(List<Map<String, Object>> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            total = total.add(
                    decimalValue(item.get("price"), BigDecimal.ZERO)
                            .multiply(BigDecimal.valueOf(intValue(item.get("quantity"), 0)))
            );
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCouponDiscount(Long userId, Long couponId, BigDecimal itemAmount) {
        if (couponId == null || couponId <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Map<String, Object> row = queryForSingle("""
                select c.discount_amount, c.min_amount
                from t_user_coupon uc
                join t_coupon c on c.id = uc.coupon_id
                where uc.user_id = ? and uc.coupon_id = ? and uc.status = 'UNUSED'
                limit 1
                """, userId, couponId);
        if (row == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal minAmount = decimalValue(row.get("min_amount"), BigDecimal.ZERO);
        if (itemAmount.compareTo(minAmount) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return decimalValue(row.get("discount_amount"), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private List<OrderSummary> listOrdersBySql(String sql, Object... args) {
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), args);
        List<OrderSummary> result = new ArrayList<>();
        for (Long id : ids) {
            result.add(getOrderSummary(id));
        }
        return result;
    }

    private List<Map<String, Object>> listOrderItems(Long orderId) {
        return jdbcTemplate.query("""
                select id, menu_item_id, item_name, image_url, spec_name, price, quantity
                from t_order_item
                where order_id = ?
                order by id asc
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "menuItemId", rs.getLong("menu_item_id"),
                        "name", rs.getString("item_name"),
                        "imageUrl", stringValue(rs.getString("image_url"), ""),
                        "specName", stringValue(rs.getString("spec_name"), ""),
                        "price", rs.getBigDecimal("price"),
                        "quantity", rs.getInt("quantity")
                ),
                orderId);
    }

    private Map<String, Object> findMenuItem(Long menuItemId) {
        Map<String, Object> row = queryForSingle("""
                select id, merchant_id, category_name, name, description, image_url,
                       spec_name, price, stock, monthly_sales, status
                from t_menu_item
                where id = ?
                """, menuItemId);
        if (row == null) {
            throw new ServiceException(404, "菜品不存在");
        }
        return linkedMap(
                "id", longValue(row.get("id"), 0L),
                "merchantId", longValue(row.get("merchant_id"), 0L),
                "categoryName", stringValue(row.get("category_name"), ""),
                "name", stringValue(row.get("name"), ""),
                "description", stringValue(row.get("description"), ""),
                "imageUrl", stringValue(row.get("image_url"), ""),
                "specName", stringValue(row.get("spec_name"), ""),
                "price", decimalValue(row.get("price"), BigDecimal.ZERO),
                "stock", intValue(row.get("stock"), 0),
                "monthlySales", intValue(row.get("monthly_sales"), 0),
                "status", stringValue(row.get("status"), "")
        );
    }

    private void insertTrack(Long orderId, Long riderId, String stage, double longitude, double latitude) {
        if (riderId == null || riderId <= 0) {
            return;
        }
        jdbcTemplate.update("""
                insert into t_delivery_track(order_id, rider_id, stage, longitude, latitude, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                orderId,
                riderId,
                stage,
                longitude,
                latitude,
                Timestamp.valueOf(LocalDateTime.now()));
    }

    private List<DeliveryTrackPoint> listTrackPoints(Long orderId) {
        return jdbcTemplate.query("""
                select created_at, longitude, latitude, stage
                from t_delivery_track
                where order_id = ?
                order by created_at asc, id asc
                """,
                (rs, rowNum) -> new DeliveryTrackPoint(
                        rs.getTimestamp("created_at").toLocalDateTime().format(TRACK_TIME_FORMATTER),
                        rs.getDouble("longitude"),
                        rs.getDouble("latitude"),
                        rs.getString("stage")
                ),
                orderId);
    }

    public Map<String, Object> queryOrderBase(Long orderId) {
        return queryForSingle("""
                select o.id, o.order_no, o.status, o.amount, o.item_amount, o.delivery_fee, o.coupon_discount,
                       o.paid, o.payment_time, o.updated_at, o.remark, o.receiver_name, o.receiver_phone,
                       o.delivery_address, o.user_longitude, o.user_latitude, o.estimated_delivery_time,
                       o.cancel_reason, o.cancel_time,
                       o.user_id, o.merchant_id, o.rider_id,
                       u.name as user_name,
                       m.name as merchant_name, m.category, m.distance_km,
                       m.longitude as merchant_longitude, m.latitude as merchant_latitude,
                       m.delivery_mode, m.delivery_area_type, m.delivery_radius_km, m.delivery_polygon,
                       r.name as rider_name, r.current_longitude as rider_longitude, r.current_latitude as rider_latitude,
                       r.rider_type, r.service_address, r.service_longitude, r.service_latitude,
                       r.service_area_type, r.service_radius_km, r.service_polygon
                from t_order o
                join t_user u on u.id = o.user_id
                join t_merchant m on m.id = o.merchant_id
                left join t_rider r on r.id = o.rider_id
                where o.id = ?
                """, orderId);
    }

    private Map<String, Object> loginMerchantFromLegacySchema(String account, String password) {
        if (!Objects.equals(password, "merchant123")) {
            throw new ServiceException(401, "商家账号或密码不正确");
        }
        Long merchantId = switch (account == null ? "" : account.trim()) {
            case "13700000001" -> 1L;
            case "13700000002" -> 2L;
            case "13700000003" -> 3L;
            case "13700000004" -> 4L;
            case "13700000005" -> 5L;
            case "13700000006" -> 6L;
            case "13700000007" -> 7L;
            case "13700000008" -> 8L;
            case "13700000009" -> 9L;
            case "13700000010" -> 10L;
            default -> 0L;
        };
        if (merchantId == 0L) {
            throw new ServiceException(401, "商家账号或密码不正确");
        }
        Map<String, Object> row = queryForSingle("""
                select id, name, category, status
                from t_merchant
                where id = ?
                """, merchantId);
        if (row == null) {
            throw new ServiceException(401, "商家账号或密码不正确");
        }
        return Map.of(
                "merchantId", longValue(row.get("id"), merchantId),
                "name", stringValue(row.get("name"), ""),
                "phone", account.trim(),
                "category", stringValue(row.get("category"), ""),
                "status", stringValue(row.get("status"), "ACTIVE")
        );
    }

    private Map<String, Object> loginRiderFromLegacySchema(String phone, String password) {
        if (!Objects.equals(password, "rider123")) {
            throw new ServiceException(401, "骑手账号或密码不正确");
        }
        Map<String, Object> row = queryForSingle("""
                select id, name, phone, status, current_longitude, current_latitude
                from t_rider
                where phone = ?
                """, phone);
        if (row == null) {
            throw new ServiceException(401, "骑手账号或密码不正确");
        }
        return Map.of(
                "riderId", longValue(row.get("id"), 0L),
                "name", stringValue(row.get("name"), ""),
                "phone", stringValue(row.get("phone"), phone),
                "status", stringValue(row.get("status"), ""),
                "longitude", doubleValue(row.get("current_longitude"), 0.0),
                "latitude", doubleValue(row.get("current_latitude"), 0.0)
        );
    }

    private Map<String, Object> queryForSingle(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void evictMerchantCaches(Long merchantId) {
        redisCacheService.delete(MERCHANT_CACHE_PREFIX + "categories");
        redisCacheService.delete(MERCHANT_CACHE_PREFIX + "detail:" + merchantId);
        redisCacheService.delete(MERCHANT_CACHE_PREFIX + "menu-items:" + merchantId);
        redisCacheService.delete(MERCHANT_CACHE_PREFIX + "menu-details:" + merchantId);
        redisCacheService.delete(MERCHANT_CACHE_PREFIX + "nearby:summary");
        redisCacheService.deleteByPattern(MERCHANT_CACHE_PREFIX + "nearby:list:*");
    }

    private String cachePart(String value) {
        if (value == null || value.isBlank()) {
            return "all";
        }
        return Integer.toHexString(value.trim().toLowerCase().hashCode());
    }

    private OrderStatus mapStageToStatus(String stage) {
        if (stage == null) {
            return OrderStatus.DELIVERING;
        }
        return switch (stage) {
            case "商家接单", "MERCHANT_ACCEPTED" -> OrderStatus.MERCHANT_ACCEPTED;
            case "骑手接单", "RIDER_ACCEPTED" -> OrderStatus.RIDER_ACCEPTED;
            case "商家已出餐", "出餐完成", "MERCHANT_PREPARED" -> OrderStatus.MERCHANT_PREPARED;
            case "到店取餐", "前往商家", "ARRIVED_STORE" -> OrderStatus.ARRIVED_STORE;
            case "配送中", "DELIVERING" -> OrderStatus.DELIVERING;
            case "订单送达", "完成", "COMPLETED" -> OrderStatus.COMPLETED;
            case "已取消", "CANCELLED" -> OrderStatus.CANCELLED;
            default -> OrderStatus.DELIVERING;
        };
    }

    private OrderStatus parseOrderStatus(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (Exception exception) {
            return OrderStatus.CREATED;
        }
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String requiredText(Object value, String message) {
        String result = stringValue(value, "").trim();
        if (result.isEmpty()) {
            throw new ServiceException(400, message);
        }
        return result;
    }

    private String timestampText(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        return "";
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private Long longValue(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private double doubleValue(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value))
                || ("false".equalsIgnoreCase(String.valueOf(value)) ? false : defaultValue);
    }

    private BigDecimal decimalValue(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private Map<String, Object> linkedMap(Object... keyValues) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
