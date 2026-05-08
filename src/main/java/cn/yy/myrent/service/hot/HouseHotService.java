package cn.yy.myrent.service.hot;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseHistoryMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.vo.HouseVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseHotService {

    public static final String HOT_RANK_KEY_PREFIX = "house:hot:rank:city:";
    public static final String SNAPSHOT_KEY_PREFIX = "house:hot:snapshot:city:";
    public static final String HOT_CITY_INDEX_KEY = "house:hot:cities";

    private static final int HOUSE_STATUS_AVAILABLE = 1;
    private static final double CONSULT_WEIGHT = 5D;
    private static final double FAVORITE_WEIGHT = 3D;
    private static final double BROWSE_WEIGHT = 1D;
    private static final String TEMP_KEY_SUFFIX = ":tmp";
    private static final String REBUILD_LOCK_KEY_PREFIX = "house:hot:rebuild:lock:city:";
    private static final long REBUILD_LOCK_TTL_SECONDS = 30L;
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = buildReleaseLockScript();

    private final StringRedisTemplate stringRedisTemplate;
    private final HouseMapper houseMapper;
    private final HouseFavoriteMapper houseFavoriteMapper;
    private final HouseHistoryMapper houseHistoryMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;

    public void incrementFavoriteScore(String city, Long houseId) {
        if (!StringUtils.hasText(city) || houseId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().incrementScore(hotRankKey(city), String.valueOf(houseId), FAVORITE_WEIGHT);
    }

    public void incrementConsultScore(String city, Long houseId) {
        if (!StringUtils.hasText(city) || houseId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().incrementScore(hotRankKey(city), String.valueOf(houseId), CONSULT_WEIGHT);
    }

    public void rebuildHotRanking() {
        rebuildAllHotRankings();
    }

    public void rebuildAllHotRankings() {
        Set<String> trackedCities = loadTrackedCities();
        Set<String> availableCities = houseMapper.selectAvailableCities().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> staleCities = new LinkedHashSet<>(trackedCities);
        staleCities.removeAll(availableCities);
        clearCityCaches(staleCities);
        replaceTrackedCities(availableCities);

        int successCount = 0;
        for (String city : availableCities) {
            try {
                rebuildHotRanking(city, false);
                successCount++;
            } catch (Exception e) {
                log.warn("rebuild city hot ranking failed, city={}", city, e);
            }
        }

        log.info("rebuild all hot rankings finished, cityCount={}, successCount={}",
                availableCities.size(), successCount);
    }

    public void rebuildHotRanking(String city) {
        rebuildHotRanking(city, true);
    }

    private void rebuildHotRanking(String city, boolean updateCityIndex) {
        if (!StringUtils.hasText(city)) {
            return;
        }
        String lockKey = rebuildLockKey(city);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, REBUILD_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("skip rebuild city hot ranking because rebuild lock is held, city={}", city);
            return;
        }
        try {
            doRebuildHotRanking(city, updateCityIndex);
        } finally {
            releaseRebuildLock(lockKey, lockValue);
        }
    }

    private void doRebuildHotRanking(String city, boolean updateCityIndex) {
        LocalDate startDate = LocalDate.now().minusDays(6);
        LocalDateTime recentSince = LocalDateTime.now().minusDays(7);
        List<House> availableHouses = houseMapper.selectAvailableHousesByCity(city);
        if (CollectionUtils.isEmpty(availableHouses)) {
            clearCityCaches(List.of(city));
            log.info("skip rebuild city hot ranking because no available house exists, city={}", city);
            return;
        }

        List<Long> houseIds = availableHouses.stream()
                .map(House::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(houseIds)) {
            clearCityCaches(List.of(city));
            log.info("skip rebuild city hot ranking because no valid house id exists, city={}", city);
            return;
        }

        Map<Long, HouseFavoriteAggRow> favoriteAggMap = houseFavoriteMapper
                .selectFavoriteAggRowsByHouseIds(recentSince, houseIds)
                .stream()
                .collect(Collectors.toMap(HouseFavoriteAggRow::getHouseId, row -> row, (left, right) -> left));
        Map<Long, Long> recentBrowseMap = toCountMap(houseHistoryMapper.selectBrowseCountsSinceByHouseIds(startDate, houseIds));
        Map<Long, Long> recentConsultMap = toCountMap(chatSessionMapper.selectConsultCountsSinceByHouseIds(recentSince, houseIds));

        clearTempCityCaches(List.of(city));
        if (updateCityIndex) {
            stringRedisTemplate.opsForSet().add(HOT_CITY_INDEX_KEY, city);
        }

        int candidateCount = 0;
        for (House house : availableHouses) {
            if (house.getId() == null) {
                continue;
            }
            writeHouseHotRanking(city, house, favoriteAggMap, recentBrowseMap, recentConsultMap);
            candidateCount++;
        }
        swapCityCaches(city);

        log.info("rebuild city hot ranking finished, city={}, candidateCount={}", city, candidateCount);
    }

    public List<HouseVO> queryHotHouses(String city, int pageIndex, int pageSize) {
        if (!StringUtils.hasText(city)) {
            return Collections.emptyList();
        }

        long start = (long) pageIndex * pageSize;
        long end = start + pageSize - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(hotRankKey(city), start, end);
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

        return buildOrderedHouseVOs(city, houseIds, scoreMap);
    }

    public boolean hasHotRankingCache(String city) {
        if (!StringUtils.hasText(city)) {
            return false;
        }
        Long size = stringRedisTemplate.opsForZSet().zCard(hotRankKey(city));
        return size != null && size > 0;
    }

    public void removeHouseFromAllHotRankings(Long houseId) {
        if (houseId == null) {
            return;
        }
        Set<String> cities = loadTrackedCities();
        if (CollectionUtils.isEmpty(cities)) {
            return;
        }

        String houseIdValue = String.valueOf(houseId);
        for (String city : cities) {
            stringRedisTemplate.opsForZSet().remove(hotRankKey(city), houseIdValue);
            stringRedisTemplate.opsForHash().delete(snapshotKey(city), houseIdValue);
        }
    }

    private Map<Long, Long> toCountMap(List<HouseSignalCountRow> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyMap();
        }
        return rows.stream()
                .filter(row -> row != null && row.houseId() != null)
                .collect(Collectors.toMap(HouseSignalCountRow::houseId,
                        row -> row.count() == null ? 0L : row.count(),
                        Long::sum));
    }

    private void clearCityCaches(Collection<String> cities) {
        if (CollectionUtils.isEmpty(cities)) {
            return;
        }
        for (String city : cities) {
            stringRedisTemplate.delete(hotRankKey(city));
            stringRedisTemplate.delete(snapshotKey(city));
        }
    }

    private void clearTempCityCaches(Collection<String> cities) {
        if (CollectionUtils.isEmpty(cities)) {
            return;
        }
        for (String city : cities) {
            stringRedisTemplate.delete(tempHotRankKey(city));
            stringRedisTemplate.delete(tempSnapshotKey(city));
        }
    }

    private Set<String> loadTrackedCities() {
        Set<String> cities = stringRedisTemplate.opsForSet().members(HOT_CITY_INDEX_KEY);
        if (CollectionUtils.isEmpty(cities)) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(cities);
    }

    private void replaceTrackedCities(Collection<String> cities) {
        stringRedisTemplate.delete(HOT_CITY_INDEX_KEY);
        if (CollectionUtils.isEmpty(cities)) {
            return;
        }
        stringRedisTemplate.opsForSet().add(HOT_CITY_INDEX_KEY, cities.toArray(String[]::new));
    }

    private List<HouseVO> buildOrderedHouseVOs(String city, List<Long> houseIds, Map<Long, Double> scoreMap) {
        if (CollectionUtils.isEmpty(houseIds)) {
            return Collections.emptyList();
        }

        List<House> houses = houseMapper.selectBatchIds(houseIds);
        Map<Long, House> houseMap = houses.stream()
                .filter(house -> house.getStatus() != null && house.getStatus() == HOUSE_STATUS_AVAILABLE)
                .filter(house -> city.equals(house.getCity()))
                .collect(Collectors.toMap(House::getId, house -> house, (left, right) -> left));

        Map<Long, HouseHotScoreSnapshot> snapshotMap = readSnapshots(city, houseIds);
        List<HouseVO> result = new ArrayList<>();
        for (Long houseId : houseIds) {
            House house = houseMap.get(houseId);
            if (house == null) {
                continue;
            }
            HouseVO vo = convertHouseToVo(house);
            HouseHotScoreSnapshot snapshot = snapshotMap.get(houseId);
            double currentScore = roundScore(scoreMap.getOrDefault(houseId, 0D));
            if (snapshot != null) {
                vo.setFavoriteCount(snapshot.getTotalFavoriteCount());
                vo.setRecentFavoriteCount(snapshot.getRecentFavoriteCount());
                vo.setRecentConsultCount(snapshot.getRecentConsultCount());
                vo.setHotScore(currentScore);
            } else {
                vo.setHotScore(currentScore);
            }
            result.add(vo);
        }
        return result;
    }

    private void writeHouseHotRanking(String city,
                                      House house,
                                      Map<Long, HouseFavoriteAggRow> favoriteAggMap,
                                      Map<Long, Long> recentBrowseMap,
                                      Map<Long, Long> recentConsultMap) {
        HouseFavoriteAggRow favoriteAgg = favoriteAggMap.get(house.getId());
        long totalFavoriteCount = favoriteAgg == null || favoriteAgg.getTotalFavoriteCount() == null
                ? 0L : favoriteAgg.getTotalFavoriteCount();
        long recentFavoriteCount = favoriteAgg == null || favoriteAgg.getRecentFavoriteCount() == null
                ? 0L : favoriteAgg.getRecentFavoriteCount();
        long recentBrowseCount = recentBrowseMap.getOrDefault(house.getId(), 0L);
        long recentConsultCount = recentConsultMap.getOrDefault(house.getId(), 0L);
        double freshnessBonus = freshnessBonus(house.getCreateTime());
        double hotScore = calculateHotScore(recentFavoriteCount, recentConsultCount, recentBrowseCount, freshnessBonus);

        HouseHotScoreSnapshot snapshot = new HouseHotScoreSnapshot();
        snapshot.setHouseId(house.getId());
        snapshot.setTotalFavoriteCount(totalFavoriteCount);
        snapshot.setRecentFavoriteCount(recentFavoriteCount);
        snapshot.setRecentBrowseCount(recentBrowseCount);
        snapshot.setRecentConsultCount(recentConsultCount);
        snapshot.setFreshnessBonus(freshnessBonus);
        snapshot.setHotScore(hotScore);

        stringRedisTemplate.opsForZSet().add(tempHotRankKey(city), String.valueOf(house.getId()), hotScore);
        writeSnapshot(tempSnapshotKey(city), snapshot);
    }

    private void swapCityCaches(String city) {
        stringRedisTemplate.rename(tempHotRankKey(city), hotRankKey(city));
        stringRedisTemplate.rename(tempSnapshotKey(city), snapshotKey(city));
    }

    private void writeSnapshot(String snapshotKey, HouseHotScoreSnapshot snapshot) {
        try {
            stringRedisTemplate.opsForHash().put(snapshotKey,
                    String.valueOf(snapshot.getHouseId()),
                    objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize hot snapshot failed", e);
        }
    }

    private Map<Long, HouseHotScoreSnapshot> readSnapshots(String city, Collection<Long> houseIds) {
        List<Object> values = stringRedisTemplate.opsForHash().multiGet(snapshotKey(city),
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
                log.warn("parse hot snapshot failed, city={}, houseId={}", city, houseId, e);
            }
        }
        return result;
    }

    private double calculateHotScore(long recentFavoriteCount,
                                     long recentConsultCount,
                                     long recentBrowseCount,
                                     double freshnessBonus) {
        return recentConsultCount * CONSULT_WEIGHT
                + recentFavoriteCount * FAVORITE_WEIGHT
                + recentBrowseCount * BROWSE_WEIGHT
                + freshnessBonus;
    }

    private double freshnessBonus(LocalDateTime createTime) {
        if (createTime == null) {
            return 0D;
        }
        long ageDays = ChronoUnit.DAYS.between(createTime.toLocalDate(), LocalDate.now());
        if (ageDays <= 3) {
            return 8D;
        }
        if (ageDays <= 7) {
            return 4D;
        }
        return 0D;
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
        vo.setCity(house.getCity());
        vo.setRegion(house.getRegion());
        vo.setNearSubway(house.getNearSubway() != null && house.getNearSubway() == 1);
        vo.setPrivateBathroom(house.getPrivateBathroom() != null && house.getPrivateBathroom() == 1);
        vo.setHasBalcony(house.getHasBalcony() != null && house.getHasBalcony() == 1);
        vo.setCivilWaterElectric(house.getCivilWaterElectric() != null && house.getCivilWaterElectric() == 1);
        vo.setSupportStudentDepositFree(house.getSupportStudentDepositFree() != null
                && house.getSupportStudentDepositFree() == 1);
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

    private String hotRankKey(String city) {
        return HOT_RANK_KEY_PREFIX + city;
    }

    private String snapshotKey(String city) {
        return SNAPSHOT_KEY_PREFIX + city;
    }

    private String tempHotRankKey(String city) {
        return hotRankKey(city) + TEMP_KEY_SUFFIX;
    }

    private String tempSnapshotKey(String city) {
        return snapshotKey(city) + TEMP_KEY_SUFFIX;
    }

    private String rebuildLockKey(String city) {
        return REBUILD_LOCK_KEY_PREFIX + city;
    }

    private void releaseRebuildLock(String lockKey, String lockValue) {
        try {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
        } catch (Exception e) {
            log.warn("release city hot rebuild lock failed, lockKey={}", lockKey, e);
        }
    }

    private static DefaultRedisScript<Long> buildReleaseLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """);
        script.setResultType(Long.class);
        return script;
    }

}
