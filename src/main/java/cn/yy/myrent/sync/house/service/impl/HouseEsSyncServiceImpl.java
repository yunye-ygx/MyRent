package cn.yy.myrent.sync.house.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HouseEsSyncServiceImpl implements HouseEsSyncService {

    private static final int ES_SCAN_BATCH_SIZE = 500;
    private static final int DB_SCAN_BATCH_SIZE = 500;

    @Autowired
    private HouseMapper houseMapper;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void upsertByHouseId(Long houseId) {
        if (houseId == null) {
            return;
        }

        House house = houseMapper.selectById(houseId);
        if (house == null) {
            deleteByHouseId(houseId);
            log.warn("house not found in DB, delete document from ES as compensation, houseId={}", houseId);
            return;
        }

        elasticsearchOperations.save(buildHouseDoc(house));
        log.info("house ES upsert finished, houseId={}", houseId);
    }

    @Override
    public void deleteByHouseId(Long houseId) {
        if (houseId == null) {
            return;
        }

        elasticsearchOperations.delete(String.valueOf(houseId), HouseDoc.class);
        log.info("house ES delete finished, houseId={}", houseId);
    }

    @Override
    public void compensateByCreateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            log.warn("skip house DB/ES compensate because time range is invalid, startTime={}, endTime={}",
                    startTime, endTime);
            return;
        }

        List<House> dbHouses = houseMapper.selectList(Wrappers.<House>lambdaQuery()
                .ge(House::getCreateTime, startTime)
                .lt(House::getCreateTime, endTime));
        Map<Long, House> dbHouseMap = dbHouses.stream()
                .filter(house -> house.getId() != null)
                .collect(Collectors.toMap(House::getId, house -> house, (left, right) -> left, LinkedHashMap::new));

        List<HouseDoc> esDocs = listEsDocsByCreateTimeRange(startTime, endTime);
        Map<Long, HouseDoc> esHouseMap = esDocs.stream()
                .filter(doc -> doc.getId() != null)
                .collect(Collectors.toMap(HouseDoc::getId, doc -> doc, (left, right) -> left, LinkedHashMap::new));

        int upsertCount = 0;
        int deleteCount = 0;

        for (House house : dbHouseMap.values()) {
            HouseDoc esDoc = esHouseMap.remove(house.getId());
            if (!isDocConsistent(house, esDoc)) {
                elasticsearchOperations.save(buildHouseDoc(house));
                upsertCount++;
            }
        }

        for (HouseDoc orphanDoc : esHouseMap.values()) {
            elasticsearchOperations.delete(String.valueOf(orphanDoc.getId()), HouseDoc.class);
            deleteCount++;
        }

        log.info("house DB/ES compensate finished, startTime={}, endTime={}, dbCount={}, esCount={}, upsertCount={}, deleteCount={}",
                startTime,
                endTime,
                dbHouseMap.size(),
                esDocs.size(),
                upsertCount,
                deleteCount);
    }

    @Override
    public int rebuildAllFromDb() {
        long lastId = 0L;
        int rebuildCount = 0;

        while (true) {
            List<House> dbHouses = houseMapper.selectList(Wrappers.<House>lambdaQuery()
                    .gt(House::getId, lastId)
                    .orderByAsc(House::getId)
                    .last("limit " + DB_SCAN_BATCH_SIZE));
            if (dbHouses == null || dbHouses.isEmpty()) {
                break;
            }

            for (House house : dbHouses) {
                if (house == null || house.getId() == null) {
                    continue;
                }
                elasticsearchOperations.save(buildHouseDoc(house));
                rebuildCount++;
                lastId = house.getId();
            }

            if (dbHouses.size() < DB_SCAN_BATCH_SIZE) {
                break;
            }
        }

        log.info("house ES rebuild from DB finished, rebuildCount={}", rebuildCount);
        return rebuildCount;
    }

    private List<HouseDoc> listEsDocsByCreateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        List<HouseDoc> docs = new ArrayList<>();
        int pageNumber = 0;

        while (true) {
            CriteriaQuery query = new CriteriaQuery(new Criteria("createTime")
                    .greaterThanEqual(startTime)
                    .lessThan(endTime));
            query.setPageable(PageRequest.of(pageNumber, ES_SCAN_BATCH_SIZE, Sort.by(Sort.Order.asc("createTime"))));

            SearchHits<HouseDoc> searchHits = elasticsearchOperations.search(query, HouseDoc.class);
            List<SearchHit<HouseDoc>> hitList = searchHits.getSearchHits();
            if (hitList.isEmpty()) {
                break;
            }

            for (SearchHit<HouseDoc> hit : hitList) {
                HouseDoc doc = hit.getContent();
                if (doc != null) {
                    docs.add(doc);
                }
            }

            if (hitList.size() < ES_SCAN_BATCH_SIZE) {
                break;
            }
            pageNumber++;
        }

        return docs;
    }

    private boolean isDocConsistent(House house, HouseDoc esDoc) {
        if (house == null || esDoc == null) {
            return false;
        }

        HouseDoc expectedDoc = buildHouseDoc(house);
        return Objects.equals(expectedDoc.getId(), esDoc.getId())
                && Objects.equals(expectedDoc.getPublisherUserId(), esDoc.getPublisherUserId())
                && Objects.equals(expectedDoc.getTitle(), esDoc.getTitle())
                && Objects.equals(expectedDoc.getRentType(), esDoc.getRentType())
                && Objects.equals(expectedDoc.getPrice(), esDoc.getPrice())
                && Objects.equals(expectedDoc.getDepositAmount(), esDoc.getDepositAmount())
                && Objects.equals(expectedDoc.getTotalCost(), esDoc.getTotalCost())
                && Objects.equals(expectedDoc.getStatus(), esDoc.getStatus())
                && Objects.equals(expectedDoc.getCreateTime(), esDoc.getCreateTime())
                && isLocationConsistent(expectedDoc.getLocation(), esDoc.getLocation());
    }

    private boolean isLocationConsistent(GeoPoint expected, GeoPoint actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }

        return Double.compare(expected.getLat(), actual.getLat()) == 0
                && Double.compare(expected.getLon(), actual.getLon()) == 0;
    }

    private HouseDoc buildHouseDoc(House house) {
        HouseDoc doc = new HouseDoc();
        doc.setId(house.getId());
        doc.setPublisherUserId(house.getPublisherUserId());
        doc.setTitle(house.getTitle());
        doc.setRentType(house.getRentType());
        doc.setPrice(house.getPrice());
        doc.setDepositAmount(house.getDepositAmount());
        doc.setTotalCost(house.getTotalCost());
        doc.setStatus(house.getStatus());
        doc.setCreateTime(house.getCreateTime());
        if (house.getLatitude() != null && house.getLongitude() != null) {
            doc.setLocation(new GeoPoint(house.getLatitude().doubleValue(), house.getLongitude().doubleValue()));
        }
        return doc;
    }
}
