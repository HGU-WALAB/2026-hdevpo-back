package com.csee.swplus.mileage.user.service;

import com.csee.swplus.mileage.user.controller.response.UserResponse;
import com.csee.swplus.mileage.user.entity.Users;
import com.csee.swplus.mileage.user.mapper.StudentSchoolMapper;
import com.csee.swplus.mileage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final StudentSchoolMapper studentSchoolMapper;

    // 유저 정보 조회
    public UserResponse getUserInfo(String studentId) {
        Users user = userRepository.findByUniqueId(studentId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // stype: school/major1/major2로 _sw_mileage_student_school에서 조회.
        // See StudentSchoolMapper.xml findStype - 가장 구체적인 매칭 행의 stype 반환, 없으면 '기타'.
        String stype = studentSchoolMapper.findStype(
                user.getDepartment(),
                user.getMajor1(),
                user.getMajor2()
        );
        return UserResponse.from(user, stype != null ? stype : "기타");
    }

    /** Test: lookup stype for given school/major combo. */
    public String getStypeForTest(String school, String major1, String major2) {
        String stype = studentSchoolMapper.findStype(school, major1, major2);
        return stype != null ? stype : "기타";
    }
}
