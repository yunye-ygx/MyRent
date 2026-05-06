package cn.yy.myrent.service.impl;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.entity.HouseFavorite;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.hot.HouseHotScoreSnapshot;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.vo.HouseFavoriteStatusVO;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseFavoriteServiceImplTest {

    @Mock
    private HouseFavoriteMapper houseFavoriteMapper;

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private HouseHotService houseHotService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private cn.yy.myrent.mapper.HouseHistoryMapper houseHistoryMapper;

    @Mock
    private cn.yy.myrent.mapper.ChatSessionMapper chatSessionMapper;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @SuppressWarnings("rawtypes")
    @Mock
    private HashOperations hashOperations;

    @InjectMocks
    private HouseFavoriteServiceImpl houseFavoriteService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void favoriteShouldIncrementHotScoreAfterCommitWhenCreatingNewActiveRelation() {
        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setCity("shanghai").setStatus(1));

        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<HouseFavorite> queryChain =
                Mockito.mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
        when(queryChain.eq(any(), any())).thenReturn(queryChain);
        when(queryChain.one()).thenReturn(null);

        HouseFavoriteServiceImpl serviceSpy = Mockito.spy(houseFavoriteService);
        doReturn(queryChain).when(serviceSpy).lambdaQuery();
        doReturn(true).when(serviceSpy).save(any(HouseFavorite.class));
        doReturn(statusVo(7L, true, 1L)).when(serviceSpy).getFavoriteStatus(7L, 1001L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            HouseFavoriteStatusVO result = serviceSpy.favorite(7L, 1001L);

            assertTrue(result.getFavorited());
            ArgumentCaptor<HouseFavorite> favoriteCaptor = ArgumentCaptor.forClass(HouseFavorite.class);
            verify(serviceSpy).save(favoriteCaptor.capture());
            assertEquals(7L, favoriteCaptor.getValue().getHouseId());
            assertEquals(1001L, favoriteCaptor.getValue().getUserId());
            assertEquals(1, favoriteCaptor.getValue().getStatus());
            verifyNoInteractions(houseHotService);

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(houseHotService).incrementFavoriteScore("shanghai", 7L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void favoriteShouldNotIncrementHotScoreWhenRelationIsAlreadyActive() {
        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setCity("shanghai").setStatus(1));

        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<HouseFavorite> queryChain =
                Mockito.mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
        when(queryChain.eq(any(), any())).thenReturn(queryChain);
        when(queryChain.one()).thenReturn(new HouseFavorite()
                .setId(9L)
                .setHouseId(7L)
                .setUserId(1001L)
                .setStatus(1));

        HouseFavoriteServiceImpl serviceSpy = Mockito.spy(houseFavoriteService);
        doReturn(queryChain).when(serviceSpy).lambdaQuery();
        doReturn(statusVo(7L, true, 1L)).when(serviceSpy).getFavoriteStatus(7L, 1001L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            HouseFavoriteStatusVO result = serviceSpy.favorite(7L, 1001L);

            assertTrue(result.getFavorited());
            verify(serviceSpy, never()).save(any(HouseFavorite.class));
            verify(serviceSpy, never()).updateById(any(HouseFavorite.class));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(houseHotService, never()).incrementFavoriteScore(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void favoriteShouldIncrementHotScoreAfterCommitWhenReactivatingInactiveRelation() {
        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setCity("shanghai").setStatus(1));

        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<HouseFavorite> queryChain =
                Mockito.mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
        when(queryChain.eq(any(), any())).thenReturn(queryChain);
        when(queryChain.one()).thenReturn(new HouseFavorite()
                .setId(9L)
                .setHouseId(7L)
                .setUserId(1001L)
                .setStatus(0));

        @SuppressWarnings("unchecked")
        LambdaUpdateChainWrapper<HouseFavorite> updateChain =
                Mockito.mock(LambdaUpdateChainWrapper.class, Answers.RETURNS_SELF);
        when(updateChain.set(any(), any())).thenReturn(updateChain);
        when(updateChain.eq(any(), any())).thenReturn(updateChain);
        when(updateChain.ne(any(), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);

        HouseFavoriteServiceImpl serviceSpy = Mockito.spy(houseFavoriteService);
        doReturn(queryChain).when(serviceSpy).lambdaQuery();
        doReturn(updateChain).when(serviceSpy).lambdaUpdate();
        doReturn(statusVo(7L, true, 1L)).when(serviceSpy).getFavoriteStatus(7L, 1001L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            HouseFavoriteStatusVO result = serviceSpy.favorite(7L, 1001L);

            assertTrue(result.getFavorited());
            verify(updateChain).update();
            verifyNoInteractions(houseHotService);

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(houseHotService).incrementFavoriteScore("shanghai", 7L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void favoriteShouldNotIncrementHotScoreWhenReactivationUpdateLosesRace() {
        when(houseMapper.selectById(7L)).thenReturn(new House().setId(7L).setCity("shanghai").setStatus(1));

        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<HouseFavorite> queryChain =
                Mockito.mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
        when(queryChain.eq(any(), any())).thenReturn(queryChain);
        when(queryChain.one()).thenReturn(new HouseFavorite()
                .setId(9L)
                .setHouseId(7L)
                .setUserId(1001L)
                .setStatus(0));

        @SuppressWarnings("unchecked")
        LambdaUpdateChainWrapper<HouseFavorite> updateChain =
                Mockito.mock(LambdaUpdateChainWrapper.class, Answers.RETURNS_SELF);
        when(updateChain.set(any(), any())).thenReturn(updateChain);
        when(updateChain.eq(any(), any())).thenReturn(updateChain);
        when(updateChain.ne(any(), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(false);

        HouseFavoriteServiceImpl serviceSpy = Mockito.spy(houseFavoriteService);
        doReturn(queryChain).when(serviceSpy).lambdaQuery();
        doReturn(updateChain).when(serviceSpy).lambdaUpdate();
        doReturn(statusVo(7L, true, 1L)).when(serviceSpy).getFavoriteStatus(7L, 1001L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            HouseFavoriteStatusVO result = serviceSpy.favorite(7L, 1001L);

            assertTrue(result.getFavorited());
            verify(updateChain).update();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(houseHotService, never()).incrementFavoriteScore(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private HouseFavoriteStatusVO statusVo(Long houseId, boolean favorited, long favoriteCount) {
        HouseFavoriteStatusVO statusVO = new HouseFavoriteStatusVO();
        statusVO.setHouseId(houseId);
        statusVO.setFavorited(favorited);
        statusVO.setFavoriteCount(favoriteCount);
        return statusVO;
    }

    @Test
    void incrementFavoriteScoreShouldWriteAtomicFavoriteDeltas() {
        HouseHotService realHouseHotService = new HouseHotService(
                stringRedisTemplate,
                houseMapper,
                houseFavoriteMapper,
                houseHistoryMapper,
                chatSessionMapper,
                objectMapper);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        realHouseHotService.incrementFavoriteScore("shanghai", 7L);

        verify(zSetOperations).incrementScore("house:hot:rank:city:shanghai", "7", 3D);
        verify(hashOperations).increment("house:hot:delta:favorite:total:city:shanghai", "7", 1L);
        verify(hashOperations).increment("house:hot:delta:favorite:recent:city:shanghai", "7", 1L);
    }

    @Test
    void queryHotHousesShouldMergeFavoriteDeltasIntoSnapshotCounts() throws Exception {
        HouseHotService realHouseHotService = new HouseHotService(
                stringRedisTemplate,
                houseMapper,
                houseFavoriteMapper,
                houseHistoryMapper,
                chatSessionMapper,
                objectMapper);
        HouseHotScoreSnapshot snapshot = new HouseHotScoreSnapshot();
        snapshot.setHouseId(7L);
        snapshot.setTotalFavoriteCount(5L);
        snapshot.setRecentFavoriteCount(2L);
        snapshot.setRecentBrowseCount(9L);
        snapshot.setRecentConsultCount(4L);
        snapshot.setFreshnessBonus(8D);
        snapshot.setHotScore(27D);

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(zSetOperations.reverseRangeWithScores("house:hot:rank:city:shanghai", 0, 9))
                .thenReturn(java.util.Set.of(new org.springframework.data.redis.core.DefaultTypedTuple<>("7", 30D)));
        when(hashOperations.multiGet(eq("house:hot:snapshot:city:shanghai"), any()))
                .thenReturn(java.util.List.of(objectMapper.writeValueAsString(snapshot)));
        when(hashOperations.multiGet(eq("house:hot:delta:favorite:total:city:shanghai"), any()))
                .thenReturn(java.util.List.of("1"));
        when(hashOperations.multiGet(eq("house:hot:delta:favorite:recent:city:shanghai"), any()))
                .thenReturn(java.util.List.of("1"));
        when(houseMapper.selectBatchIds(any())).thenReturn(java.util.List.of(
                new House().setId(7L).setCity("shanghai").setStatus(1).setTitle("studio")
        ));

        java.util.List<cn.yy.myrent.vo.HouseVO> result = realHouseHotService.queryHotHouses("shanghai", 0, 10);

        assertEquals(1, result.size());
        assertEquals(6L, result.get(0).getFavoriteCount());
        assertEquals(3L, result.get(0).getRecentFavoriteCount());
        assertEquals(30D, result.get(0).getHotScore());
    }
}
