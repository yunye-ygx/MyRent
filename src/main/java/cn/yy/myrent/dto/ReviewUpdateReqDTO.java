package cn.yy.myrent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewUpdateReqDTO {

    @Min(value = 1, message = "评分最小为1分")
    @Max(value = 5, message = "评分最大为5分")
    private Integer score;

    @NotBlank(message = "评价内容不能为空")
    private String content;
}
