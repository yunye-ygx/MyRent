package cn.yy.myrent.service.ai.chat.tools;

import cn.yy.myrent.entity.House;
import cn.yy.myrent.service.IHouseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHouseDetailToolTest {

    @Mock
    private IHouseService houseService;

    @InjectMocks
    private GetHouseDetailTool getHouseDetailTool;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getHouseDetailShouldRejectMissingHouseId() throws Exception {
        getHouseDetailTool = new GetHouseDetailTool(houseService, objectMapper);

        String result = getHouseDetailTool.getHouseDetail(null);
        JsonNode root = objectMapper.readTree(result);

        assertFalse(root.get("ok").asBoolean());
        assertEquals("MISSING_HOUSE_ID", root.get("errorCode").asText());
        verify(houseService, never()).getById(null);
    }

    @Test
    void getHouseDetailShouldReturnNotFoundErrorForUnknownHouse() throws Exception {
        getHouseDetailTool = new GetHouseDetailTool(houseService, objectMapper);
        when(houseService.getById(99L)).thenReturn(null);

        String result = getHouseDetailTool.getHouseDetail(99L);
        JsonNode root = objectMapper.readTree(result);

        assertFalse(root.get("ok").asBoolean());
        assertEquals("HOUSE_NOT_FOUND", root.get("errorCode").asText());
    }

    @Test
    void getHouseDetailShouldReturnStructuredSuccessPayload() throws Exception {
        getHouseDetailTool = new GetHouseDetailTool(houseService, objectMapper);
        House house = new House();
        house.setId(101L);
        house.setTitle("陆家嘴精装一居");
        house.setCity("上海");
        house.setRegion("浦东");
        house.setPrice(350000);
        house.setDepositAmount(350000);
        house.setRentType(1);
        house.setNearSubway(1);
        house.setStatus(1);
        when(houseService.getById(101L)).thenReturn(house);

        String result = getHouseDetailTool.getHouseDetail(101L);
        JsonNode root = objectMapper.readTree(result);

        assertTrue(root.get("ok").asBoolean());
        assertTrue(root.has("house"));
        assertEquals(101L, root.get("house").get("houseId").asLong());
    }
}
