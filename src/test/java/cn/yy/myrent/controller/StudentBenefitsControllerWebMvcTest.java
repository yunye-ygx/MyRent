package cn.yy.myrent.controller;

import cn.yy.myrent.common.JwtTokenUtil;
import cn.yy.myrent.mapper.ChatMessageMapper;
import cn.yy.myrent.mapper.ChatSessionMapper;
import cn.yy.myrent.mapper.HouseFavoriteMapper;
import cn.yy.myrent.mapper.HouseHistoryMapper;
import cn.yy.myrent.mapper.HouseMapper;
import cn.yy.myrent.mapper.LocalTaskMapper;
import cn.yy.myrent.mapper.LocationDictMapper;
import cn.yy.myrent.mapper.MockPayTradeMapper;
import cn.yy.myrent.mapper.NotificationMapper;
import cn.yy.myrent.mapper.OrderMapper;
import cn.yy.myrent.mapper.PaymentMapper;
import cn.yy.myrent.mapper.PaymentRefundMapper;
import cn.yy.myrent.mapper.PublisherFollowMapper;
import cn.yy.myrent.mapper.ReviewMapper;
import cn.yy.myrent.mapper.StudentVerificationMapper;
import cn.yy.myrent.mapper.UserMapper;
import cn.yy.myrent.service.IStudentBenefitsService;
import cn.yy.myrent.vo.StudentBenefitsVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentBenefitsController.class)
class StudentBenefitsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IStudentBenefitsService studentBenefitsService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private ChatMessageMapper chatMessageMapper;

    @MockBean
    private ChatSessionMapper chatSessionMapper;

    @MockBean
    private HouseFavoriteMapper houseFavoriteMapper;

    @MockBean
    private HouseHistoryMapper houseHistoryMapper;

    @MockBean
    private HouseMapper houseMapper;

    @MockBean
    private LocalTaskMapper localTaskMapper;

    @MockBean
    private LocationDictMapper locationDictMapper;

    @MockBean
    private MockPayTradeMapper mockPayTradeMapper;

    @MockBean
    private NotificationMapper notificationMapper;

    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private PaymentMapper paymentMapper;

    @MockBean
    private PaymentRefundMapper paymentRefundMapper;

    @MockBean
    private PublisherFollowMapper publisherFollowMapper;

    @MockBean
    private ReviewMapper reviewMapper;

    @MockBean
    private StudentVerificationMapper studentVerificationMapper;

    @MockBean
    private UserMapper userMapper;

    @Test
    void getCurrentBenefitsShouldReturnCurrentStatus() throws Exception {
        StudentBenefitsVO vo = StudentBenefitsVO.unverified();

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(studentBenefitsService.getCurrentBenefits(1001L)).willReturn(vo);

        mockMvc.perform(get("/student-benefits/me").header("token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.benefits").isArray());
    }

    @Test
    void applyShouldReturnPendingVerification() throws Exception {
        StudentBenefitsVO.VerificationInfo verification = new StudentBenefitsVO.VerificationInfo();
        verification.setSchoolName("Test University");
        verification.setStudentNo("20260001");
        verification.setGraduationDate(LocalDate.of(2028, 6, 30));

        StudentBenefitsVO vo = StudentBenefitsVO.pending(verification);

        given(jwtTokenUtil.parseUserId("test-token")).willReturn(1001L);
        given(studentBenefitsService.apply(eq(1001L), any())).willReturn(vo);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("schoolName", "Test University");
        payload.put("studentNo", "20260001");
        payload.put("graduationDate", "2028-06-30");

        mockMvc.perform(post("/student-benefits/apply")
                        .header("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.verification.schoolName").value("Test University"))
                .andExpect(jsonPath("$.data.verification.studentNo").value("20260001"));

        verify(studentBenefitsService).apply(eq(1001L), any());
    }
}
