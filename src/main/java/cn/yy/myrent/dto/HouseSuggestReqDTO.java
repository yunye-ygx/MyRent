package cn.yy.myrent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HouseSuggestReqDTO {

    @NotBlank(message = "keyword不能为空")
    private String keyword;

    @Min(value = 1, message = "size最小为1")
    private Integer size = 5;
}

