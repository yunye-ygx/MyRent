package cn.yy.myrent.service.hot;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.vo.HouseVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseHotService {

    public static final String HOT_RANK_KEY = "house:hot:rank:global";

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final long DAILY_METRIC_TTL_DAYS = 15;
    private static final long DEDUP_TTL_DAYS = 2;
    private static final String DAILY_CONSULT_KEY_PREFIX = "house:hot:metric:consult:";
    private static final String DAILY_REPLY_KEY_PREFIX = "house:hot:metric:reply:";
    private static final String DEDUP_KEY_PREFIX = "house:hot:dedup:";
    private static final String SNAPSHOT_KEY = "house:hot:snapshot";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;
    private final HouseMapper houseMapper;
    private final HouseFavoriteMapper houseFavoriteMapper;
    private final ObjectMapper objectMapper;

    public void recordChatInteraction(Long houseId, Long senderId, Long receiverId) {
        if (houseId == null || senderId == null || receiverId == null) {
            return;
        }

        House house = houseMapper.selectById(houseId);
        if (house == null || house.getPublisherUserId() == null) {
            return;
        }
        if (house.getStatus() == null || house.getStatus() != HOUSE_STATUS_AVAILABLE) {
            return;
        }

        LocalDate today = LocalDate.now();
        if (senderId.equals(house.getPublisherUserId())) {
            addDailyMetricIfAbsent(dailyReplyKey(today), dedupKey("reply", today, houseId, receiverId), houseId);
            return;
        }
        addDailyMetricIfAbsent(dailyConsultKey(today), dedupKey("consult", today, houseId, senderId), houseId);
    }

    public void rebuildHotRanking() {
        //获取当前时间往前推 7 天的时间点
        LocalDateTime recentSince = LocalDateTime.now().minusDays(7);
        List<House> availableHouses = houseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<House>()
                        .eq("status", HOUSE_STATUS_AVAILABLE));

        if (CollectionUtils.isEmpty(availableHouses)) {
            stringRedisTemplate.delete(HOT_RANK_KEY);
            stringRedisTemplate.delete(SNAPSHOT_KEY);
            log.info("skip rebuild hot ranking because no available house exists");
            return;
        }

        Map<Long, HouseFavoriteAggRow> favoriteAggMap = houseFavoriteMapper.selectFavoriteAggRows(recentSince)
                .stream()
                .collect(Collectors.toMap(HouseFavoriteAggRow::getHouseId, row -> row, (left, right) -> left));
        Map<Long, Long> recentConsultMap = aggregateDailyMetrics(DAILY_CONSULT_KEY_PREFIX, 7);
        Map<Long, Long> recentReplyMap = aggregateDailyMetrics(DAILY_REPLY_KEY_PREFIX, 7);

        stringRedisTemplate.delete(HOT_RANK_KEY);
        stringRedisTemplate.delete(SNAPSHOT_KEY);

        for (House house : availableHouses) {
            HouseFavoriteAggRow favoriteAgg = favoriteAggMap.get(house.getId());
            long totalFavoriteCount = favoriteAgg == null || favoriteAgg.getTotalFavoriteCount() == null
                    ? 0L : favoriteAgg.getTotalFavoriteCount();
            long recentFavoriteCount = favoriteAgg == null || favoriteAgg.getRecentFavoriteCount() == null
                    ? 0L : favoriteAgg.getRecentFavoriteCount();
            long recentConsultCount = recentConsultMap.getOrDefault(house.getId(), 0L);
            long recentReplyCount = recentReplyMap.getOrDefault(house.getId(), 0L);

            double hotScore = calculateHotScore(recentFavoriteCount, recentConsultCount, recentReplyCount, totalFavoriteCount);

            HouseHotScoreSnapshot snapshot = new HouseHotScoreSnapshot();
            snapshot.setHouseId(house.getId());
            snapshot.setTotalFavoriteCount(totalFavoriteCount);
            snapshot.setRecentFavoriteCount(recentFavoriteCount);
            snapshot.setRecentConsultCount(recentConsultCount);
            snapshot.setRecentReplyCount(recentReplyCount);
            snapshot.setHotScore(hotScore);

            stringRedisTemplate.opsForZSet().add(HOT_RANK_KEY, String.valueOf(house.getId()), hotScore);
            writeSnapshot(snapshot);
        }

        log.info("rebuild hot ranking finished, availableHouseCount={}", availableHouses.size());
    }

    public List<HouseVO> queryHotHouses(int pageIndex, int pageSize) {
        long start = (long) pageIndex * pageSize;
        long end = start + pageSize - 1;

        //前top10
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(HOT_RANK_KEY, start, end);
        if (CollectionUtils.isEmpty(tuples)) {
            return Collections.emptyList();
        }

        List<Long> houseIds = new ArrayList<>();
        Map<Long, Double> scoreMap = new HashMap<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple == null || tuple.getValue() == null) {
                continue;
            }
            Long houseId = Long.valueOf(tuple.getValue());
            houseIds.add(houseId);
            scoreMap.put(houseId, tuple.getScore() == null ? 0D : tuple.getScore());
        }

        return buildOrderedHouseVOs(houseIds, scoreMap);
    }

    public boolean hasHotRankingCache() {
        Long size = stringRedisTemplate.opsForZSet().zCard(HOT_RANK_KEY);
        return size != null && size > 0;
    }

    private List<HouseVO> buildOrderedHouseVOs(List<Long> houseIds, Map<Long, Double> scoreMap) {
        if (CollectionUtils.isEmpty(houseIds)) {
            return Collections.emptyList();
        }

        List<House> houses = houseMapper.selectBatchIds(houseIds);
        Map<Long, House> houseMap = houses.stream()
                .filter(house -> house.getStatus() != null && house.getStatus() == HOUSE_STATUS_AVAILABLE)
                .collect(Collectors.toMap(House::getId, house -> house, (left, right) -> left));

        //可能长这样[1001:{"houseId": 101,"totalFavoriteCount": 12,"recentFavoriteCount": 3,"recentConsultCount": 5,"recentReplyCount": 2,"hotScore": 41.8}]
        Map<Long, HouseHotScoreSnapshot> snapshotMap = readSnapshots(houseIds);
        List<HouseVO> result = new ArrayList<>();
        for (Long houseId : houseIds) {
            House house = houseMap.get(houseId);
            if (house == null) {
                continue;
            }
            HouseVO vo = convertHouseToVo(house);
            HouseHotScoreSnapshot snapshot = snapshotMap.get(houseId);
            if (snapshot != null) {
                vo.setFavoriteCount(snapshot.getTotalFavoriteCount());
                vo.setRecentFavoriteCount(snapshot.getRecentFavoriteCount());
                vo.setRecentConsultCount(snapshot.getRecentConsultCount());
                vo.setRecentReplyCount(snapshot.getRecentReplyCount());
                vo.setHotScore(roundScore(snapshot.getHotScore()));
            } else {
                vo.setHotScore(roundScore(scoreMap.getOrDefault(houseId, 0D)));
            }
            result.add(vo);
        }
        return result;
    }

    private Map<Long, Long> aggregateDailyMetrics(String keyPrefix, int days) {
        Map<Long, Long> result = new HashMap<>();
        for (int i = 0; i < days; i++) {
            String key = keyPrefix + LocalDate.now().minusDays(i).format(DAY_FORMATTER);
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                Long houseId = Long.valueOf(String.valueOf(entry.getKey()));
                long count = Long.parseLong(String.valueOf(entry.getValue()));
                result.merge(houseId, count, Long::sum);
            }
        }
        return result;
    }

    private void addDailyMetricIfAbsent(String metricKey, String dedupKey, Long houseId) {
        Boolean firstSeen = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", DEDUP_TTL_DAYS, TimeUnit.DAYS);
        if (!Boolean.TRUE.equals(firstSeen)) {
            return;
        }
        stringRedisTemplate.opsForHash().increment(metricKey, String.valueOf(houseId), 1L);
        stringRedisTemplate.expire(metricKey, DAILY_METRIC_TTL_DAYS, TimeUnit.DAYS);
    }

    private void writeSnapshot(HouseHotScoreSnapshot snapshot) {
        try {
            stringRedisTemplate.opsForHash().put(SNAPSHOT_KEY,
                    String.valueOf(snapshot.getHouseId()),
                    objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize hot snapshot failed", e);
        }
    }

    //获取总收藏数，近7天收藏数，近7天咨询数，近7天回复数
    private Map<Long, HouseHotScoreSnapshot> readSnapshots(Collection<Long> houseIds) {
        List<Object> values = stringRedisTemplate.opsForHash().multiGet(SNAPSHOT_KEY,
                houseIds.stream().map(String::valueOf).collect(Collectors.toList()));
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, HouseHotScoreSnapshot> result = new LinkedHashMap<>();
        int index = 0;
        for (Long houseId : houseIds) {
            Object value = index < values.size() ? values.get(index) : null;
            index++;
            if (value == null) {
                continue;
            }
            try {
                result.put(houseId, objectMapper.readValue(String.valueOf(value), HouseHotScoreSnapshot.class));
            } catch (JsonProcessingException e) {
                log.warn("parse hot snapshot failed, houseId={}", houseId, e);
            }
        }
        return result;
    }

    private double calculateHotScore(long recentFavoriteCount,
                                     long recentConsultCount,
                                     long recentReplyCount,
                                     long totalFavoriteCount) {
        return recentFavoriteCount * 6D
                + recentConsultCount * 4D
                + recentReplyCount * 2D
                + Math.log1p(totalFavoriteCount) * 3D;
    }

    private double roundScore(double score) {
        return BigDecimal.valueOf(score)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private HouseVO convertHouseToVo(House house) {
        HouseVO vo = new HouseVO();
        vo.setId(house.getId());
        vo.setPublisherUserId(house.getPublisherUserId());
        vo.setTitle(house.getTitle());
        vo.setStatus(house.getStatus());
        if (house.getPrice() != null) {
            vo.setPrice(BigDecimal.valueOf(house.getPrice())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        if (house.getDepositAmount() != null) {
            vo.setDepositAmount(BigDecimal.valueOf(house.getDepositAmount())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return vo;
    }

    private String dailyConsultKey(LocalDate day) {
        return DAILY_CONSULT_KEY_PREFIX + day.format(DAY_FORMATTER);
    }

    private String dailyReplyKey(LocalDate day) {
        return DAILY_REPLY_KEY_PREFIX + day.format(DAY_FORMATTER);
    }

    private String dedupKey(String type, LocalDate day, Long houseId, Long userId) {
        return DEDUP_KEY_PREFIX + type + ":" + day.format(DAY_FORMATTER) + ":" + houseId + ":" + userId;
    }
}
