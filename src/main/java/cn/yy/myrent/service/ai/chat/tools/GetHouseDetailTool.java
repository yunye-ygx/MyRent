package cn.yy.myrent.service.ai.chat.tools;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseService;
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
public class GetHouseDetailTool {

    private final IHouseService houseService;
    private final ObjectMapper objectMapper;

    @Tool(description = """
            查询指定房源的详细信息，包括价格、位置、设施和状态。
            仅当用户明确询问某套房源详情时调用。
            houseId 必填，通常来自 searchHouses 的结果。
            """)
    public String getHouseDetail(
            @ToolParam(description = "房源ID，必填。") Long houseId
    ) {
        if (houseId == null) {
            return writeJson(error("MISSING_HOUSE_ID", "请先提供想查看详情的房源ID"));
        }

        try {
            House house = houseService.getById(houseId);
            if (house == null) {
                return writeJson(error("HOUSE_NOT_FOUND", "房源不存在或已下架"));
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("houseId", house.getId());
            detail.put("title", house.getTitle());
            detail.put("city", house.getCity());
            detail.put("region", house.getRegion());
            detail.put("priceYuan", toYuan(house.getPrice()));
            detail.put("depositYuan", toYuan(house.getDepositAmount()));
            detail.put("rentMode", Integer.valueOf(1).equals(house.getRentType()) ? "整租" : "合租");
            detail.put("facilities", buildFacilities(house));
            detail.put("status", Integer.valueOf(1).equals(house.getStatus()) ? "可租" : "已下架");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("house", detail);
            return writeJson(result);
        } catch (Exception e) {
            log.error("getHouseDetail failed for houseId={}", houseId, e);
            return writeJson(error("SYSTEM_ERROR", "查询房源详情失败，请稍后再试"));
        }
    }

    private List<String> buildFacilities(House house) {
        List<String> facilities = new ArrayList<>();
        if (Integer.valueOf(1).equals(house.getNearSubway())) {
            facilities.add("近地铁");
        }
        if (Integer.valueOf(1).equals(house.getPrivateBathroom())) {
            facilities.add("独立卫浴");
        }
        if (Integer.valueOf(1).equals(house.getHasBalcony())) {
            facilities.add("带阳台");
        }
        if (Integer.valueOf(1).equals(house.getCivilWaterElectric())) {
            facilities.add("民水民电");
        }
        if (Integer.valueOf(1).equals(house.getSupportStudentDepositFree())) {
            facilities.add("学生免押");
        }
        return facilities;
    }

    private Map<String, Object> error(String errorCode, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("errorCode", errorCode);
        result.put("message", message);
        return result;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize tool response", e);
        }
    }

    private BigDecimal toYuan(Integer cent) {
        if (cent == null) {
            return null;
        }
        return BigDecimal.valueOf(cent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
