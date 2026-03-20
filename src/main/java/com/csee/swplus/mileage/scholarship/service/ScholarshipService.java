package com.csee.swplus.mileage.scholarship.service;

import com.csee.swplus.mileage.scholarship.dto.ScholarshipResponseDto;
import com.csee.swplus.mileage.scholarship.mapper.ScholarshipMapper;
import com.csee.swplus.mileage.scholarship.repository.ScholarshipRepository;
import com.csee.swplus.mileage.user.controller.response.UserResponse;
import com.csee.swplus.mileage.user.entity.Users;
import com.csee.swplus.mileage.user.service.UserService;
import com.csee.swplus.mileage.setting.service.ManagerService;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor // not null 또는 final 인 필드를 받는 생성자
@Slf4j
public class ScholarshipService {
    private final ScholarshipMapper scholarshipMapper;
    private final ManagerService managerService;
    private final ScholarshipRepository scholarshipRepository;
    private final UserService userService;

    // [피드백 수용 후 코드] 장학금 신청 테이블 존재
    @Transactional
    public void applyScholarship(String studentId, boolean isAgree) {
        UserResponse userResponse = userService.getUserInfo(studentId);
        String stype = userResponse.getStudentType();
        int term = userResponse.getTerm();

        // TODO: Extract "기타" and max term (9) to config - scholarship eligibility rules should be configurable
        if(stype.equals("기타") || term > 9) {
            log.warn("⚠️ 마일리지 장학금 신청 권한이 없는 학생 - studentId: {}", studentId);
            throw new IllegalStateException("신청 대상자가 아닙니다.");
        }

        int isChecked = isAgree ? 1 : 0;
        String semester = managerService.getCurrentSemester();
        LocalDateTime now = LocalDateTime.now();

        log.info("📌 applyScholarship 실행 - studentId: {}, isAgree: {}, isChecked: {}, semester: {}",
                studentId, isAgree, isChecked, semester);

        int updatedRowsInStudent = scholarshipMapper.updateStudentApplicationStatus(studentId, now, isChecked);
        int updatedRows = scholarshipMapper.createApplication(studentId, isChecked, semester);

        log.info("📝 createApplication 결과 - updatedRows: {}", updatedRows);

        if (updatedRows == 0) {
            log.warn("⚠️ 이미 신청된 학생이거나 존재하지 않는 학생 - studentId: {}", studentId);
            throw new IllegalStateException("이미 신청된 학생이거나 존재하지 않는 학생입니다.");
        }
    }

    public ScholarshipResponseDto getIsApplyStatus(String studentId) {
        Users user = scholarshipRepository.findByUniqueId(studentId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ScholarshipResponseDto.from(user);
    }
}