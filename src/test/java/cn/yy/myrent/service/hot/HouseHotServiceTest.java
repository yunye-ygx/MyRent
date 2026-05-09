package cn.yy.myrent.service.hot;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseHotDailyStatsMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.vo.HouseVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseHotServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private HouseFavoriteMapper houseFavoriteMapper;

    @Mock
    private HouseHotDailyStatsMapper houseHotDailyStatsMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private HouseHotService service;

    @Test
    void rebuildHotRankingShouldWriteCityScopedRankAndSnapshotKeys() throws Exception {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(houseMapper.selectAvailableHousesByCity("nanjing")).thenReturn(List.of(
                new House().setId(11L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(1)),
                new House().setId(12L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(10))
        ));
        when(houseFavoriteMapper.selectFavoriteTotalAggRowsByHouseIds(any())).thenReturn(List.of(
                favoriteAggRow(11L, 4L, 2L)
        ));
        when(houseHotDailyStatsMapper.selectRecentAggRowsByCity(eq("nanjing"), any())).thenReturn(List.of(
                new HouseHotDailyStatsAggRow(11L, 5L, 2L, 2L)
        ));

        service.rebuildHotRanking("nanjing");

        verify(houseMapper).selectAvailableHousesByCity("nanjing");
        verify(houseHotDailyStatsMapper).selectRecentAggRowsByCity(eq("nanjing"), any());
        verify(stringRedisTemplate).delete("house:hot:rank:city:nanjing:tmp");
        verify(stringRedisTemplate).delete("house:hot:snapshot:city:nanjing:tmp");
        verify(zSetOperations).add("house:hot:rank:city:nanjing:tmp", "11", 32.2188758248682D);
        verify(zSetOperations).add("house:hot:rank:city:nanjing:tmp", "12", 0D);
        verify(hashOperations).put("house:hot:snapshot:city:nanjing:tmp", "11", "{}");
        verify(stringRedisTemplate).rename("house:hot:rank:city:nanjing:tmp", "house:hot:rank:city:nanjing");
        verify(stringRedisTemplate).rename("house:hot:snapshot:city:nanjing:tmp", "house:hot:snapshot:city:nanjing");
        verify(stringRedisTemplate, never()).delete("house:hot:rank:city:nanjing");
        verify(stringRedisTemplate, never()).delete("house:hot:snapshot:city:nanjing");
        verify(setOperations).add("house:hot:cities", "nanjing");
    }

    @Test
    void rebuildHotRankingShouldIncludeLongTermFavoriteQualityScore() throws Exception {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(houseMapper.selectAvailableHousesByCity("nanjing")).thenReturn(List.of(
                new House().setId(21L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(30))
        ));
        when(houseFavoriteMapper.selectFavoriteTotalAggRowsByHouseIds(any())).thenReturn(List.of(
                favoriteAggRow(21L, 9L, 0L)
        ));
        when(houseHotDailyStatsMapper.selectRecentAggRowsByCity(eq("nanjing"), any())).thenReturn(List.of());

        service.rebuildHotRanking("nanjing");

        verify(zSetOperations).add(
                eq("house:hot:rank:city:nanjing:tmp"),
                eq("21"),
                doubleThat(score -> Math.abs(score - 4.605170185988092D) < 0.000001D)
        );
    }

    @Test
    void rebuildHotRankingShouldOnlyWriteTopCandidateLimit() throws Exception {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        List<House> houses = new java.util.ArrayList<>();
        List<HouseFavoriteAggRow> favoriteRows = new java.util.ArrayList<>();
        for (long id = 1L; id <= 201L; id++) {
            houses.add(new House().setId(id).setCity("nanjing").setStatus(1)
                    .setCreateTime(LocalDateTime.now().minusDays(30)));
            favoriteRows.add(favoriteAggRow(id, id, 0L));
        }
        when(houseMapper.selectAvailableHousesByCity("nanjing")).thenReturn(houses);
        when(houseFavoriteMapper.selectFavoriteTotalAggRowsByHouseIds(any())).thenReturn(favoriteRows);
        when(houseHotDailyStatsMapper.selectRecentAggRowsByCity(eq("nanjing"), any())).thenReturn(List.of());

        service.rebuildHotRanking("nanjing");

        verify(zSetOperations, times(200)).add(eq("house:hot:rank:city:nanjing:tmp"), anyString(), anyDouble());
        verify(zSetOperations, never()).add(eq("house:hot:rank:city:nanjing:tmp"), eq("1"), anyDouble());
    }

    @Test
    void rebuildAllHotRankingsShouldRemoveStaleCityCachesFromPreviousRanking() throws Exception {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(setOperations.members("house:hot:cities")).thenReturn(Set.of("nanjing", "shanghai"));
        when(houseMapper.selectAvailableCities()).thenReturn(List.of("nanjing"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(houseMapper.selectAvailableHousesByCity("nanjing")).thenReturn(List.of(
                new House().setId(11L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(1))
        ));
        when(houseFavoriteMapper.selectFavoriteTotalAggRowsByHouseIds(any())).thenReturn(List.of());
        when(houseHotDailyStatsMapper.selectRecentAggRowsByCity(eq("nanjing"), any())).thenReturn(List.of());

        service.rebuildAllHotRankings();

        verify(stringRedisTemplate).delete("house:hot:rank:city:shanghai");
        verify(stringRedisTemplate).delete("house:hot:snapshot:city:shanghai");
        verify(stringRedisTemplate).delete("house:hot:cities");
        verify(setOperations).add("house:hot:cities", "nanjing");
    }

    @Test
    void rebuildAllHotRankingsShouldClearTrackedCityCachesWhenNoAvailableHousesRemain() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("house:hot:cities")).thenReturn(Set.of("nanjing", "shanghai"));
        when(houseMapper.selectAvailableCities()).thenReturn(Collections.emptyList());

        service.rebuildAllHotRankings();

        verify(stringRedisTemplate).delete("house:hot:rank:city:nanjing");
        verify(stringRedisTemplate).delete("house:hot:snapshot:city:nanjing");
        verify(stringRedisTemplate).delete("house:hot:rank:city:shanghai");
        verify(stringRedisTemplate).delete("house:hot:snapshot:city:shanghai");
        verify(stringRedisTemplate).delete("house:hot:cities");
    }

    @Test
    void rebuildHotRankingByCityShouldClearCityWhenNoAvailableHouses() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(houseMapper.selectAvailableHousesByCity("shanghai")).thenReturn(Collections.emptyList());

        service.rebuildHotRanking("shanghai");

        verify(stringRedisTemplate).delete("house:hot:rank:city:shanghai");
        verify(stringRedisTemplate).delete("house:hot:snapshot:city:shanghai");
    }

    @Test
    void rebuildHotRankingByCityShouldSkipBlankCity() {
        service.rebuildHotRanking(" ");

        verify(houseMapper, never()).selectAvailableHousesByCity(anyString());
    }

    @Test
    void queryHotHousesShouldReadFromRequestedCityKey() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(zSetOperations.reverseRangeWithScores("house:hot:rank:city:nanjing", 0, 9))
                .thenReturn(Set.of(new DefaultTypedTuple<>("11", 29D)));
        when(hashOperations.multiGet(anyString(), any())).thenReturn(Collections.emptyList());
        when(houseMapper.selectBatchIds(List.of(11L))).thenReturn(List.of(
                new House().setId(11L).setCity("nanjing").setStatus(1).setTitle("studio")
        ));

        List<HouseVO> result = service.queryHotHouses("nanjing", 0, 10);

        verify(zSetOperations).reverseRangeWithScores("house:hot:rank:city:nanjing", 0, 9);
        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getId());
        assertEquals(29D, result.get(0).getHotScore());
    }

    @Test
    void queryHotHousesShouldNotReadBeyondTopDisplayLimit() {
        List<HouseVO> result = service.queryHotHouses("nanjing", 3, 20);

        assertEquals(0, result.size());
        verify(stringRedisTemplate, never()).opsForZSet();
    }

    @Test
    void queryHotHousesShouldExcludeHousesWhoseCurrentCityNoLongerMatchesRequestedCity() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(zSetOperations.reverseRangeWithScores("house:hot:rank:city:nanjing", 0, 9))
                .thenReturn(Set.of(
                        new DefaultTypedTuple<>("11", 29D),
                        new DefaultTypedTuple<>("12", 18D)
                ));
        when(hashOperations.multiGet(anyString(), any())).thenReturn(Collections.emptyList());
        when(houseMapper.selectBatchIds(any())).thenReturn(List.of(
                new House().setId(11L).setCity("shanghai").setStatus(1).setTitle("wrong city"),
                new House().setId(12L).setCity("nanjing").setStatus(1).setTitle("correct city")
        ));

        List<HouseVO> result = service.queryHotHouses("nanjing", 0, 10);

        assertEquals(1, result.size());
        assertEquals(12L, result.get(0).getId());
    }

    @Test
    void hasHotRankingCacheShouldCheckRequestedCityKey() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard("house:hot:rank:city:nanjing")).thenReturn(3L);

        boolean hasCache = service.hasHotRankingCache("nanjing");

        verify(zSetOperations).zCard("house:hot:rank:city:nanjing");
        assertEquals(true, hasCache);
    }

    @Test
    void removeHouseFromAllHotRankingsShouldRemoveRankAndSnapshotFromTrackedCities() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.members("house:hot:cities")).thenReturn(Set.of("nanjing", "shanghai"));

        service.removeHouseFromAllHotRankings(7L);

        verify(zSetOperations).remove("house:hot:rank:city:nanjing", "7");
        verify(hashOperations).delete("house:hot:snapshot:city:nanjing", "7");
        verify(zSetOperations).remove("house:hot:rank:city:shanghai", "7");
        verify(hashOperations).delete("house:hot:snapshot:city:shanghai", "7");
    }

    @Test
    void incrementFavoriteScoreShouldIncrementCityRankOnly() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service.incrementFavoriteScore("shanghai", 7L);

        verify(zSetOperations).incrementScore("house:hot:rank:city:shanghai", "7", 3D);
    }

    @Test
    void incrementConsultScoreShouldIncrementCityRankOnly() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service.incrementConsultScore("shanghai", 7L);

        verify(zSetOperations).incrementScore("house:hot:rank:city:shanghai", "7", 5D);
    }

    @Test
    void rebuildHotRankingShouldSerializeConcurrentRebuildsForSameCity() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true)
                .thenReturn(false);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(houseFavoriteMapper.selectFavoriteTotalAggRowsByHouseIds(any())).thenReturn(List.of());
        when(houseHotDailyStatsMapper.selectRecentAggRowsByCity(eq("nanjing"), any())).thenReturn(List.of());
        when(houseMapper.selectAvailableHousesByCity("nanjing")).thenReturn(List.of(
                new House().setId(11L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(1))
        ));

        service.rebuildHotRanking("nanjing");
        service.rebuildHotRanking("nanjing");

        verify(houseMapper).selectAvailableHousesByCity("nanjing");
        verify(valueOperations, org.mockito.Mockito.times(2))
                .setIfAbsent(eq("house:hot:rebuild:lock:city:nanjing"), anyString(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void rebuildHotRankingShouldReleaseRedisLockAfterRebuild() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(stringRedisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(houseMapper.selectAvailableHousesByCity("nanjing")).thenReturn(List.of(
                new House().setId(11L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(1))
        ));
        when(houseFavoriteMapper.selectFavoriteTotalAggRowsByHouseIds(any())).thenReturn(List.of());
        when(houseHotDailyStatsMapper.selectRecentAggRowsByCity(eq("nanjing"), any())).thenReturn(List.of());

        service.rebuildHotRanking("nanjing");

        verify(stringRedisTemplate).execute(any(), eq(List.of("house:hot:rebuild:lock:city:nanjing")), anyString());
    }

    private HouseFavoriteAggRow favoriteAggRow(Long houseId, Long totalFavoriteCount, Long recentFavoriteCount) {
        HouseFavoriteAggRow row = new HouseFavoriteAggRow();
        row.setHouseId(houseId);
        row.setTotalFavoriteCount(totalFavoriteCount);
        row.setRecentFavoriteCount(recentFavoriteCount);
        return row;
    }
}
