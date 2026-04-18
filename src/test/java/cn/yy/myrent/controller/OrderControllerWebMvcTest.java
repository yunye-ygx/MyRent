package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.LocalTaskMapper;
import cn.yy.myrent.mapper.LocationDictMapper;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IOrderService;
import cn.yy.myrent.vo.CreateOrderVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IOrderService orderService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private ChatMessageMapper chatMessageMapper;

    @MockBean
    private ChatSessionMapper chatSessionMapper;

    @MockBean
    private HouseFavoriteMapper houseFavoriteMapper;

    @MockBean
    private HouseMapper houseMapper;

    @MockBean
    private LocalTaskMapper localTaskMapper;

    @MockBean
    private LocationDictMapper locationDictMapper;

    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private PaymentMapper paymentMapper;

    @MockBean
    private UserMapper userMapper;

    @Test
    void createOrderShouldReturnCheckoutInfo() throws Exception {
        CreateOrderVO result = new CreateOrderVO();
        result.setOrderNo("ORDER-1001");
        result.setPaymentNo("PAY-1001");
        result.setMockPayUrl("/mock-pay/checkout?paymentNo=PAY-1001");
        result.setExpireTime(LocalDateTime.of(2026, 4, 18, 21, 0, 0));

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(orderService.createOrder(any())).willReturn(result);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("houseId", 101);
        payload.put("version", 0);

        mockMvc.perform(post("/order/create")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("ORDER-1001"))
                .andExpect(jsonPath("$.data.paymentNo").value("PAY-1001"))
                .andExpect(jsonPath("$.data.mockPayUrl").value("/mock-pay/checkout?paymentNo=PAY-1001"));
    }
}
