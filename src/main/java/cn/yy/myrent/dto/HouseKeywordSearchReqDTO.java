package cn.yy.myrent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HouseKeywordSearchReqDTO {

    @NotBlank(message = "keyword cannot be blank")
    private String keyword;

    @Min(value = 1, message = "page must be at least 1")
    private Integer page = 1;

    @Min(value = 1, message = "size must be at least 1")
    @Max(value = 50, message = "size cannot be greater than 50")
    private Integer size = 10;
}
