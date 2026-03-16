package cn.yy.myrent.sync.house.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.sync.house.service.HouseEsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HouseEsSyncServiceImpl implements HouseEsSyncService {

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
            log.warn("房源不存在，执行ES删除兜底，houseId={}", houseId);
            return;
        }
        HouseDoc doc = new HouseDoc();
        doc.setId(house.getId());
        doc.setPublisherUserId(house.getPublisherUserId());
        doc.setTitle(house.getTitle());
        doc.setPrice(house.getPrice());
        doc.setDepositAmount(house.getDepositAmount());
        doc.setStatus(house.getStatus());
        if (house.getLatitude() != null && house.getLongitude() != null) {
            doc.setLocation(new GeoPoint(house.getLatitude().doubleValue(), house.getLongitude().doubleValue()));
        }

        elasticsearchOperations.save(doc);
        log.info("房源ES upsert完成，houseId={}", houseId);
    }

    @Override
    public void deleteByHouseId(Long houseId) {
        if (houseId == null) {
            return;
        }
        elasticsearchOperations.delete(String.valueOf(houseId), HouseDoc.class);
        log.info("房源ES delete完成，houseId={}", houseId);
    }
}

