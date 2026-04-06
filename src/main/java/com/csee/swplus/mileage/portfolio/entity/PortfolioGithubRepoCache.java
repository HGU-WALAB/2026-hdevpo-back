package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import com.csee.swplus.mileage.portfolio.converter.RepoLanguagesJsonConverter;
import com.csee.swplus.mileage.portfolio.dto.RepoLanguageDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cached GitHub metadata for one repo under a portfolio (filled by sync job; API will read later).
 * Table: {@code _sw_mileage_portfolio_github_repo_cache}
 */
@Entity
@Table(
        name = "_sw_mileage_portfolio_github_repo_cache",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "repo_id"}),
        indexes = {
                @Index(name = "idx_portfolio_github_cache_portfolio", columnList = "portfolio_id"),
                @Index(name = "idx_portfolio_github_cache_synced", columnList = "portfolio_id, github_synced_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioGithubRepoCache extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "html_url", length = 1024)
    private String htmlUrl;

    @Column(name = "github_description", columnDefinition = "TEXT")
    private String githubDescription;

    @Column(name = "primary_language", length = 100)
    private String primaryLanguage;

    @Convert(converter = RepoLanguagesJsonConverter.class)
    @Column(name = "languages_json", columnDefinition = "TEXT")
    @Builder.Default
    private List<RepoLanguageDto> languages = new ArrayList<>();

    @Column(name = "github_created_at", length = 40)
    private String githubCreatedAt;

    @Column(name = "github_updated_at", length = 40)
    private String githubUpdatedAt;

    @Column(name = "visibility", length = 20)
    private String visibility;

    @Column(name = "owner_login", length = 255)
    private String ownerLogin;

    @Column(name = "stargazers_count")
    private Integer stargazersCount;

    @Column(name = "forks_count")
    private Integer forksCount;

    @Column(name = "github_synced_at", nullable = false)
    private LocalDateTime githubSyncedAt;

    @PrePersist
    void defaultGithubSyncedAt() {
        if (githubSyncedAt == null) {
            githubSyncedAt = LocalDateTime.now();
        }
    }
}
