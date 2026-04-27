package cn.yy.myrent.service;

import cn.yy.myrent.dto.StudentVerificationApplyReqDTO;
import cn.yy.myrent.vo.StudentBenefitsVO;

public interface IStudentBenefitsService {

    StudentBenefitsVO getCurrentBenefits(Long userId);

    StudentBenefitsVO apply(Long userId, StudentVerificationApplyReqDTO reqDTO);
}
