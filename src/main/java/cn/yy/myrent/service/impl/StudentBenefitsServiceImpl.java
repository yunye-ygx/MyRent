package cn.yy.myrent.service.impl;

import cn.yy.myrent.dto.StudentVerificationApplyReqDTO;
import cn.yy.myrent.entity.StudentVerification;
import cn.yy.myrent.mapper.StudentVerificationMapper;
import cn.yy.myrent.service.IStudentBenefitsService;
import cn.yy.myrent.vo.StudentBenefitsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentBenefitsServiceImpl implements IStudentBenefitsService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    private static final List<String> APPROVED_BENEFITS = List.of(
            "学生专属优惠券",
            "免押优先房源",
            "学生找房优先响应",
            "租房安心保障"
    );

    private final StudentVerificationMapper studentVerificationMapper;

    @Override
    public StudentBenefitsVO getCurrentBenefits(Long userId) {
        StudentVerification verification = findByUserId(userId);
        if (verification == null) {
            return StudentBenefitsVO.unverified();
        }
        return buildBenefits(verification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentBenefitsVO apply(Long userId, StudentVerificationApplyReqDTO reqDTO) {
        validateApplyRequest(userId, reqDTO);

        StudentVerification existing = findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            StudentVerification created = new StudentVerification()
                    .setUserId(userId)
                    .setStatus(STATUS_PENDING)
                    .setSchoolName(reqDTO.getSchoolName().trim())
                    .setStudentNo(reqDTO.getStudentNo().trim())
                    .setGraduationDate(reqDTO.getGraduationDate())
                    .setApplyTime(now);
            studentVerificationMapper.insert(created);
            return buildBenefits(created);
        }

        if (Integer.valueOf(STATUS_APPROVED).equals(existing.getStatus())) {
            return buildBenefits(existing);
        }

        existing.setStatus(STATUS_PENDING);
        existing.setSchoolName(reqDTO.getSchoolName().trim());
        existing.setStudentNo(reqDTO.getStudentNo().trim());
        existing.setGraduationDate(reqDTO.getGraduationDate());
        existing.setApplyTime(now);
        existing.setReviewTime(null);
        existing.setRejectReason(null);
        studentVerificationMapper.updateById(existing);
        return buildBenefits(existing);
    }

    private StudentVerification findByUserId(Long userId) {
        return studentVerificationMapper.selectOne(new LambdaQueryWrapper<StudentVerification>()
                .eq(StudentVerification::getUserId, userId)
                .last("limit 1"));
    }

    private void validateApplyRequest(Long userId, StudentVerificationApplyReqDTO reqDTO) {
        if (userId == null) {
            throw new IllegalArgumentException("user id cannot be null");
        }
        if (reqDTO == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (!StringUtils.hasText(reqDTO.getSchoolName())) {
            throw new IllegalArgumentException("school name cannot be blank");
        }
        if (!StringUtils.hasText(reqDTO.getStudentNo())) {
            throw new IllegalArgumentException("student no cannot be blank");
        }
        if (reqDTO.getGraduationDate() == null) {
            throw new IllegalArgumentException("graduation date cannot be null");
        }
    }

    private StudentBenefitsVO buildBenefits(StudentVerification verification) {
        StudentBenefitsVO.VerificationInfo info = new StudentBenefitsVO.VerificationInfo();
        info.setSchoolName(verification.getSchoolName());
        info.setStudentNo(verification.getStudentNo());
        info.setGraduationDate(verification.getGraduationDate());
        info.setApplyTime(verification.getApplyTime());
        info.setReviewTime(verification.getReviewTime());
        info.setRejectReason(verification.getRejectReason());

        Integer status = verification.getStatus();
        if (Integer.valueOf(STATUS_APPROVED).equals(status)) {
            return StudentBenefitsVO.approved(info, APPROVED_BENEFITS);
        }
        if (Integer.valueOf(STATUS_REJECTED).equals(status)) {
            return StudentBenefitsVO.rejected(info);
        }
        return StudentBenefitsVO.pending(info);
    }
}
