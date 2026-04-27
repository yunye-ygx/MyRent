package cn.yy.myrent.controller;

import cn.yy.myrent.common.Result;
import cn.yy.myrent.common.UserContext;
import cn.yy.myrent.dto.StudentVerificationApplyReqDTO;
import cn.yy.myrent.service.IStudentBenefitsService;
import cn.yy.myrent.vo.StudentBenefitsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student-benefits")
@RequiredArgsConstructor
public class StudentBenefitsController {

    private final IStudentBenefitsService studentBenefitsService;

    @GetMapping("/me")
    public Result<StudentBenefitsVO> getCurrentBenefits() {
        try {
            Long userId = UserContext.requireCurrentUserId();
            return Result.success(studentBenefitsService.getCurrentBenefits(userId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/apply")
    public Result<StudentBenefitsVO> apply(@Valid @RequestBody StudentVerificationApplyReqDTO reqDTO) {
        try {
            Long userId = UserContext.requireCurrentUserId();
            return Result.success(studentBenefitsService.apply(userId, reqDTO));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
