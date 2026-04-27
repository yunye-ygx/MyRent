package cn.yy.myrent.service.impl;

import cn.yy.myrent.dto.StudentVerificationApplyReqDTO;
import cn.yy.myrent.entity.StudentVerification;
import cn.yy.myrent.mapper.StudentVerificationMapper;
import cn.yy.myrent.vo.StudentBenefitsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentBenefitsServiceImplTest {

    @Mock
    private StudentVerificationMapper studentVerificationMapper;

    @InjectMocks
    private StudentBenefitsServiceImpl studentBenefitsService;

    @Test
    void getCurrentBenefitsShouldReturnUnverifiedWhenNoRecord() {
        when(studentVerificationMapper.selectOne(any())).thenReturn(null);

        StudentBenefitsVO result = studentBenefitsService.getCurrentBenefits(1001L);

        assertEquals("UNVERIFIED", result.getStatus());
        assertTrue(result.getBenefits().isEmpty());
    }

    @Test
    void applyShouldCreatePendingVerificationForNewUser() {
        when(studentVerificationMapper.selectOne(any())).thenReturn(null);
        when(studentVerificationMapper.insert(any(StudentVerification.class))).thenReturn(1);

        StudentVerificationApplyReqDTO req = new StudentVerificationApplyReqDTO();
        req.setSchoolName("Test University");
        req.setStudentNo("20260001");
        req.setGraduationDate(LocalDate.of(2028, 6, 30));

        StudentBenefitsVO result = studentBenefitsService.apply(1001L, req);

        ArgumentCaptor<StudentVerification> captor = ArgumentCaptor.forClass(StudentVerification.class);
        verify(studentVerificationMapper).insert(captor.capture());

        StudentVerification saved = captor.getValue();
        assertEquals(Long.valueOf(1001L), saved.getUserId());
        assertEquals(Integer.valueOf(0), saved.getStatus());
        assertEquals("Test University", saved.getSchoolName());
        assertEquals("20260001", saved.getStudentNo());
        assertEquals(LocalDate.of(2028, 6, 30), saved.getGraduationDate());
        assertNotNull(saved.getApplyTime());
        assertEquals("PENDING", result.getStatus());
    }

    @Test
    void getCurrentBenefitsShouldReturnApprovedBenefits() {
        StudentVerification verification = new StudentVerification()
                .setUserId(1001L)
                .setStatus(1)
                .setSchoolName("Test University")
                .setStudentNo("20260001")
                .setGraduationDate(LocalDate.of(2028, 6, 30));
        when(studentVerificationMapper.selectOne(any())).thenReturn(verification);

        StudentBenefitsVO result = studentBenefitsService.getCurrentBenefits(1001L);

        assertEquals("APPROVED", result.getStatus());
        assertEquals(4, result.getBenefits().size());
    }
}
