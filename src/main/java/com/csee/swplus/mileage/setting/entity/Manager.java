package com.csee.swplus.mileage.setting.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@Table(name = "_sw_manager_setting")
public class Manager {
    @Id
    private Long id;

    @Column(name = "reg_start")
    private String regStart;

    @Column(name = "reg_end")
    private String regEnd;

    /** MyPage announcement text (e.g. scholarship notice). */
    @Column(name = "mypage_announcement", length = 500)
    private String mypageAnnouncement;
}
