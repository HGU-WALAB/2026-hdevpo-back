package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import com.csee.swplus.mileage.portfolio.converter.ProfileLinksJsonConverter;
import com.csee.swplus.mileage.portfolio.converter.StringListJsonConverter;
import com.csee.swplus.mileage.portfolio.dto.ProfileLinkDto;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.*;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Main portfolio (1:1 with users).
 * Table: _sw_mileage_portfolio
 */
@Entity
@Table(name = "_sw_mileage_portfolio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private Users user;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    /** Local filename under the configured profile upload directory (multipart PUT). */
    @Column(name = "profile_image_upload_key", length = 255)
    private String profileImageUploadKey;

    /** Optional HTTPS URL for profile photo (CV/HTML export prefers this over upload). */
    @Column(name = "profile_image_external_url", length = 2048)
    private String profileImageExternalUrl;

    /** Optional labeled links (JSON array in DB). */
    @Convert(converter = ProfileLinksJsonConverter.class)
    @Column(name = "profile_links", columnDefinition = "TEXT")
    @Builder.Default
    private List<ProfileLinkDto> profileLinks = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "section_order", columnDefinition = "TEXT")
    private List<String> sectionOrder;
}
