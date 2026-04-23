package cn.yy.myrent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

@Data
public class PublisherFollowStatusVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long publisherUserId;

    private Boolean following;
}
