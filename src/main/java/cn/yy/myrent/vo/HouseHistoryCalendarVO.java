package cn.yy.myrent.vo;

import lombok.Data;

import java.util.List;

@Data
public class HouseHistoryCalendarVO {

    private Integer year;

    private Integer month;

    private List<Integer> activeDays;
}
