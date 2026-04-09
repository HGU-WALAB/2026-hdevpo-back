package com.csee.swplus.mileage.user.entity;

import javax.persistence.*;

import lombok.extern.slf4j.Slf4j;
import com.csee.swplus.mileage.auth.dto.AuthDto;
import com.csee.swplus.mileage.base.entity.BaseTime;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "_sw_student")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class Users extends BaseTime implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snum", unique = true, length = 12)
    private String uniqueId;

    @Column(name = "sname", length = 30)
    private String name;

    @Column(name = "semail", nullable = false, unique = true, length = 50)
    private String email;

    @Column(name = "last_login_date",  nullable = false)
    private LocalDateTime login_time;

    @Column(name = "apply_date")
    private LocalDateTime applyDate;

    @Column(name = "school", length = 40)
    private String department;

    @Column(name = "major_1", length = 60)
    private String major1;

    @Column(name = "major_2", length = 60)
    private String major2;

    @Column(name = "grade_level", columnDefinition = "TINYINT(2)")
    private Integer grade;

    @Column(name = "semester_count", columnDefinition = "TINYINT(2)")
    private Integer semester;

    @Column(length = 20)
    private String smobile;

    @Column(length = 70)
    private String password;

    @Column(name = "is_student", columnDefinition = "CHAR(1)")
    private String isStudent;

    @Column(name = "is_apply", columnDefinition = "TINYINT(1)")
    private Integer isApply;

    @Column(name = "login_count", columnDefinition = "SMALLINT(6)")
    private Integer loginCount;

    @Column(name = "is_approved" ,columnDefinition = "CHAR(1)")
    private String isApproved;

    @Column(name = "hash_key", length = 70)
    private String hashKey;

    @Column(name = "isChecked")
    private Integer isChecked;

    public void increaseLoginCount() {
        if (this.loginCount == null) {
            this.loginCount = 0;
        }
        this.loginCount += 1;
        this.login_time = LocalDateTime.now();
        log.info("Updated login count: {}, login time: {}", this.loginCount, this.login_time); // 로그 추가
    }

    /**
     * Refresh profile fields from a trusted login source (Hisnet).
     * Does NOT touch loginCount / login_time.
     */
    public void updateProfileFrom(AuthDto dto) {
        if (dto == null) {
            return;
        }
        this.name = dto.getStudentName();
        this.email = dto.getStudentEmail();
        this.department = dto.getDepartment();
        this.major1 = dto.getMajor1();
        this.major2 = dto.getMajor2();
        this.grade = dto.getGrade();
        this.semester = dto.getTerm();
    }

    public static Users from(AuthDto dto) {
        return Users.builder()
                .uniqueId(dto.getStudentId())
                .name(dto.getStudentName())
                .email(dto.getStudentEmail())
                .login_time(LocalDateTime.now())
                .department(dto.getDepartment())
                .major1(dto.getMajor1())
                .major2(dto.getMajor2())
                .grade(dto.getGrade())
                .semester(dto.getTerm())
                .isApproved("Y")
                .isChecked(0)
                .build();
    }
}