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
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    public List<HouseVO> searchNearbyHouse(SearchHouseReqDTO reqDTO) {

        // 1. 从前端 DTO 提取核心参数 (容错处理)
        double lat = reqDTO.getLatitude();
        double lon = reqDTO.getLongitude();
        // 拼装 ES 需要的距离格式，例如 "5000m"。若前端未传则默认 5000m
        String distanceStr = (reqDTO.getRadius() != null ? reqDTO.getRadius() : 5000) + "m";

        // ⚠️ 核心防坑：Spring Data ES 分页从 0 开始，前端传入的 page 通常从 1 开始
        int pageIndex = (reqDTO.getPage() != null ? reqDTO.getPage() : 1) - 1;
        int pageSize = reqDTO.getSize() != null ? reqDTO.getSize() : 10;
        // ==========================================
        // 2. 构建 ES 新版 API 查询条件 (Lambda DSL)
        // ==========================================
        Query boolQuery = Query.of(q -> q.bool(b -> b
                // 必须条件：只查上架可租的房源 (status = 1)
                .must(m -> m.term(t -> t.field("status").value(1)))
                // 性能核心：LBS 空间过滤放 filter 里，不参与 TF-IDF 算分，极其提速！
                .filter(f -> f.geoDistance(g -> g
                        .field("location") // ES 中的 GeoPoint 类型字段名
                        .distance(distanceStr)
                        .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lon)))
                ))
        ));
        // ==========================================
        // 3. 构建 ES 距离排序 (由近到远)
        // ==========================================
        SortOptions geoSort = SortOptions.of(s -> s.geoDistance(g -> g
                .field("location")
                .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lon)))
                .order(SortOrder.Asc)
                .unit(DistanceUnit.Meters) // 强制 ES 返回以“米”为单位的距离值
        ));
        // ==========================================
        // 4. 构建 NativeQuery 并执行搜索 (完全不碰 MySQL)
        // ==========================================
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withSort(geoSort)
                .withPageable(PageRequest.of(pageIndex, pageSize)) // 注入分页
                .build();
        // 核心：去 ES 查出包含全部数据的宽文档 (注意这里用的是 HouseDoc.class 而不是 House.class)
        SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
        // ==========================================
        // 5. 将 ES 文档 (Doc) 转化为 视图对象 (VO)
        // ==========================================
        List<HouseVO> voList = new ArrayList<>();

        for (SearchHit<HouseDoc> hit : hits) {
            HouseDoc doc = hit.getContent();
            HouseVO vo = new HouseVO();
            // 5.1 基础信息原样拷贝
            vo.setId(doc.getId());
            vo.setTitle(doc.getTitle());
            vo.setStatus(doc.getStatus());



            // 5.2 资金转化：后端承担“分转元”逻辑，保护前端不丢失精度
            if (doc.getPrice() != null) {
                BigDecimal priceYuan = new BigDecimal(doc.getPrice())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
                vo.setPrice(priceYuan);
            }

            if (doc.getDepositAmount() != null) {
                BigDecimal priceYuan = new BigDecimal(doc.getPrice())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
                vo.setDepositAmount(priceYuan);
            }
            // 5.3 距离转化：零开销获取 ES 计算出的绝对距离
            Object[] sortValues = hit.getSortValues().toArray();
            if (sortValues.length > 0) {
                double distanceInMeters = (Double) sortValues[0];
                vo.setDistance(formatDistance(distanceInMeters));
            }
            voList.add(vo);
        }
        return voList;
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
