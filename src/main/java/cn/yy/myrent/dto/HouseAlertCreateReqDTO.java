package cn.yy.myrent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HouseAlertCreateReqDTO {

    @NotBlank(message = "city cannot be blank")
    private String city;

    @NotBlank(message = "region cannot be blank")
    private String region;

    @NotNull(message = "maxPrice cannot be null")
    @Min(value = 1, message = "maxPrice must be positive")
    private Integer maxPrice;

    @NotNull(message = "rentType cannot be null")
    private Integer rentType;
}
