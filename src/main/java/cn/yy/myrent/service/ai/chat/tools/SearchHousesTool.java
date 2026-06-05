package cn.yy.myrent.service.ai.chat.tools;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.discovery.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchHousesTool {

    private final HouseRecallService houseRecallService;
    private final HouseRankingService houseRankingService;
    private final ObjectMapper objectMapper;

    @Tool(description = """
            根据用户需求搜索真实在租房源，返回匹配的房源列表。
            当用户表达了想看房、找房、推荐房源的意图时调用此工具。
            区域名称必填，其他参数可选——不确定的参数不要填，不要猜测用户没有说的信息。
            """)
    public String searchHouses(
            @ToolParam(description = "区域名称，如'陆家嘴'、'三林'、'世纪公园'。必填。") String locationName,
            @ToolParam(description = "月租预算上限（元），如 3500。用户没说就不填。") Integer budgetYuan,
            @ToolParam(description = "WHOLE=整租，SHARED=合租。用户没说就不填。") String rentMode,
            @ToolParam(description = "是否要求靠近地铁站。用户没说就不填。") Boolean nearSubway,
            @ToolParam(description = "是否要求独立卫浴。用户没说就不填。") Boolean privateBathroom,
            @ToolParam(description = "是否要求有阳台。用户没说就不填。") Boolean hasBalcony,
            @ToolParam(description = "是否要求民水民电。用户没说就不填。") Boolean civilWaterElectric,
            @ToolParam(description = "返回房源数量，默认5，最大10。") Integer limit
    ) {
        int pageSize = (limit != null && limit > 0 && limit <= 10) ? limit : 5;

        HouseRecallQuery recallQuery = HouseRecallQuery.builder()
                .locationName(locationName)
                .budgetYuan(budgetYuan)
                .rentMode(rentMode)
                .nearSubway(nearSubway)
                .privateBathroom(privateBathroom)
                .hasBalcony(hasBalcony)
                .civilWaterElectric(civilWaterElectric)
                .page(1)
                .size(pageSize)
                .recallProfile(HouseRecallProfile.AI_RECOMMEND)
                .build();

        HouseRankQuery rankQuery = HouseRankQuery.builder()
                .budgetYuan(budgetYuan)
                .budgetScope("RENT_ONLY")
                .rentMode(rentMode)
                .nearSubway(nearSubway)
                .privateBathroom(privateBathroom)
                .hasBalcony(hasBalcony)
                .civilWaterElectric(civilWaterElectric)
                .page(1)
                .size(pageSize)
                .rankingProfile(HouseRankingProfile.AI_RECOMMEND_DEFAULT)
                .build();

        try {
            HouseRecallResult recallResult = houseRecallService.recall(recallQuery);
            if (recallResult.candidates().isEmpty()) {
                return "{\"count\":0,\"message\":\"当前条件下没有找到匹配的房源，建议用户调整预算或扩大区域范围。\"}";
            }

            HouseRankResult rankResult = houseRankingService.rank(recallResult.candidates(), rankQuery);
            List<Map<String, Object>> houses = new ArrayList<>();

            for (HouseRankedItem item : rankResult.currentPageItems()) {
                House house = item.house();
                if (house == null) continue;

                Map<String, Object> h = new LinkedHashMap<>();
                h.put("houseId", house.getId());
                h.put("title", house.getTitle());
                h.put("priceYuan", toYuan(house.getPrice()));
                h.put("rentMode", Integer.valueOf(1).equals(house.getRentType()) ? "整租" : "合租");

                List<String> highlights = new ArrayList<>();
                if (Integer.valueOf(1).equals(house.getNearSubway())) highlights.add("近地铁");
                if (Integer.valueOf(1).equals(house.getPrivateBathroom())) highlights.add("独立卫浴");
                if (Integer.valueOf(1).equals(house.getHasBalcony())) highlights.add("带阳台");
                if (Integer.valueOf(1).equals(house.getCivilWaterElectric())) highlights.add("民水民电");
                h.put("highlights", highlights);

                houses.add(h);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", houses.size());
            result.put("location", locationName);
            result.put("houses", houses);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("searchHouses failed for location={}", locationName, e);
            return "{\"count\":0,\"message\":\"搜索暂时不可用，请稍后再试。\"}";
        }
    }

    private BigDecimal toYuan(Integer cent) {
        if (cent == null) return null;
        return BigDecimal.valueOf(cent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
