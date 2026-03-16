package cn.yy.myrent.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import lombok.Data;

@Data
@Document(indexName = "house_info") // 对应 ES 里的索引名
public class HouseDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long publisherUserId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title; // 房源标题，支持分词搜索

    @Field(type = FieldType.Integer)
    private Integer price; // 价格(分)

    @Field(type = FieldType.Integer)
    private Integer depositAmount;

    @Field(type = FieldType.Integer)
    private Integer status; // 1-可租, 2-已锁定 (搜索时只搜1的)

    /**
     * 面试核心考点：地理位置字段
     * ES 专属的数据类型 geo_point，专门用于存储经纬度并进行空间计算
     */
    @GeoPointField
    private GeoPoint location;
}
