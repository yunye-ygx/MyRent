package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.sync.house.HouseSyncConstants;
import cn.yy.myrent.sync.house.HouseSyncDispatcher;
import cn.yy.myrent.sync.house.model.HouseSyncContext;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseVO;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 房源信息表服务实现。
 */
@Service
public class HouseServiceImpl extends ServiceImpl<HouseMapper, House> implements IHouseService {

    private static final Logger log = LoggerFactory.getLogger(HouseServiceImpl.class);

    private static final long ES_QUERY_TIMEOUT_MS = 1200;

    private static final String FALLBACK_SOURCE_ES = "ES";
    private static final String FALLBACK_SOURCE_REDIS_HOT = "REDIS_HOT";
    private static final String FALLBACK_SOURCE_DB_CITY_HOT = "DB_CITY_HOT";

    private static final String TIP_ES_DOWN = "附近房源加载异常，已为你展示推荐房源";
    private static final String TIP_OUT_OF_RANGE = "当前房源不在搜索范围内";

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private HouseSyncDispatcher houseSyncDispatcher;

    @Override
    public HouseSearchResultVO searchNearbyHouse(SearchHouseReqDTO reqDTO) {
        double lat = reqDTO.getLatitude();
        double lon = reqDTO.getLongitude();
        double radiusMeters = parseRadiusMeters(reqDTO.getRadius());
        String distanceStr = ((int) radiusMeters) + "m";
        String city = reqDTO.getCity();

        int pageIndex = (reqDTO.getPage() != null ? reqDTO.getPage() : 1) - 1;
        int pageSize = reqDTO.getSize() != null ? reqDTO.getSize() : 10;

        try {
            List<HouseVO> esResult = CompletableFuture.supplyAsync(() -> searchInEs(lat, lon, distanceStr, pageIndex, pageSize))
                    .get(ES_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (esResult.isEmpty()) {
                log.info("ES搜索成功但范围内无房源，lat={}, lon={}, radius={}m, pageIndex={}, pageSize={}",
                        lat,
                        lon,
                        radiusMeters,
                        pageIndex,
                        pageSize);
                return buildSearchResult(esResult, false, FALLBACK_SOURCE_ES, TIP_OUT_OF_RANGE);
            }
            return buildSearchResult(esResult, false, FALLBACK_SOURCE_ES, null);
        } catch (TimeoutException te) {
            log.warn("ES搜索超时({}ms)，进入兜底策略，lat={}, lon={}, radius={}m", ES_QUERY_TIMEOUT_MS, lat, lon, radiusMeters);
        } catch (Exception e) {
            log.error("ES搜索异常，进入兜底策略，lat={}, lon={}, radius={}m", lat, lon, radiusMeters, e);
        }

        return searchWhenEsUnavailable(city, pageIndex, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createHouseWithSync(House house) {
        if (house == null) {
            return false;
        }
        boolean saved = this.save(house);
        if (!saved || house.getId() == null) {
            return false;
        }

        dispatchCoreEvent(house.getId(), HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, "house-create");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateHouseWithSync(Long id, House reqHouse) {
        if (id == null || reqHouse == null) {
            return false;
        }

        House dbHouse = this.getById(id);
        if (dbHouse == null) {
            return false;
        }

        House toUpdate = new House();
        toUpdate.setId(id);

        boolean hasAnyChange = false;
        boolean coreFieldChanged = false;

        if (reqHouse.getTitle() != null && !Objects.equals(reqHouse.getTitle(), dbHouse.getTitle())) {
            toUpdate.setTitle(reqHouse.getTitle());
            hasAnyChange = true;
        }
        if (reqHouse.getPrice() != null && !Objects.equals(reqHouse.getPrice(), dbHouse.getPrice())) {
            toUpdate.setPrice(reqHouse.getPrice());
            hasAnyChange = true;
            coreFieldChanged = true;
        }
        if (reqHouse.getDepositAmount() != null && !Objects.equals(reqHouse.getDepositAmount(), dbHouse.getDepositAmount())) {
            toUpdate.setDepositAmount(reqHouse.getDepositAmount());
            hasAnyChange = true;
            coreFieldChanged = true;
        }
        if (reqHouse.getLatitude() != null && !Objects.equals(reqHouse.getLatitude(), dbHouse.getLatitude())) {
            toUpdate.setLatitude(reqHouse.getLatitude());
            hasAnyChange = true;
            coreFieldChanged = true;
        }
        if (reqHouse.getLongitude() != null && !Objects.equals(reqHouse.getLongitude(), dbHouse.getLongitude())) {
            toUpdate.setLongitude(reqHouse.getLongitude());
            hasAnyChange = true;
            coreFieldChanged = true;
        }
        if (reqHouse.getStatus() != null && !Objects.equals(reqHouse.getStatus(), dbHouse.getStatus())) {
            toUpdate.setStatus(reqHouse.getStatus());
            hasAnyChange = true;
            coreFieldChanged = true;
        }

        if (!hasAnyChange) {
            log.info("房源更新请求无实际变更，houseId={}", id);
            return true;
        }

        boolean updated = this.updateById(toUpdate);
        if (!updated) {
            return false;
        }

        if (coreFieldChanged) {
            dispatchCoreEvent(id, HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, "house-update-core");
        } else {
            dispatchNormalEventAfterCommit(id, HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, "house-update-normal");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteHouseWithSync(Long id) {
        if (id == null) {
            return false;
        }
        boolean removed = this.removeById(id);
        if (!removed) {
            return false;
        }
        dispatchCoreEvent(id, HouseSyncConstants.EVENT_HOUSE_ES_DELETE, "house-delete");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateHouseStatusWithSync(Long houseId, Integer expectedStatus, Integer targetStatus, String reason) {
        if (houseId == null || targetStatus == null) {
            return false;
        }

        boolean updated = this.lambdaUpdate()
                .eq(House::getId, houseId)
                .eq(expectedStatus != null, House::getStatus, expectedStatus)
                .set(House::getStatus, targetStatus)
                .setSql("`version` = IFNULL(`version`,0) + 1")
                .update();
        if (!updated) {
            return false;
        }

        dispatchCoreEvent(houseId, HouseSyncConstants.EVENT_HOUSE_ES_UPSERT, reason);
        return true;
    }

    private HouseSearchResultVO searchWhenEsUnavailable(String city, int pageIndex, int pageSize) {
        try {
            List<HouseVO> redisRecommended = searchHotFromRedis(city, pageIndex, pageSize);
            if (!redisRecommended.isEmpty()) {
                log.info("ES异常时Redis热门推荐成功，city={}, pageIndex={}, pageSize={}, count={}",
                        city,
                        pageIndex,
                        pageSize,
                        redisRecommended.size());
                return buildSearchResult(redisRecommended, true, FALLBACK_SOURCE_REDIS_HOT, TIP_ES_DOWN);
            }
            log.warn("ES异常时Redis热门推荐为空，city={}, pageIndex={}, pageSize={}", city, pageIndex, pageSize);
        } catch (Exception e) {
            log.error("ES异常时Redis热门推荐失败，city={}, pageIndex={}, pageSize={}", city, pageIndex, pageSize, e);
        }

        List<HouseVO> dbRecommended = searchHotFromDbByCity(city, pageIndex, pageSize);
        return buildSearchResult(dbRecommended, true, FALLBACK_SOURCE_DB_CITY_HOT, TIP_ES_DOWN);
    }

    private HouseSearchResultVO buildSearchResult(List<HouseVO> houses, boolean esDown, String fallbackSource, String tipMessage) {
        HouseSearchResultVO result = new HouseSearchResultVO();
        result.setHouses(houses);
        result.setEsDown(esDown);
        result.setFallbackSource(fallbackSource);
        result.setTipMessage(tipMessage);
        return result;
    }

    private List<HouseVO> searchInEs(double lat, double lon, String distanceStr, int pageIndex, int pageSize) {
        Query boolQuery = Query.of(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("status").value(1)))
                .filter(f -> f.geoDistance(g -> g
                        .field("location")
                        .distance(distanceStr)
                        .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lon)))
                ))
        ));
        SortOptions geoSort = SortOptions.of(s -> s.geoDistance(g -> g
                .field("location")
                .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lon)))
                .order(SortOrder.Asc)
                .unit(DistanceUnit.Meters)
        ));
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withSort(geoSort)
                .withPageable(PageRequest.of(pageIndex, pageSize))
                .build();
        SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
        List<HouseVO> voList = new ArrayList<>();
        for (SearchHit<HouseDoc> hit : hits) {
            HouseDoc doc = hit.getContent();
            HouseVO vo = convertDocToVo(doc);
            Object[] sortValues = hit.getSortValues().toArray();
            if (sortValues.length > 0) {
                double distanceInMeters = (Double) sortValues[0];
                vo.setDistance(formatDistance(distanceInMeters));
            }
            voList.add(vo);
        }
        log.info("ES搜索成功，返回{}条，pageIndex={}, pageSize={}", voList.size(), pageIndex, pageSize);
        return voList;
    }

    private List<HouseVO> searchHotFromRedis(String city, int pageIndex, int pageSize) {
        String cityKey = StringUtils.hasText(city) ? city.trim() : "DEFAULT_CITY";
        String cacheKey = "house:hot:" + cityKey;

        // TODO 热门房源计算能力完成后，从Redis中按热度读取城市热门房源并组装 HouseVO。
        log.warn("Redis热门房源查询逻辑待实现，cacheKey={}, pageIndex={}, pageSize={}", cacheKey, pageIndex, pageSize);

        if (stringRedisTemplate.getConnectionFactory() == null) {
            throw new IllegalStateException("Redis连接工厂不存在");
        }
        return new ArrayList<>();
    }

    private List<HouseVO> searchHotFromDbByCity(String city, int pageIndex, int pageSize) {
        Page<House> page = new Page<>(pageIndex + 1L, pageSize);
        Page<House> housePage = this.lambdaQuery()
                .eq(House::getStatus, 1)
                .like(StringUtils.hasText(city), House::getTitle, city)
                .orderByDesc(House::getId)
                .page(page);

        List<HouseVO> voList = new ArrayList<>();
        for (House house : housePage.getRecords()) {
            voList.add(convertHouseToVo(house));
        }

        log.info("DB城市热门推荐完成，city={}, pageIndex={}, pageSize={}, count={}",
                city,
                pageIndex,
                pageSize,
                voList.size());
        return voList;
    }

    private HouseVO convertDocToVo(HouseDoc doc) {
        HouseVO vo = new HouseVO();
        vo.setId(doc.getId());
        vo.setPublisherUserId(doc.getPublisherUserId());
        vo.setTitle(doc.getTitle());
        vo.setStatus(doc.getStatus());
        if (doc.getPrice() != null) {
            BigDecimal priceYuan = new BigDecimal(doc.getPrice())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            vo.setPrice(priceYuan);
        }
        if (doc.getDepositAmount() != null) {
            BigDecimal depositYuan = new BigDecimal(doc.getDepositAmount())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            vo.setDepositAmount(depositYuan);
        }
        return vo;
    }

    private HouseVO convertHouseToVo(House house) {
        HouseVO vo = new HouseVO();
        vo.setId(house.getId());
        vo.setPublisherUserId(house.getPublisherUserId());
        vo.setTitle(house.getTitle());
        vo.setStatus(house.getStatus());
        if (house.getPrice() != null) {
            BigDecimal priceYuan = new BigDecimal(house.getPrice())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            vo.setPrice(priceYuan);
        }
        if (house.getDepositAmount() != null) {
            BigDecimal depositYuan = new BigDecimal(house.getDepositAmount())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            vo.setDepositAmount(depositYuan);
        }
        return vo;
    }

    private double parseRadiusMeters(String radiusStr) {
        if (radiusStr == null || radiusStr.isEmpty()) {
            return 5000d;
        }
        String lower = radiusStr.toLowerCase().trim();
        if (lower.endsWith("km")) {
            String number = lower.substring(0, lower.length() - 2);
            return Double.parseDouble(number) * 1000;
        }
        return Double.parseDouble(lower);
    }

    private String formatDistance(double meters) {
        if (meters < 1000) {
            return (int) meters + "m";
        }
        BigDecimal km = new BigDecimal(meters).divide(new BigDecimal(1000), 1, RoundingMode.HALF_UP);
        return km.toString() + "km";
    }

    private void dispatchCoreEvent(Long houseId, String eventType, String reason) {
        HouseSyncContext context = new HouseSyncContext();
        context.setHouseId(houseId);
        context.setEventType(eventType);
        context.setCoreEvent(true);
        context.setReason(reason);
        houseSyncDispatcher.dispatch(context);
    }

    private void dispatchNormalEventAfterCommit(Long houseId, String eventType, String reason) {
        HouseSyncContext context = new HouseSyncContext();
        context.setHouseId(houseId);
        context.setEventType(eventType);
        context.setCoreEvent(false);
        context.setReason(reason);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            houseSyncDispatcher.dispatch(context);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                houseSyncDispatcher.dispatch(context);
            }
        });
    }
}

