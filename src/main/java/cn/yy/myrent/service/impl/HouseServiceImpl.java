package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.service.IHouseService;
import cn.yy.myrent.vo.HouseVO;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>
 * 房源信息表 服务实现类
 * </p>
 *
 * @author yy
 * @since 2026-02-26
 */
@Service
public class HouseServiceImpl extends ServiceImpl<HouseMapper, House> implements IHouseService {

    private static final Logger log = LoggerFactory.getLogger(HouseServiceImpl.class);
    private static final double EARTH_RADIUS_M = 6371000d;
    private static final long ES_QUERY_TIMEOUT_MS = 1200;
    private static final double DB_FALLBACK_BOX_PADDING = 1.2; // 放大包围盒，防止边界漏数

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    public List<HouseVO> searchNearbyHouse(SearchHouseReqDTO reqDTO) {
        double lat = reqDTO.getLatitude();
        double lon = reqDTO.getLongitude();
        double radiusMeters = parseRadiusMeters(reqDTO.getRadius());
        String distanceStr = ((int) radiusMeters) + "m";

        int pageIndex = (reqDTO.getPage() != null ? reqDTO.getPage() : 1) - 1;
        int pageSize = reqDTO.getSize() != null ? reqDTO.getSize() : 10;

        try {
            return CompletableFuture.supplyAsync(() -> searchInEs(lat, lon, distanceStr, pageIndex, pageSize))
                    .get(ES_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("ES搜索超时({}ms)，降级到DB，lat={}, lon={}, radius={}m", ES_QUERY_TIMEOUT_MS, lat, lon, radiusMeters);
        } catch (Exception e) {
            log.error("ES搜索异常，降级到DB，lat={}, lon={}, radius={}m", lat, lon, radiusMeters, e);
        }
        return searchInDb(lat, lon, radiusMeters, pageIndex, pageSize);
    }


   //es查询
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
            HouseVO vo = new HouseVO();
            vo.setId(doc.getId());
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

    //兜底方案，db查询
    private List<HouseVO> searchInDb(double lat, double lon, double radiusMeters, int pageIndex, int pageSize) {
        double paddedRadius = radiusMeters * DB_FALLBACK_BOX_PADDING;
        double latRadius = paddedRadius / 111_000d;
        double lonRadiusDenominator = Math.cos(Math.toRadians(lat));
        double lonRadius = lonRadiusDenominator == 0 ? 180 : paddedRadius / (111_000d * lonRadiusDenominator);
        LambdaQueryWrapper<House> wrapper = new LambdaQueryWrapper<House>()
                .eq(House::getStatus, 1)
                .between(House::getLatitude, lat - latRadius, lat + latRadius)
                .between(House::getLongitude, lon - lonRadius, lon + lonRadius);
        Page<House> page = new Page<>(pageIndex + 1L, pageSize);
        Page<House> housePage = this.page(page, wrapper);
        List<HouseVO> voList = new ArrayList<>();
        for (House house : housePage.getRecords()) {
            HouseVO vo = new HouseVO();
            vo.setId(house.getId());
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
            if (house.getLatitude() != null && house.getLongitude() != null) {
                double distance = haversine(lat, lon, house.getLatitude().doubleValue(), house.getLongitude().doubleValue());
                // 再用真实距离过滤，避免包围盒放大导致的超范围
                if (distance > radiusMeters) {
                    continue;
                }
                vo.setDistance(formatDistance(distance));
            }
            voList.add(vo);
        }
        log.info("DB兜底查询完成，返回{}条，pageIndex={}, pageSize={}, latBox=[{},{}], lonBox=[{},{}], radius={}m",
                voList.size(), pageIndex, pageSize, lat - latRadius, lat + latRadius, lon - lonRadius, lon + lonRadius, radiusMeters);
        return voList;
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

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * 内部辅助方法：将距离米格式化为前端友好的字符串（1000米以内用m，以上用km）
     */
    private String formatDistance(double meters) {
        if (meters < 1000) {
            return (int) meters + "m";
        } else {
            BigDecimal km = new BigDecimal(meters).divide(new BigDecimal(1000), 1, RoundingMode.HALF_UP);
            return km.toString() + "km";
        }
    }
}
