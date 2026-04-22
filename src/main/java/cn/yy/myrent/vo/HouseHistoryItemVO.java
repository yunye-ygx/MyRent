package cn.yy.myrent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class HouseHistoryItemVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long historyId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long houseId;

    private LocalDate browseDate;

    private LocalDateTime lastBrowseTime;

    private BigDecimal price;

    private String cover;
}
