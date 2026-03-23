package cn.yy.myrent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

@Data
public class HouseFavoriteStatusVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long houseId;

    private Boolean favorited;

    private Long favoriteCount;
}
