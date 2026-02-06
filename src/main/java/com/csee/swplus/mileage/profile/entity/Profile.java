package com.csee.swplus.mileage.profile.entity;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "_sw_mileage_profile_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "profile_image_url", length = 50)
    private String profileImageUrl;
    
    @Column(name = "self_description", length = 1023)
    private String selfDescription;
    
    @Column(name = "job", length = 256)
    private String job;
    
    @Column(name = "github_link", length = 127)
    private String githubLink;
    
    @Column(name = "instagram_link", length = 127)
    private String instagramLink;
    
    @Column(name = "blog_link", length = 127)
    private String blogLink;
    
    @Column(name = "linkedin_link", length = 127)
    private String linkedinLink;
    
    @Column(name = "snum", length = 12)
    private String snum; // Foreign key to _sw_student.uniqueId
    
    // GitHub OAuth fields
    @Column(name = "github_id")
    private Long githubId;
    
    @Column(name = "github_username", length = 100)
    private String githubUsername;
    
    @Column(name = "github_connected_at")
    private LocalDateTime githubConnectedAt;
}
