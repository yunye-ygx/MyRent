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
            查询指定房源的详细信息，包括价格、设施、位置等。
            当用户问到某套具体房源的详情时调用。
            需要房源ID，通常来自 searchHouses 的返回结果。
            """)
    public String getHouseDetail(
            @ToolParam(description = "房源ID，必填。") Long houseId
    ) {
        try {
            House house = houseService.getById(houseId);
            if (house == null) {
                return "{\"error\":\"房源不存在或已下架\"}";
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("houseId", house.getId());
            detail.put("title", house.getTitle());
            detail.put("city", house.getCity());
            detail.put("region", house.getRegion());
            detail.put("priceYuan", toYuan(house.getPrice()));
            detail.put("depositYuan", toYuan(house.getDepositAmount()));
            detail.put("rentMode", Integer.valueOf(1).equals(house.getRentType()) ? "整租" : "合租");

            List<String> facilities = new ArrayList<>();
            if (Integer.valueOf(1).equals(house.getNearSubway())) facilities.add("近地铁");
            if (Integer.valueOf(1).equals(house.getPrivateBathroom())) facilities.add("独立卫浴");
            if (Integer.valueOf(1).equals(house.getHasBalcony())) facilities.add("带阳台");
            if (Integer.valueOf(1).equals(house.getCivilWaterElectric())) facilities.add("民水民电");
            if (Integer.valueOf(1).equals(house.getSupportStudentDepositFree())) facilities.add("学生免押");
            detail.put("facilities", facilities);

            detail.put("status", Integer.valueOf(1).equals(house.getStatus()) ? "可租" : "已锁定");

            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            log.error("getHouseDetail failed for houseId={}", houseId, e);
            return "{\"error\":\"查询房源详情失败\"}";
        }
    }

    private BigDecimal toYuan(Integer cent) {
        if (cent == null) return null;
        return BigDecimal.valueOf(cent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
