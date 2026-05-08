package cn.yy.myrent.service.hot;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseHistoryMapper;
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
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
    private HouseHistoryMapper houseHistoryMapper;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private HouseHotService service;

    @Test
    void rebuildHotRankingShouldWriteCityScopedRankAndSnapshotKeys() throws Exception {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(houseMapper.selectAvailableHousesByCity("nanjing")).thenReturn(List.of(
                new House().setId(11L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(1)),
                new House().setId(12L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(10))
        ));
        when(houseFavoriteMapper.selectFavoriteAggRowsByHouseIds(any(), any())).thenReturn(List.of(
                favoriteAggRow(11L, 4L, 2L)
        ));
        when(houseHistoryMapper.selectBrowseCountsSinceByHouseIds(any(), any())).thenReturn(List.of(
                new HouseSignalCountRow(11L, 5L)
        ));
        when(chatSessionMapper.selectConsultCountsSinceByHouseIds(any(), any())).thenReturn(List.of(
                new HouseSignalCountRow(11L, 2L)
        ));

        service.rebuildHotRanking("nanjing");

        verify(houseMapper).selectAvailableHousesByCity("nanjing");
        verify(houseHistoryMapper).selectBrowseCountsSinceByHouseIds(any(), any());
        verify(chatSessionMapper).selectConsultCountsSinceByHouseIds(any(), any());
        verify(stringRedisTemplate).delete("house:hot:rank:city:nanjing");
        verify(stringRedisTemplate).delete("house:hot:snapshot:city:nanjing");
        verify(zSetOperations).add("house:hot:rank:city:nanjing", "11", 29D);
        verify(zSetOperations).add("house:hot:rank:city:nanjing", "12", 0D);
        verify(hashOperations).put("house:hot:snapshot:city:nanjing", "11", "{}");
        verify(setOperations).add("house:hot:cities", "nanjing");
    }

    @Test
    void rebuildAllHotRankingsShouldRemoveStaleCityCachesFromPreviousRanking() throws Exception {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("house:hot:cities")).thenReturn(Set.of("nanjing", "shanghai"));
        when(houseMapper.selectAvailableCities()).thenReturn(List.of("nanjing"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(houseMapper.selectAvailableHousesByCity("nanjing")).thenReturn(List.of(
                new House().setId(11L).setCity("nanjing").setStatus(1).setCreateTime(LocalDateTime.now().minusDays(1))
        ));
        when(houseFavoriteMapper.selectFavoriteAggRowsByHouseIds(any(), any())).thenReturn(List.of());
        when(houseHistoryMapper.selectBrowseCountsSinceByHouseIds(any(), any())).thenReturn(List.of());
        when(chatSessionMapper.selectConsultCountsSinceByHouseIds(any(), any())).thenReturn(List.of());

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

    private HouseFavoriteAggRow favoriteAggRow(Long houseId, Long totalFavoriteCount, Long recentFavoriteCount) {
        HouseFavoriteAggRow row = new HouseFavoriteAggRow();
        row.setHouseId(houseId);
        row.setTotalFavoriteCount(totalFavoriteCount);
        row.setRecentFavoriteCount(recentFavoriteCount);
        return row;
    }
}
