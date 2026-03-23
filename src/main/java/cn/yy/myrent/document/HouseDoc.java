package cn.yy.myrent.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.annotations.ValueConverter;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.time.LocalDateTime;

@Data
@Document(indexName = "house_info")
public class HouseDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long publisherUserId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;

    @Field(type = FieldType.Integer)
    private Integer rentType;

    @Field(type = FieldType.Integer)
    private Integer price;

    @Field(type = FieldType.Integer)
    private Integer depositAmount;

    @Field(type = FieldType.Integer)
    private Integer totalCost;

    @Field(type = FieldType.Integer)
    private Integer status;

    @ValueConverter(HouseCreateTimeValueConverter.class)
    @Field(type = FieldType.Date, format = {DateFormat.strict_date_optional_time, DateFormat.strict_date})
    private LocalDateTime createTime;

    @GeoPointField
    private GeoPoint location;
}
