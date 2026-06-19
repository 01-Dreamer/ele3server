package top.zxylearn.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

@Data
@Document(indexName = "shop_index")
public class ShopDocument {

    @Id
    @Field(type = FieldType.Long)
    private Long id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword, index = false)
    private String avatar;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword, index = false)
    private String address;

    @GeoPointField
    private GeoPoint location;

    @Field(name = "open_time", type = FieldType.Keyword)
    private String openTime;

    @Field(name = "close_time", type = FieldType.Keyword)
    private String closeTime;

    @Field(name = "review_score", type = FieldType.Float)
    private Float reviewScore;

    @Field(name = "review_count", type = FieldType.Long)
    private Long reviewCount;

    @Field(name = "sales_count", type = FieldType.Long)
    private Long salesCount;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(name = "item_content", type = FieldType.Text)
    private String itemContent;
}
