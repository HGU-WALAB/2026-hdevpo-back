package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.portfolio.dto.*;
import com.csee.swplus.mileage.profile.entity.Profile;
import com.csee.swplus.mileage.profile.repository.ProfileRepository;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exports portfolio data as a single-file HTML page (recruiter-optimized, print-friendly).
 * **blueStyle** layout: sidebar (name, role, school, meta-line, summary-chip, tech pills, contact) +
 * main (About, Projects, Mileage & extracurricular timeline, optional Achievements, Activities, footer).
 * LLM prompts ({@link #buildCvPromptTail()}, {@link #buildArchivePromptTail()}) require the same structure/class names for generated HTML.
 */
@Service
@RequiredArgsConstructor
public class PortfolioHtmlExportService {

    /** Mileage / activity text that likely denotes an award (for ACHIEVEMENTS section, blueStyle). */

    private final PortfolioService portfolioService;
    private final ProfileRepository profileRepository;

    /**
     * Must match {@code server.servlet.context-path} (e.g. {@code /milestone25}) so export {@code img} URLs
     * resolve like the live app ({@code /milestone25/api/...}), not {@code /api/...} at domain root.
     */
    @Value("${server.servlet.context-path:}")
    private String servletContextPath;

    @Value("${file.portfolio-profile-upload-dir:${file.profile-upload-dir:./uploads/profile}}")
    private String profileUploadDir;

    /**
     * Generates HTML portfolio for the given user. Uses visible repos only.
     */
    public String generateHtml(Users user) {
        UserInfoResponse userInfo = portfolioService.getUserInfo(user);
        TechStackResponse techStack = portfolioService.getTechStack(user);
        RepositoriesResponse repos = portfolioService.getRepositories(user, 1, 100, null, true);
        MileageListResponse mileage = portfolioService.getMileageList(user);
        ActivitiesResponse activities = portfolioService.getActivities(user, null);

        String githubUrl = null;
        Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
        if (profile != null && profile.getGithubLink() != null && !profile.getGithubLink().isEmpty()) {
            githubUrl = profile.getGithubLink();
        } else if (profile != null && profile.getGithubUsername() != null) {
            githubUrl = "https://github.com/" + profile.getGithubUsername();
        }

        return buildHtml(userInfo, techStack, repos.getRepositories(),
                mileage.getMileage(), activities.getActivities(), githubUrl, user.getEmail());
    }

    /**
     * Builds portfolio data in the STEP 2 INPUT DATA format for LLM prompt / full test.
     * Returns plain text that can be pasted into the prompt template.
     */
    public String buildPromptInputData(Users user) {
        UserInfoResponse userInfo = portfolioService.getUserInfo(user);
        TechStackResponse techStack = portfolioService.getTechStack(user);
        RepositoriesResponse repos = portfolioService.getRepositories(user, 1, 100, null, true);
        MileageListResponse mileage = portfolioService.getMileageList(user);
        ActivitiesResponse activities = portfolioService.getActivities(user, null);

        String githubUrl = null;
        Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
        if (profile != null && profile.getGithubLink() != null && !profile.getGithubLink().isEmpty()) {
            githubUrl = profile.getGithubLink();
        } else if (profile != null && profile.getGithubUsername() != null) {
            githubUrl = "https://github.com/" + profile.getGithubUsername();
        }
        String email = user.getEmail();

        StringBuilder sb = new StringBuilder();
        sb.append("[bio]\n");
        sb.append("- 이름: ").append(nullToEmpty(userInfo.getName())).append("\n");
        sb.append("- 학교/학과: ").append(schoolDept(userInfo)).append("\n");
        sb.append("- 학년/학기: (").append(nullToEmpty(userInfo.getGrade())).append("학년 ")
          .append(nullToEmpty(userInfo.getSemester())).append("학기)\n");
        sb.append("- 한줄 자기소개: ").append(nullToEmpty(userInfo.getBio())).append("\n");
        appendProfileLinksLines(sb, userInfo);
        sb.append("\n");

        appendTechStackPlainText(sb, techStack);

        sb.append("[github_repos]\n");
        if (repos.getRepositories() != null) {
            for (RepoEntryResponse r : repos.getRepositories()) {
                String title = r.getCustom_title() != null && !r.getCustom_title().isEmpty()
                        ? r.getCustom_title()
                        : r.getGithub_title();
                if (title == null) title = "Repository";
                String desc = repoDisplayDescription(r);
                String langStr = formatRepoLanguages(r);
                if (!langStr.isEmpty()) langStr = " (" + langStr + ")";
                String commitStr = (r.getCommit_count() != null) ? " " + r.getCommit_count() + " commits" : "";
                String starStr = (r.getStargazers_count() != null) ? " " + r.getStargazers_count() + " stars" : "";
                String forkStr = (r.getForks_count() != null) ? " " + r.getForks_count() + " forks" : "";
                sb.append("- ").append(title).append(" - ").append(desc).append(langStr).append(commitStr).append(starStr).append(forkStr).append("\n");
                if (r.getHtml_url() != null) sb.append(r.getHtml_url()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("[mileage_list]\n");
        if (mileage.getMileage() != null) {
            for (MileageEntryResponse m : mileage.getMileage()) {
                String sem = nullToEmpty(m.getSemester());
                String cat = nullToEmpty(m.getCategoryName());
                String sub = nullToEmpty(m.getSubitemName());
                String add = m.getAdditional_info() != null && !m.getAdditional_info().isEmpty()
                        ? m.getAdditional_info() : nullToEmpty(m.getDescription1());
                sb.append("- ").append(sem).append(" ").append(cat).append(" - ").append(sub)
                  .append(" · ").append(add).append("\n");
            }
        }
        sb.append("\n");

        sb.append("[activities]\n");
        if (activities.getActivities() != null) {
            for (ActivityResponse a : activities.getActivities()) {
                String title = nullToEmpty(a.getTitle());
                String desc = nullToEmpty(a.getDescription());
                String start = a.getStart_date() != null ? a.getStart_date().toString() : "";
                String end = a.getEnd_date() != null ? a.getEnd_date().toString() : "";
                sb.append("- ").append(title).append(" (").append(start).append(" ~ ").append(end).append(")");
                if (!desc.isEmpty()) sb.append(" · ").append(desc);
                if (a.getUrl() != null && !a.getUrl().trim().isEmpty()) {
                    sb.append(" · URL: ").append(a.getUrl().trim());
                }
                if (a.getTags() != null && !a.getTags().isEmpty()) {
                    sb.append(" · tags: ").append(String.join(", ", a.getTags()));
                }
                sb.append("\n");
            }
        }
        sb.append("\n");

        sb.append("[contact]\n");
        sb.append("- GitHub URL: ").append(githubUrl != null ? githubUrl : "").append("\n");
        sb.append("- Email: ").append(email != null ? email : "").append("\n");

        return sb.toString();
    }

    /**
     * Builds the full LLM prompt (ROLE, TASK, STEP 1-5) with user's portfolio data filled into STEP 2.
     * Ready to paste into an LLM for portfolio HTML generation.
     */
    public String buildFullPrompt(Users user) {
        String inputData = buildPromptInputData(user);
        return PROMPT_HEAD + inputData + buildCvPromptTail();
    }

    /**
     * Builds CV-specific LLM prompt with job info and selected portfolio items only.
     * Bio and tech stack are always included. Repos, mileage, activities are filtered by selected IDs.
     */
    public String buildCvPrompt(Users user, CvBuildPromptRequest request) {
        java.util.Set<Long> repoIds = request.getSelected_repo_ids() != null
                ? new java.util.HashSet<>(request.getSelected_repo_ids()) : java.util.Collections.emptySet();
        java.util.Set<Long> mileageIds = request.getSelected_mileage_ids() != null
                ? new java.util.HashSet<>(request.getSelected_mileage_ids()) : java.util.Collections.emptySet();
        java.util.Set<Long> activityIds = request.getSelected_activity_ids() != null
                ? new java.util.HashSet<>(request.getSelected_activity_ids()) : java.util.Collections.emptySet();

        UserInfoResponse userInfo = portfolioService.getUserInfo(user);
        TechStackResponse techStack = portfolioService.getTechStack(user);
        RepositoriesResponse repos = portfolioService.getRepositories(user, 1, 100, true, false);
        MileageListResponse mileage = portfolioService.getMileageList(user);
        ActivitiesResponse activities = portfolioService.getActivities(user, null);

        String githubUrl = null;
        Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
        if (profile != null && profile.getGithubLink() != null && !profile.getGithubLink().isEmpty()) {
            githubUrl = profile.getGithubLink();
        } else if (profile != null && profile.getGithubUsername() != null) {
            githubUrl = "https://github.com/" + profile.getGithubUsername();
        }
        String email = user.getEmail();

        StringBuilder sb = new StringBuilder();

        sb.append("[job_info]\n");
        sb.append("- 공고정보: ").append(nullToEmpty(request.getJob_posting())).append("\n");
        sb.append("- 지원 직무: ").append(nullToEmpty(request.getTarget_position())).append("\n");
        sb.append("- 추가 요청사항: ").append(nullToEmpty(request.getAdditional_notes())).append("\n\n");

        sb.append("[bio]\n");
        sb.append("- 이름: ").append(nullToEmpty(userInfo.getName())).append("\n");
        sb.append("- 학교/학과: ").append(schoolDept(userInfo)).append("\n");
        sb.append("- 학년/학기: (").append(nullToEmpty(userInfo.getGrade())).append("학년 ")
          .append(nullToEmpty(userInfo.getSemester())).append("학기)\n");
        sb.append("- 한줄 자기소개: ").append(nullToEmpty(userInfo.getBio())).append("\n");
        appendProfileLinksLines(sb, userInfo);
        sb.append("\n");

        appendTechStackPlainText(sb, techStack);

        sb.append("[github_repos]\n");
        if (repos.getRepositories() != null) {
            for (RepoEntryResponse r : repos.getRepositories()) {
                if (r.getId() == null || !repoIds.contains(r.getId())) continue;
                String title = r.getCustom_title() != null && !r.getCustom_title().isEmpty()
                        ? r.getCustom_title()
                        : r.getGithub_title();
                if (title == null) title = "Repository";
                String desc = repoDisplayDescription(r);
                String langStr = formatRepoLanguages(r);
                if (!langStr.isEmpty()) langStr = " (" + langStr + ")";
                String commitStr = (r.getCommit_count() != null) ? " " + r.getCommit_count() + " commits" : "";
                String starStr = (r.getStargazers_count() != null) ? " " + r.getStargazers_count() + " stars" : "";
                String forkStr = (r.getForks_count() != null) ? " " + r.getForks_count() + " forks" : "";
                sb.append("- ").append(title).append(" - ").append(desc).append(langStr).append(commitStr).append(starStr).append(forkStr).append("\n");
                if (r.getHtml_url() != null) sb.append(r.getHtml_url()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("[mileage_list]\n");
        if (mileage.getMileage() != null) {
            for (MileageEntryResponse m : mileage.getMileage()) {
                if (m.getId() == null || !mileageIds.contains(m.getId())) continue;
                String sem = nullToEmpty(m.getSemester());
                String cat = nullToEmpty(m.getCategoryName());
                String sub = nullToEmpty(m.getSubitemName());
                String add = m.getAdditional_info() != null && !m.getAdditional_info().isEmpty()
                        ? m.getAdditional_info() : nullToEmpty(m.getDescription1());
                sb.append("- ").append(sem).append(" ").append(cat).append(" - ").append(sub)
                  .append(" · ").append(add).append("\n");
            }
        }
        sb.append("\n");

        sb.append("[activities]\n");
        if (activities.getActivities() != null) {
            for (ActivityResponse a : activities.getActivities()) {
                if (a.getId() == null || !activityIds.contains(a.getId())) continue;
                String title = nullToEmpty(a.getTitle());
                String desc = nullToEmpty(a.getDescription());
                String start = a.getStart_date() != null ? a.getStart_date().toString() : "";
                String end = a.getEnd_date() != null ? a.getEnd_date().toString() : "";
                sb.append("- ").append(title).append(" (").append(start).append(" ~ ").append(end).append(")");
                if (!desc.isEmpty()) sb.append(" · ").append(desc);
                if (a.getUrl() != null && !a.getUrl().trim().isEmpty()) {
                    sb.append(" · URL: ").append(a.getUrl().trim());
                }
                if (a.getTags() != null && !a.getTags().isEmpty()) {
                    sb.append(" · tags: ").append(String.join(", ", a.getTags()));
                }
                sb.append("\n");
            }
        }
        sb.append("\n");

        sb.append("[contact]\n");
        sb.append("- GitHub URL: ").append(githubUrl != null ? githubUrl : "").append("\n");
        sb.append("- Email: ").append(email != null ? email : "").append("\n");

        return PROMPT_HEAD + sb.toString() + buildCvPromptTail();
    }

    /**
     * Reflective “archive” prompt: neutral tone, one-shot HTML, chronological/category ordering in STEP 2.
     * Same data sources and selection IDs as {@link #buildCvPrompt}; uses {@link #ARCHIVE_PROMPT_HEAD} / {@link #buildArchivePromptTail()} only.
     */
    public String buildArchivePrompt(Users user, CvBuildPromptRequest request) {
        Set<Long> repoIds = request.getSelected_repo_ids() != null
                ? new HashSet<>(request.getSelected_repo_ids()) : Collections.emptySet();
        Set<Long> mileageIds = request.getSelected_mileage_ids() != null
                ? new HashSet<>(request.getSelected_mileage_ids()) : Collections.emptySet();
        Set<Long> activityIds = request.getSelected_activity_ids() != null
                ? new HashSet<>(request.getSelected_activity_ids()) : Collections.emptySet();

        UserInfoResponse userInfo = portfolioService.getUserInfo(user);
        TechStackResponse techStack = portfolioService.getTechStack(user);
        RepositoriesResponse repos = portfolioService.getRepositories(user, 1, 100, true, false);
        MileageListResponse mileage = portfolioService.getMileageList(user);
        ActivitiesResponse activities = portfolioService.getActivities(user, null);

        String githubUrl = null;
        Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
        if (profile != null && profile.getGithubLink() != null && !profile.getGithubLink().isEmpty()) {
            githubUrl = profile.getGithubLink();
        } else if (profile != null && profile.getGithubUsername() != null) {
            githubUrl = "https://github.com/" + profile.getGithubUsername();
        }
        String email = user.getEmail();

        List<MileageEntryResponse> mileageList = new ArrayList<>();
        if (mileage.getMileage() != null) {
            for (MileageEntryResponse m : mileage.getMileage()) {
                if (m.getId() != null && mileageIds.contains(m.getId())) {
                    mileageList.add(m);
                }
            }
        }
        mileageList.sort(Comparator
                .comparing((MileageEntryResponse m) -> m.getSemester() != null ? m.getSemester() : "\uFFFF")
                .thenComparing(m -> nullToEmpty(m.getCategoryName()))
                .thenComparing(m -> nullToEmpty(m.getSubitemName())));

        List<ActivityResponse> activityList = new ArrayList<>();
        if (activities.getActivities() != null) {
            for (ActivityResponse a : activities.getActivities()) {
                if (a.getId() != null && activityIds.contains(a.getId())) {
                    activityList.add(a);
                }
            }
        }
        activityList.sort(Comparator.comparing(ActivityResponse::getStart_date, Comparator.nullsLast(Comparator.naturalOrder())));

        List<RepoEntryResponse> repoList = new ArrayList<>();
        if (repos.getRepositories() != null) {
            for (RepoEntryResponse r : repos.getRepositories()) {
                if (r.getId() != null && repoIds.contains(r.getId())) {
                    repoList.add(r);
                }
            }
        }
        repoList.sort(Comparator.comparing(
                (RepoEntryResponse r) -> r.getUpdated_at() != null ? r.getUpdated_at() : "",
                Comparator.reverseOrder()));

        StringBuilder sb = new StringBuilder();

        sb.append("[self_assessment_context]\n");
        sb.append("- 관심 영역 (현재 관심·탐색 방향; 요청 필드 job_posting): ").append(nullToEmpty(request.getJob_posting())).append("\n");
        sb.append("- 초점·방향: ").append(nullToEmpty(request.getTarget_position())).append("\n");
        sb.append("- 추가 메모: ").append(nullToEmpty(request.getAdditional_notes())).append("\n\n");

        sb.append("[bio]\n");
        sb.append("- 이름: ").append(nullToEmpty(userInfo.getName())).append("\n");
        sb.append("- 학교/학과: ").append(schoolDept(userInfo)).append("\n");
        sb.append("- 학년/학기: (").append(nullToEmpty(userInfo.getGrade())).append("학년 ")
          .append(nullToEmpty(userInfo.getSemester())).append("학기)\n");
        sb.append("- 한줄 자기소개: ").append(nullToEmpty(userInfo.getBio())).append("\n");
        appendProfileLinksLines(sb, userInfo);
        sb.append("\n");

        appendTechStackPlainText(sb, techStack);

        sb.append("[mileage_list]\n");
        for (MileageEntryResponse m : mileageList) {
            String sem = nullToEmpty(m.getSemester());
            String cat = nullToEmpty(m.getCategoryName());
            String sub = nullToEmpty(m.getSubitemName());
            String add = m.getAdditional_info() != null && !m.getAdditional_info().isEmpty()
                    ? m.getAdditional_info() : nullToEmpty(m.getDescription1());
            sb.append("- ").append(sem).append(" ").append(cat).append(" - ").append(sub)
              .append(" · ").append(add).append("\n");
        }
        sb.append("\n");

        sb.append("[activities]\n");
        for (ActivityResponse a : activityList) {
            String title = nullToEmpty(a.getTitle());
            String desc = nullToEmpty(a.getDescription());
            String start = a.getStart_date() != null ? a.getStart_date().toString() : "";
            String end = a.getEnd_date() != null ? a.getEnd_date().toString() : "";
            sb.append("- ").append(title).append(" (").append(start).append(" ~ ").append(end).append(")");
            if (!desc.isEmpty()) sb.append(" · ").append(desc);
            if (a.getUrl() != null && !a.getUrl().trim().isEmpty()) {
                sb.append(" · URL: ").append(a.getUrl().trim());
            }
            if (a.getTags() != null && !a.getTags().isEmpty()) {
                sb.append(" · tags: ").append(String.join(", ", a.getTags()));
            }
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("[github_repos]\n");
        for (RepoEntryResponse r : repoList) {
            String title = r.getCustom_title() != null && !r.getCustom_title().isEmpty()
                    ? r.getCustom_title()
                    : r.getGithub_title();
            if (title == null) title = "Repository";
            String desc = repoDisplayDescription(r);
            String langStr = formatRepoLanguages(r);
            if (!langStr.isEmpty()) langStr = " (" + langStr + ")";
            String commitStr = (r.getCommit_count() != null) ? " " + r.getCommit_count() + " commits" : "";
            String starStr = (r.getStargazers_count() != null) ? " " + r.getStargazers_count() + " stars" : "";
            String forkStr = (r.getForks_count() != null) ? " " + r.getForks_count() + " forks" : "";
            sb.append("- ").append(title).append(" - ").append(desc).append(langStr).append(commitStr).append(starStr).append(forkStr).append("\n");
            if (r.getHtml_url() != null) sb.append(r.getHtml_url()).append("\n");
        }
        sb.append("\n");

        sb.append("[contact]\n");
        sb.append("- GitHub URL: ").append(githubUrl != null ? githubUrl : "").append("\n");
        sb.append("- Email: ").append(email != null ? email : "").append("\n");

        return ARCHIVE_PROMPT_HEAD + sb.toString() + buildArchivePromptTail();
    }

    private String repoDisplayDescription(RepoEntryResponse r) {
        if (r == null || r.getDescription() == null) {
            return "";
        }
        return r.getDescription().trim();
    }

    private String nullToEmpty(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private void appendProfileLinksLines(StringBuilder sb, UserInfoResponse userInfo) {
        if (userInfo.getProfile_links() == null || userInfo.getProfile_links().isEmpty()) {
            return;
        }
        for (ProfileLinkDto p : userInfo.getProfile_links()) {
            if (p == null) {
                continue;
            }
            String url = p.getUrl() != null ? p.getUrl().trim() : "";
            if (url.isEmpty()) {
                continue;
            }
            String label = p.getLabel() != null && !p.getLabel().trim().isEmpty()
                    ? p.getLabel().trim() : url;
            sb.append("- 링크: ").append(label).append(" — ").append(url).append("\n");
        }
    }

    private List<RepoLanguageDto> getRepoLanguagesForDisplay(RepoEntryResponse r) {
        if (r.getLanguages() != null && !r.getLanguages().isEmpty()) {
            return r.getLanguages();
        }
        if (r.getLanguage() != null && !r.getLanguage().isEmpty()) {
            return Collections.singletonList(
                    RepoLanguageDto.builder().name(r.getLanguage()).percentage(null).build());
        }
        return Collections.emptyList();
    }

    private void appendTechStackPlainText(StringBuilder sb, TechStackResponse techStack) {
        sb.append("[tech_stack]\n");
        if (techStack.getDomains() != null) {
            for (TechStackDomainResponse d : techStack.getDomains()) {
                String dn = d.getName() != null ? d.getName() : "";
                if (d.getTech_stacks() != null) {
                    for (TechStackEntryResponse t : d.getTech_stacks()) {
                        String line = t.getName() != null ? t.getName() : "";
                        if (!dn.isEmpty()) line += " (" + dn + ")";
                        if (t.getLevel() != null) line += " " + t.getLevel() + "%";
                        sb.append("- ").append(line).append("\n");
                    }
                }
            }
        }
        sb.append("\n");
    }

    private String formatRepoLanguages(RepoEntryResponse r) {
        List<RepoLanguageDto> langList = getRepoLanguagesForDisplay(r);
        return langList.stream()
                .map(l -> l.getPercentage() != null
                        ? l.getName() + " (" + l.getPercentage() + "%)"
                        : l.getName())
                .collect(Collectors.joining(", "));
    }

    private String buildHtml(UserInfoResponse userInfo, TechStackResponse techStack,
            List<RepoEntryResponse> repos, List<MileageEntryResponse> mileageList,
            List<ActivityResponse> activities, String githubUrl, String email) {

        String name = escape(userInfo.getName());
        String schoolDeptEsc = escape(schoolDeptSansGrade(userInfo));
        List<String> bioParagraphs = splitBioParagraphs(userInfo.getBio());
        String[] roleSummary = deriveRoleAndSummaryChip(bioParagraphs);
        String roleLine = roleSummary[0];
        String summaryChip = roleSummary[1];
        List<String> aboutParas = buildBlueStyleAboutParagraphs(bioParagraphs);

        String metaLine = buildMetaLine(userInfo);
        String profileImgSrc = buildProfileImageSrc(userInfo);

        String title = name;
        if (roleLine != null && !roleLine.trim().isEmpty()) {
            title += " · " + escape(roleLine);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\" />\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n");
        sb.append("  <title>").append(title).append("</title>\n");
        sb.append("  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />\n");
        sb.append("  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />\n");
        sb.append("  <link href=\"https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap\" rel=\"stylesheet\" />\n");
        sb.append("  <style>\n");
        sb.append(CSS);
        sb.append("\n  </style>\n</head>\n<body>\n");

        sb.append("  <div class=\"page\">\n");
        sb.append("    <div class=\"card\">\n");
        sb.append("      <div class=\"card-inner\">\n");

        sb.append("        <aside class=\"sidebar\">\n");
        sb.append("          <div class=\"profile\">\n");
        appendProfileImageTag(sb, profileImgSrc);
        if (name != null && !name.isEmpty()) {
            sb.append("            <div class=\"name\">").append(name).append("</div>\n");
        }
        if (roleLine != null && !roleLine.trim().isEmpty()) {
            sb.append("            <div class=\"role\">").append(escape(roleLine)).append("</div>\n");
        }
        if (schoolDeptEsc != null && !schoolDeptEsc.isEmpty()) {
            sb.append("            <div class=\"school\">").append(schoolDeptEsc).append("</div>\n");
        }
        if (metaLine != null && !metaLine.isEmpty()) {
            sb.append("            <div class=\"meta-line\">").append(escape(metaLine)).append("</div>\n");
        }
        if (summaryChip != null && !summaryChip.trim().isEmpty()) {
            sb.append("            <div class=\"summary-chip\"><span>").append(escape(summaryChip)).append("</span></div>\n");
        }

        if (hasTechStackEntries(techStack)) {
            sb.append("            <div class=\"section\" style=\"margin-bottom: 0;\">\n");
            sb.append("              <div class=\"section-header\" style=\"margin-bottom: 6px;\">\n");
            sb.append("                <div class=\"section-marker\"></div>\n");
            sb.append("                <div>\n");
            sb.append("                  <div class=\"section-title\" style=\"font-size: 13px;\">TECH STACK</div>\n");
            sb.append("                </div>\n");
            sb.append("              </div>\n");
            for (TechStackDomainResponse d : techStack.getDomains()) {
                if (d == null || d.getTech_stacks() == null) {
                    continue;
                }
                String dn = d.getName() != null ? d.getName().trim() : "";
                boolean hasAny = d.getTech_stacks().stream()
                        .anyMatch(t -> t != null && t.getName() != null && !t.getName().trim().isEmpty());
                if (!hasAny) {
                    continue;
                }
                if (!dn.isEmpty()) {
                    sb.append("              <div class=\"section-subtitle\" style=\"margin:8px 0 6px;\">")
                            .append(escape(dn))
                            .append("</div>\n");
                }
                sb.append("              <div class=\"pill-group\" style=\"margin-bottom:10px;\">\n");
                for (TechStackEntryResponse t : d.getTech_stacks()) {
                    if (t == null) {
                        continue;
                    }
                    String techName = t.getName() != null ? t.getName().trim() : "";
                    if (techName.isEmpty()) {
                        continue;
                    }
                    StringBuilder pillText = new StringBuilder(techName);
                    if (t.getLevel() != null) {
                        pillText.append(" ").append(t.getLevel()).append("%");
                    }
                    sb.append("                <div class=\"pill\">").append(escape(pillText.toString())).append("</div>\n");
                }
                sb.append("              </div>\n");
            }
            sb.append("            </div>\n");
        }

        boolean hasEmail = email != null && !email.trim().isEmpty();
        boolean hasGithub = githubUrl != null && !githubUrl.trim().isEmpty();
        boolean hasProfileLinks = userInfo.getProfile_links() != null && !userInfo.getProfile_links().isEmpty();

        if (hasEmail || hasGithub || hasProfileLinks) {
            sb.append("            <div class=\"contact-block\">\n");
            sb.append("              <div class=\"contact-label\">Contact</div>\n");
            if (hasEmail) {
                String emailEsc = escape(email.trim());
                sb.append("              <div class=\"contact-item\">\n");
                sb.append("                <span class=\"icon\">📧</span>\n");
                sb.append("                <a href=\"mailto:").append(emailEsc).append("\">").append(emailEsc).append("</a>\n");
                sb.append("              </div>\n");
            }
            if (hasGithub) {
                String gh = githubUrl.trim();
                sb.append("              <div class=\"contact-item\">\n");
                sb.append("                <span class=\"icon\">🐙</span>\n");
                sb.append("                <a href=\"").append(escape(gh)).append("\" class=\"link-chip\" target=\"_blank\" rel=\"noreferrer\">\n");
                sb.append("                  <span>GitHub</span>\n");
                sb.append("                  <span>").append(escape(toGithubDisplayText(gh))).append("</span>\n");
                sb.append("                </a>\n");
                sb.append("              </div>\n");
            }
            if (hasProfileLinks) {
                for (ProfileLinkDto p : userInfo.getProfile_links()) {
                    if (p == null) {
                        continue;
                    }
                    String rawUrl = p.getUrl() != null ? p.getUrl().trim() : "";
                    if (rawUrl.isEmpty()) {
                        continue;
                    }
                    String href = normalizeExternalHttpUrl(rawUrl);
                    if (href == null) {
                        continue;
                    }
                    String label = p.getLabel() != null && !p.getLabel().trim().isEmpty()
                            ? p.getLabel().trim()
                            : "Link";
                    sb.append("              <div class=\"contact-item\">\n");
                    sb.append("                <span class=\"icon\">🔗</span>\n");
                    sb.append("                <a href=\"").append(escape(href))
                            .append("\" class=\"link-chip\" target=\"_blank\" rel=\"noreferrer\">\n");
                    sb.append("                  <span>").append(escape(label)).append("</span>\n");
                    sb.append("                  <span>").append(escape(toUrlDisplayText(href))).append("</span>\n");
                    sb.append("                </a>\n");
                    sb.append("              </div>\n");
                }
            }
            sb.append("            </div>\n");
        }

        sb.append("          </div>\n");
        sb.append("        </aside>\n");

        sb.append("        <main class=\"main\">\n");

        if (!aboutParas.isEmpty()) {
            sb.append("          <section class=\"section\">\n");
            sb.append("            <div class=\"section-header\">\n");
            sb.append("              <div class=\"section-marker\"></div>\n");
            sb.append("              <div>\n");
            sb.append("                <div class=\"section-title\">ABOUT ME</div>\n");
            sb.append("                <div class=\"section-subtitle\">소개</div>\n");
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("            <div class=\"section-body about-text\">\n");
            for (String p : aboutParas) {
                if (p == null) {
                    continue;
                }
                String t = p.trim();
                if (t.isEmpty()) {
                    continue;
                }
                sb.append("              <p>").append(escape(t)).append("</p>\n");
            }
            sb.append("            </div>\n");
            sb.append("          </section>\n");
        }

        if (repos != null && !repos.isEmpty()) {
            sb.append("          <section class=\"section\">\n");
            sb.append("            <div class=\"section-header\">\n");
            sb.append("              <div class=\"section-marker\"></div>\n");
            sb.append("              <div>\n");
            sb.append("                <div class=\"section-title\">PROJECTS</div>\n");
            sb.append("                <div class=\"section-subtitle\">GitHub & 산학 프로젝트</div>\n");
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("            <div class=\"section-body\">\n");
            sb.append("              <div class=\"projects-grid\">\n");
            for (RepoEntryResponse r : repos) {
                if (r == null) {
                    continue;
                }
                String repoTitle = r.getCustom_title() != null && !r.getCustom_title().trim().isEmpty()
                        ? r.getCustom_title().trim()
                        : r.getGithub_title();
                if (repoTitle == null || repoTitle.trim().isEmpty()) {
                    repoTitle = "Repository";
                }
                String desc = repoDisplayDescription(r);
                String link = r.getHtml_url() != null ? r.getHtml_url().trim() : "#";
                String repoMeta = buildRepoDateRangeText(r.getCreated_at(), r.getUpdated_at());
                sb.append("                <article class=\"project-card\">\n");
                sb.append("                  <div class=\"project-header\">\n");
                sb.append("                    <div class=\"project-name\">").append(escape(repoTitle)).append("</div>\n");
                sb.append("                    <div class=\"project-meta\">").append(escape(repoMeta)).append("</div>\n");
                sb.append("                  </div>\n");
                sb.append("                  <div class=\"project-desc\">").append(escape(desc)).append("</div>\n");
                sb.append("                  <div class=\"project-footer\">\n");
                sb.append("                    <div class=\"stack-badges\">\n");
                for (RepoLanguageDto lang : getRepoLanguagesForDisplay(r)) {
                    if (lang == null || lang.getName() == null) {
                        continue;
                    }
                    String ln = lang.getName().trim();
                    if (ln.isEmpty()) {
                        continue;
                    }
                    String badgeText = ln;
                    if (lang.getPercentage() != null) {
                        badgeText = ln + " (" + lang.getPercentage().intValue() + "%)";
                    }
                    sb.append("                      <div class=\"stack-badge\">").append(escape(badgeText)).append("</div>\n");
                }
                if (r.getCommit_count() != null) {
                    sb.append("                      <div class=\"stack-badge\">").append(r.getCommit_count()).append(" commits</div>\n");
                }
                if (r.getStargazers_count() != null) {
                    sb.append("                      <div class=\"stack-badge\">★ ").append(r.getStargazers_count()).append("</div>\n");
                }
                if (r.getForks_count() != null) {
                    sb.append("                      <div class=\"stack-badge\">Forks ").append(r.getForks_count()).append("</div>\n");
                }
                sb.append("                    </div>\n");
                sb.append("                    <a href=\"").append(escape(link)).append("\" class=\"link-chip\" target=\"_blank\" rel=\"noreferrer\">\n");
                sb.append("                      <span>🔗</span>\n");
                sb.append("                      <span>GitHub 보기</span>\n");
                sb.append("                    </a>\n");
                sb.append("                  </div>\n");
                sb.append("                </article>\n");
            }
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("          </section>\n");
        }

        if (mileageList != null && !mileageList.isEmpty()) {
            sb.append("          <section class=\"section\">\n");
            sb.append("            <div class=\"section-header\">\n");
            sb.append("              <div class=\"section-marker\"></div>\n");
            sb.append("              <div>\n");
            sb.append("                <div class=\"section-title\">CURRICULAR & EXTRACURRICULAR</div>\n");
            sb.append("                <div class=\"section-subtitle\">전공 교과 · 비교과</div>\n");
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("            <div class=\"section-body\">\n");
            sb.append("              <div class=\"timeline\">\n");
            for (MileageEntryResponse m : mileageList) {
                if (m == null) {
                    continue;
                }
                String sem = m.getSemester() != null ? m.getSemester().trim() : "";
                String cat = m.getCategoryName() != null ? m.getCategoryName().trim() : "";
                String sub = m.getSubitemName() != null ? m.getSubitemName().trim() : "";
                String add = m.getAdditional_info() != null ? m.getAdditional_info().trim() : "";
                String d1 = m.getDescription1() != null ? m.getDescription1().trim() : "";
                String titleText = !sub.isEmpty() ? sub : cat;
                if (titleText == null) {
                    titleText = "";
                }
                String dateText = buildMileageDateText(sem, cat);
                String descText = buildMileageDescText(add, d1);
                if (titleText.trim().isEmpty() && dateText.trim().isEmpty() && descText.trim().isEmpty()) {
                    continue;
                }
                sb.append("                <div class=\"timeline-item\">\n");
                sb.append("                  <div class=\"timeline-dot\"></div>\n");
                if (!titleText.trim().isEmpty()) {
                    sb.append("                  <div class=\"timeline-title\">").append(escape(titleText)).append("</div>\n");
                }
                sb.append("                  <div class=\"timeline-date\">").append(escape(dateText)).append("</div>\n");
                if (!descText.trim().isEmpty()) {
                    sb.append("                  <div class=\"timeline-desc\">").append(escape(descText)).append("</div>\n");
                }
                sb.append("                </div>\n");
            }
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("          </section>\n");
        }

        List<MileageEntryResponse> awardMileages = new ArrayList<>();
        if (mileageList != null) {
            for (MileageEntryResponse m : mileageList) {
                if (m != null && mileageLooksLikeAward(m)) {
                    awardMileages.add(m);
                }
            }
        }
        if (!awardMileages.isEmpty()) {
            sb.append("          <section class=\"section\">\n");
            sb.append("            <div class=\"section-header\">\n");
            sb.append("              <div class=\"section-marker\"></div>\n");
            sb.append("              <div>\n");
            sb.append("                <div class=\"section-title\">ACHIEVEMENTS</div>\n");
            sb.append("                <div class=\"section-subtitle\">수상 · 대외 성과</div>\n");
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("            <div class=\"section-body\">\n");
            sb.append("              <div class=\"achievements-box\">\n");
            sb.append("                <div class=\"achievements-title\"><span>\uD83C\uDFC6</span> Highlights</div>\n");
            sb.append("                <ul class=\"achievements-list\">\n");
            for (MileageEntryResponse m : awardMileages) {
                String sem = m.getSemester() != null ? m.getSemester().trim() : "";
                String sub = m.getSubitemName() != null ? m.getSubitemName().trim() : "";
                String cat = m.getCategoryName() != null ? m.getCategoryName().trim() : "";
                String line = buildMileageDateText(sem, cat);
                if (!sub.isEmpty()) {
                    line = line.isEmpty() ? sub : sub + " — " + line;
                }
                String desc = buildMileageDescText(
                        m.getAdditional_info() != null ? m.getAdditional_info().trim() : "",
                        m.getDescription1() != null ? m.getDescription1().trim() : "");
                if (!desc.isEmpty()) {
                    line += ": " + desc;
                }
                if (!line.trim().isEmpty()) {
                    sb.append("                  <li>").append(escape(line.trim())).append("</li>\n");
                }
            }
            sb.append("                </ul>\n");
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("          </section>\n");
        }

        if (activities != null && !activities.isEmpty()) {
            sb.append("          <section class=\"section\">\n");
            sb.append("            <div class=\"section-header\">\n");
            sb.append("              <div class=\"section-marker\"></div>\n");
            sb.append("              <div>\n");
            sb.append("                <div class=\"section-title\">ACTIVITIES & EXPERIENCE</div>\n");
            sb.append("                <div class=\"section-subtitle\">활동 및 경험</div>\n");
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("            <div class=\"section-body\">\n");
            sb.append("              <div class=\"timeline\">\n");
            for (ActivityResponse a : activities) {
                if (a == null) {
                    continue;
                }
                String at = a.getTitle() != null ? a.getTitle().trim() : "";
                String desc = a.getDescription() != null ? a.getDescription().trim() : "";
                String start = a.getStart_date() != null ? a.getStart_date().toString() : "";
                String end = a.getEnd_date() != null ? a.getEnd_date().toString() : "";
                String range = buildDateRangeText(start, end);
                if (at.isEmpty() && desc.isEmpty() && range.isEmpty()) {
                    continue;
                }
                sb.append("                <div class=\"timeline-item\">\n");
                sb.append("                  <div class=\"timeline-dot\"></div>\n");
                sb.append("                  <div class=\"timeline-title\">").append(escape(at)).append("</div>\n");
                sb.append("                  <div class=\"timeline-date\">").append(escape(range)).append("</div>\n");
                if (!desc.isEmpty()) {
                    sb.append("                  <div class=\"timeline-desc\">").append(escape(desc)).append("</div>\n");
                }
                sb.append("                </div>\n");
            }
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("          </section>\n");
        }

        sb.append("        <footer>\n");
        sb.append("          <span>이 포트폴리오는 실제 활동 및 GitHub 레포지토리 정보를 기반으로 자동 생성되었습니다.</span>\n");
        if (githubUrl != null && !githubUrl.trim().isEmpty()) {
            sb.append("          <a href=\"").append(escape(githubUrl.trim())).append("\" target=\"_blank\" rel=\"noreferrer\">GitHub</a>\n");
        }
        if (email != null && !email.trim().isEmpty()) {
            sb.append("          <a href=\"mailto:").append(escape(email.trim())).append("\">Email</a>\n");
        }
        sb.append("        </footer>\n");

        sb.append("        </main>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private boolean hasTechStackEntries(TechStackResponse techStack) {
        if (techStack == null || techStack.getDomains() == null) {
            return false;
        }
        for (TechStackDomainResponse d : techStack.getDomains()) {
            if (d != null && d.getTech_stacks() != null && !d.getTech_stacks().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void appendProfileImageTag(StringBuilder sb, String profileImgSrc) {
        if (profileImgSrc == null || profileImgSrc.trim().isEmpty()) {
            return;
        }
        if (profileImgSrc.startsWith("data:")) {
            sb.append("            <img src=\"").append(profileImgSrc).append("\" alt=\"Profile\" class=\"profile-img\" />\n");
        } else {
            sb.append("            <img src=\"").append(escape(profileImgSrc)).append("\" alt=\"Profile\" class=\"profile-img\" />\n");
        }
    }

    /** blueStyle: `.role` and `.summary-chip` from [bio] blocks (first / second segment or first line / rest). */
    private static String[] deriveRoleAndSummaryChip(List<String> paras) {
        if (paras == null || paras.isEmpty()) {
            return new String[] { "", "" };
        }
        if (paras.size() >= 2) {
            return new String[] { paras.get(0).trim(), paras.get(1).trim() };
        }
        String one = paras.get(0);
        int nl = one.indexOf('\n');
        if (nl > 0) {
            return new String[] { one.substring(0, nl).trim(), one.substring(nl + 1).trim() };
        }
        String t = one.trim();
        return new String[] { t, t };
    }

    /** ABOUT ME paragraphs: from second segment onward (duplicates summary line when3+ segments), blueStyle. */
    private static List<String> buildBlueStyleAboutParagraphs(List<String> paras) {
        List<String> about = new ArrayList<>();
        if (paras == null || paras.isEmpty()) {
            return about;
        }
        if (paras.size() >= 3) {
            for (int i = 1; i < paras.size(); i++) {
                about.add(paras.get(i).trim());
            }
        } else if (paras.size() == 2) {
            about.add(paras.get(1).trim());
        } else {
            String one = paras.get(0);
            int nl = one.indexOf('\n');
            if (nl > 0) {
                String after = one.substring(nl + 1).trim();
                for (String line : after.split("\\r?\\n")) {
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        about.add(t);
                    }
                }
                if (about.isEmpty() && !after.isEmpty()) {
                    about.add(after);
                }
            } else {
                about.add(one.trim());
            }
        }
        return about;
    }

    private List<String> splitBioParagraphs(String bio) {
        if (bio == null) {
            return Collections.emptyList();
        }
        String t = bio.trim();
        if (t.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = t.split("\\r?\\n+");
        List<String> out = new ArrayList<String>();
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String s = p.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private String buildMetaLine(UserInfoResponse u) {
        if (u == null) {
            return "";
        }
        String g = u.getGrade() != null ? String.valueOf(u.getGrade()) : "";
        String s = u.getSemester() != null ? String.valueOf(u.getSemester()) : "";
        if (g.isEmpty() && s.isEmpty()) {
            return "";
        }
        if (!g.isEmpty() && !s.isEmpty()) {
            return g + "학년 " + s + "학기";
        }
        if (!g.isEmpty()) {
            return g + "학년";
        }
        return s + "학기";
    }

    private String buildDateRangeText(String start, String end) {
        String st = start != null ? start.trim() : "";
        String en = end != null ? end.trim() : "";
        if (st.isEmpty() && en.isEmpty()) {
            return "";
        }
        if (!st.isEmpty() && !en.isEmpty()) {
            return st + " ~ " + en;
        }
        return !st.isEmpty() ? st : en;
    }

    private String buildMileageDateText(String sem, String cat) {
        String s = sem != null ? sem.trim() : "";
        String c = cat != null ? cat.trim() : "";
        if (s.isEmpty() && c.isEmpty()) {
            return "";
        }
        if (!s.isEmpty() && !c.isEmpty()) {
            return s + " · " + c;
        }
        return !s.isEmpty() ? s : c;
    }

    private String buildMileageDescText(String add, String d1) {
        String a = add != null ? add.trim() : "";
        String d = d1 != null ? d1.trim() : "";
        if (a.isEmpty() && d.isEmpty()) {
            return "";
        }
        if (!a.isEmpty() && !d.isEmpty() && !a.equals(d)) {
            return a + " · " + d;
        }
        return !a.isEmpty() ? a : d;
    }

    private String buildRepoDateRangeText(String createdAt, String updatedAt) {
        String c = isoDatePrefixOrEmpty(createdAt);
        String u = isoDatePrefixOrEmpty(updatedAt);
        if (c.isEmpty() && u.isEmpty()) {
            return "GitHub 레포지토리";
        }
        if (!c.isEmpty() && u.isEmpty()) {
            return c;
        }
        if (c.isEmpty() && !u.isEmpty()) {
            return u;
        }
        if (c.equals(u)) {
            return c;
        }
        return c + " → " + u;
    }

    private String isoDatePrefixOrEmpty(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return "";
        }
        return t.length() >= 10 ? t.substring(0, 10) : t;
    }

    private String toGithubDisplayText(String githubUrl) {
        if (githubUrl == null) {
            return "";
        }
        String trimmed = githubUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            URL u = new URL(trimmed);
            String host = u.getHost() != null ? u.getHost() : "";
            String path = u.getPath() != null ? u.getPath() : "";
            if (!host.isEmpty() && !path.isEmpty()) {
                if ("www.github.com".equalsIgnoreCase(host)) {
                    host = "github.com";
                }
                return host + path;
            }
        } catch (MalformedURLException ignored) {
            /* fall through */
        }
        return trimmed;
    }

    private String toUrlDisplayText(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            URL u = new URL(trimmed);
            String host = u.getHost() != null ? u.getHost() : "";
            String path = u.getPath() != null ? u.getPath() : "";
            if (!host.isEmpty()) {
                if (path == null || path.isEmpty() || "/".equals(path)) {
                    return host;
                }
                return host + path;
            }
        } catch (MalformedURLException ignored) {
            /* fall through */
        }
        return trimmed;
    }

    /**
     * Returns a safe http(s) URL for use in {@code href}, or null if not usable.
     * Accepts URLs without scheme and normalizes them to {@code https://...}.
     */
    private String normalizeExternalHttpUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String s = rawUrl.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("//")) {
            s = "https:" + s;
        }
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://" + s;
        }
        try {
            URL u = new URL(s);
            String protocol = u.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return null;
            }
            if (u.getHost() == null || u.getHost().isEmpty()) {
                return null;
            }
            return u.toString();
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private String schoolDept(UserInfoResponse u) {
        StringBuilder s = new StringBuilder();
        if (u.getDepartment() != null) s.append(u.getDepartment()).append(" ");
        if (u.getMajor1() != null) s.append(u.getMajor1());
        if (u.getMajor2() != null && !u.getMajor2().isEmpty()) s.append(" ").append(u.getMajor2());
        if (u.getGrade() != null || u.getSemester() != null) {
            s.append(" (").append(u.getGrade() != null ? u.getGrade() : "?").append("학년 ");
            s.append(u.getSemester() != null ? u.getSemester() : "?").append("학기)");
        }
        return s.toString().trim();
    }

    /** School / major line only (blueStyle sidebar `.school` — grade·semester go in `.meta-line`). */
    private String schoolDeptSansGrade(UserInfoResponse u) {
        StringBuilder s = new StringBuilder();
        if (u.getDepartment() != null) {
            s.append(u.getDepartment()).append(" ");
        }
        if (u.getMajor1() != null) {
            s.append(u.getMajor1());
        }
        if (u.getMajor2() != null && !u.getMajor2().isEmpty()) {
            s.append(" ").append(u.getMajor2());
        }
        return s.toString().trim();
    }

    private static boolean mileageLooksLikeAward(MileageEntryResponse m) {
        if (m == null) {
            return false;
        }
        String blob = String.join(" ",
                nullToEmptyStatic(m.getSubitemName()),
                nullToEmptyStatic(m.getCategoryName()),
                nullToEmptyStatic(m.getAdditional_info()),
                nullToEmptyStatic(m.getDescription1()));
        return blob.contains("\uC218\uC0C1")
                || blob.contains("\uC6B0\uC218")
                || blob.contains("\uAE08\uC0C1")
                || blob.contains("\uC740\uC0C1")
                || blob.contains("\uB3D9\uC0C1")
                || blob.contains("\uACBD\uC9C4")
                || blob.toLowerCase().contains("award");
    }

    private static String nullToEmptyStatic(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Inline base64 when file exists; otherwise context-path-relative image URL (no hostname). */
    private String buildProfileImageSrc(UserInfoResponse userInfo) {
        return buildProfileImageSrcFromFilename(userInfo.getProfile_image_url());
    }

    private String buildProfileImageSrcFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        try {
            Path filePath = Paths.get(profileUploadDir).resolve(filename).normalize();
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                byte[] bytes = Files.readAllBytes(filePath);
                String mime = Files.probeContentType(filePath);
                if (mime == null) {
                    if (filename.toLowerCase().endsWith(".png")) mime = "image/png";
                    else if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) mime = "image/jpeg";
                    else if (filename.toLowerCase().endsWith(".gif")) mime = "image/gif";
                    else if (filename.toLowerCase().endsWith(".webp")) mime = "image/webp";
                    else mime = "image/png";
                }
                String b64 = Base64.getEncoder().encodeToString(bytes);
                return "data:" + mime + ";base64," + b64;
            }
        } catch (IOException ignored) {
            /* fall through to relative URL */
        }
        String rel = buildProfileImageUploadRelativeUrl(filename);
        return escape(rel);
    }

    /**
     * {@code /milestone25/api/portfolio/user-info/image/...} when context-path is {@code /milestone25};
     * {@code /api/...} when context-path is empty (local default).
     */
    private String buildProfileImageUploadRelativeUrl(String filename) {
        String prefix = servletContextPath != null ? servletContextPath.trim() : "";
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/api/portfolio/user-info/image/"
                + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String PROMPT_HEAD =
            "# ROLE\n"
            + "You are an expert career coach and frontend developer specializing in creating recruiter-optimized portfolio websites for entry-level CS students in Korea. You have reviewed 10,000+ CS student resumes and know exactly what hiring managers look for in the first 30 seconds.\n\n"
            + "---\n\n"
            + "# TASK\n"
            + "Generate a single-file HTML portfolio for a CS student based strictly on the input data provided. The design should follow a classic resume/portfolio style — clean, professional, easy to print, and readable in Korean.\n\n"
            + "---\n\n"
            + "# STEP 1: INTAKE & CLARIFICATION (Do this BEFORE generating anything)\n\n"
            + "When the user provides their data, you must:\n\n"
            + "1. Summarize what you received in a short structured list:\n"
            + "   - 확인된 정보: (list what you have)\n"
            + "   - ❓ 부족하거나 불명확한 정보: (list what's missing or unclear)\n\n"
            + "2. Ask focused follow-up questions — maximum 5 questions, only about missing info that would meaningfully impact the portfolio. Group them clearly. Do NOT ask about things already provided.\n\n"
            + "3. Show a text preview of the portfolio structure before generating HTML:\n"
            + "```\n"
            + "📋 생성 예정 포트폴리오 구조:\n"
            + "- Header: [이름] · [지망 직무]\n"
            + "- About Me: [2-3줄 미리보기]\n"
            + "- Tech Stack: [기술 나열]\n"
            + "- Projects: [프로젝트명 1, 2, 3]\n"
            + "- Activities: [활동 요약]\n"
            + "- Contact: [이메일, GitHub]\n"
            + "```\n"
            + "   Then ask: \"이 구조로 HTML을 생성할까요? 수정할 부분이 있으면 말씀해주세요.\"\n\n"
            + "4. Only generate HTML after the user confirms.\n\n"
            + "---\n\n"
            + "# STEP 2: INPUT DATA (User fills this in)\n\n"
            + "```\n";

    private String buildCvPromptTail() {
        return "\n```\n\n"
                + "---\n\n"
                + "# STEP 3: GENERATION RULES — MUST FOLLOW ALL\n\n"
                + "## RULE 1: Strict Priority Order\n"
                + "Feature content in this order. Skip silently if empty:\n"
                + "1. GitHub repos + 산학 프로젝트 — Real artifacts, highest credibility\n"
                + "2. 전공 교과/비교과 — Technical depth\n"
                + "3. Activities + 대외 활동 — Collaboration, leadership\n"
                + "4. Bio — Minimal, one short paragraph only\n\n"
                + "## RULE 2: Zero Fabrication Policy\n"
                + "- NEVER invent metrics, links, or project details not in the input\n"
                + "- Empty fields → omit that section entirely, no placeholders\n"
                + "- Do not infer technologies not explicitly mentioned\n\n"
                + "## RULE 3: Language Calibration\n"
                + "| Raw Input | Rewritten As |\n"
                + "|---|---|\n"
                + "| \"열심히 했다\" | \"[기술명]을 활용해 [기능] 구현\" |\n"
                + "| \"팀장을 맡았다\" | \"팀 리드로서 [구체적 결과]\" |\n"
                + "| \"공부했다\" | \"[기술명] 학습 및 적용\" |\n"
                + "| \"Senior\", \"Lead\", \"전국 1위\" | 입력에 명시된 경우에만 그대로 사용 |\n\n"
                + "## RULE 4: Honesty Modifiers\n"
                + "- Always specify solo vs. team project (팀 N명 중 담당 파트)\n"
                + "- Do not claim \"full-stack\" unless both frontend + backend evidence exist\n"
                + "- Only list tech stack items demonstrated in actual projects or coursework\n\n"
                + "## RULE 5: Achievements = Top Priority Visual\n"
                + "- Any award or prize must appear prominently (badges or highlighted section)\n"
                + "- Include prize tier if provided (대상, 최우수, 우수 등)\n\n"
                + "---\n\n"
                + "# STEP 4: HTML — **blueStyle** (mandatory)\n\n"
                + "## 4-A: CSS — COPY THIS BLOCK VERBATIM. Do not change any value, color, or class name.\n\n"
                + "```html\n"
                + "<style>\n"
                + CSS
                + "\n</style>\n"
                + "```\n\n"
                + "## 4-B: Head — use exactly this structure\n"
                + "```html\n"
                + "<!DOCTYPE html>\n"
                + "<html lang=\"ko\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\" />\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
                + "  <title>[name] · Portfolio</title>\n"
                + "  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />\n"
                + "  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />\n"
                + "  <link href=\"https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap\" rel=\"stylesheet\" />\n"
                + "  [PASTE THE VERBATIM <style> BLOCK FROM 4-A HERE]\n"
                + "</head>\n"
                + "```\n\n"
                + "## 4-C: Body skeleton — use **exactly** these class names\n"
                + "```html\n"
                + "<body>\n"
                + "  <div class=\"page\">\n"
                + "    <div class=\"card\">\n"
                + "      <div class=\"card-inner\">\n"
                + "        <aside class=\"sidebar\">\n"
                + "          <div class=\"profile\">\n"
                + "            <div class=\"name\">[이름]</div>\n"
                + "            <div class=\"role\">[지망 직무/방향]</div>\n"
                + "            <div class=\"school\">[학과]</div>\n"
                + "            <div class=\"meta-line\">[학년/학기]</div>\n"
                + "            <div class=\"summary-chip\"><span>[핵심 한줄]</span></div>\n"
                + "            <div class=\"section\" style=\"margin-bottom:0;\">\n"
                + "              <div class=\"section-header\" style=\"margin-bottom:6px;\">\n"
                + "                <div class=\"section-marker\"></div>\n"
                + "                <div><div class=\"section-title\" style=\"font-size:13px;\">TECH STACK</div></div>\n"
                + "              </div>\n"
                + "              <div class=\"pill-group\">\n"
                + "                <div class=\"pill\">[tech1]</div>\n"
                + "              </div>\n"
                + "            </div>\n"
                + "            <div class=\"contact-block\">\n"
                + "              <div class=\"contact-label\">Contact</div>\n"
                + "              <div class=\"contact-item\"><span class=\"icon\">📧</span><a href=\"mailto:[email]\">[email]</a></div>\n"
                + "              <div class=\"contact-item\"><span class=\"icon\">🐙</span>\n"
                + "                <a href=\"[github]\" class=\"link-chip\" target=\"_blank\" rel=\"noreferrer\">\n"
                + "                  <span>GitHub</span><span>[github display]</span></a></div>\n"
                + "            </div>\n"
                + "          </div>\n"
                + "        </aside>\n"
                + "        <main class=\"main\">\n"
                + "          <section class=\"section\">\n"
                + "            <div class=\"section-header\">\n"
                + "              <div class=\"section-marker\"></div>\n"
                + "              <div>\n"
                + "                <div class=\"section-title\">[SECTION TITLE]</div>\n"
                + "                <div class=\"section-subtitle\">[subtitle]</div>\n"
                + "              </div>\n"
                + "            </div>\n"
                + "            <div class=\"section-body\">\n"
                + "              <!-- Prose: .about-text + <p> -->\n"
                + "              <!-- Lists: .timeline + .timeline-item -->\n"
                + "              <!-- Achievements: .achievements-box -->\n"
                + "              <!-- Projects: .project-card -->\n"
                + "            </div>\n"
                + "          </section>\n"
                + "          <footer>[footer text]</footer>\n"
                + "        </main>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "</body>\n"
                + "```\n\n"
                + "---\n\n"
                + "# STEP 5: QUALITY CHECKLIST (Self-verify silently before outputting HTML)\n"
                + "- [ ] Clarification questions were asked and answered before generating\n"
                + "- [ ] Text preview was confirmed by user before generating HTML\n"
                + "- [ ] `<style>` block is copied verbatim from 4-A\n"
                + "- [ ] No invented data (metrics, links, names not in input)\n"
                + "- [ ] All tech stack badges appear in at least one project or course\n"
                + "- [ ] Max 3 projects, ordered by technical weight\n"
                + "- [ ] About Me is ≤ 3 sentences\n"
                + "- [ ] Empty input fields → sections omitted entirely\n"
                + "- [ ] HTML is a single file, fully self-contained\n"
                + "- [ ] Korean text renders correctly with Noto Sans KR\n"
                + "- [ ] Print stylesheet included\n"
                + "- [ ] blueStyle class names and layout (sidebar + main + timelines + project cards)\n";
    }

    /** Archive / self-assessment prompt — never mix with {@link #PROMPT_HEAD}. */
    private static final String ARCHIVE_PROMPT_HEAD =
            "# ROLE\n"
            + "You are a calm, honest mentor for CS students in Korea. Your job is to help them see their real progress, name strengths grounded in evidence, surface gaps visible from what is (and is not) in their data, and suggest realistic next steps—not to sell them to recruiters.\n\n"
            + "---\n\n"
            + "# TASK\n"
            + "Generate a single-file, self-contained HTML page from STEP 2 only. Tone: neutral and reflective. The reader is the student themselves (and maybe an advisor), not a hiring manager.\n\n"
            + "---\n\n"
            + "# STEP 1: ONE-SHOT (Archive mode)\n"
            + "Do **not** ask clarifying questions. Do **not** wait for confirmation. Do **not** simulate a chat. Read STEP 2 and output the HTML in this single response.\n\n"
            + "---\n\n"
            + "# STEP 2: INPUT DATA\n\n"
            + "```\n";

    private static final String CSS =
            ":root{--bg:#f9fafb;--card-bg:#ffffff;--text-main:#111827;--text-sub:#4b5563;--accent:#2563eb;--accent-soft:#e5edff;--border:#e5e7eb;}"
            + "*{box-sizing:border-box;margin:0;padding:0;}"
            + "body{font-family:'Noto Sans KR','Inter',system-ui,-apple-system,BlinkMacSystemFont,sans-serif;background-color:var(--bg);color:var(--text-main);line-height:1.6;}"
            + ".page{max-width:960px;margin:32px auto;padding:0 16px 40px;}"
            + ".card{background-color:var(--card-bg);border-radius:16px;box-shadow:0 10px 30px rgba(15,23,42,0.08);border:1px solid rgba(148,163,184,0.25);overflow:hidden;}"
            + ".card-inner{display:flex;flex-direction:row;}"
            + ".sidebar{width:260px;background:radial-gradient(circle at top left,#e5edff 0,#ffffff 55%,#f9fafb 100%);border-right:1px solid rgba(148,163,184,0.25);padding:32px 24px;}"
            + ".main{flex:1;padding:28px 32px 32px;}"
            + ".profile-img{width:86px;height:86px;border-radius:24px;object-fit:cover;margin-bottom:14px;display:block;}"
            + ".name{font-size:26px;font-weight:700;letter-spacing:-0.03em;margin-bottom:4px;color:#0f172a;}"
            + ".role{font-size:14px;font-weight:600;color:var(--accent);margin-bottom:10px;}"
            + ".school{font-size:13px;color:var(--text-sub);margin-bottom:4px;}"
            + ".meta-line{font-size:12px;color:#6b7280;margin-bottom:16px;}"
            + ".summary-chip{font-size:12px;color:#1d4ed8;background-color:var(--accent-soft);border-radius:999px;padding:5px 11px;display:inline-flex;align-items:center;gap:6px;margin-bottom:18px;}"
            + ".summary-chip span{font-weight:600;}"
            + ".contact-block{margin-top:16px;padding-top:12px;border-top:1px dashed rgba(148,163,184,0.6);}"
            + ".contact-label{font-size:12px;font-weight:600;color:#6b7280;margin-bottom:6px;text-transform:uppercase;letter-spacing:0.08em;}"
            + ".contact-item{font-size:13px;color:var(--text-main);display:flex;align-items:center;gap:8px;margin-bottom:6px;word-break:break-all;}"
            + ".contact-item span.icon{font-size:14px;}"
            + ".pill-group{display:flex;flex-wrap:wrap;gap:6px;}"
            + ".pill{font-size:11px;font-weight:600;letter-spacing:0.03em;text-transform:uppercase;color:#1d4ed8;background-color:#e5edff;border-radius:999px;padding:4px 9px;border:1px solid rgba(37,99,235,0.18);}"
            + ".section{margin-bottom:22px;}"
            + ".section-header{display:flex;align-items:center;gap:8px;margin-bottom:10px;}"
            + ".section-marker{width:4px;height:18px;border-radius:999px;background:linear-gradient(180deg,#2563eb,#4f46e5);}"
            + ".section-title{font-size:15px;font-weight:600;letter-spacing:0.06em;text-transform:uppercase;color:#111827;}"
            + ".section-subtitle{font-size:12px;color:#9ca3af;letter-spacing:0.02em;text-transform:uppercase;}"
            + ".section-body{font-size:13px;color:var(--text-main);}"
            + ".about-text p + p{margin-top:6px;}"
            + ".projects-grid{display:flex;flex-direction:column;gap:12px;}"
            + ".project-card{background-color:#ffffff;border-radius:10px;border-left:3px solid var(--accent);border-right:1px solid rgba(148,163,184,0.35);border-top:1px solid rgba(148,163,184,0.25);border-bottom:1px solid rgba(148,163,184,0.35);padding:10px 12px 10px 14px;box-shadow:0 6px 14px rgba(15,23,42,0.06);}"
            + ".project-header{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:4px;}"
            + ".project-name{font-size:13px;font-weight:600;color:#0f172a;}"
            + ".project-meta{font-size:11px;color:#6b7280;text-align:right;white-space:nowrap;}"
            + ".project-desc{font-size:12px;color:#4b5563;margin-bottom:6px;}"
            + ".project-footer{display:flex;justify-content:space-between;align-items:center;gap:8px;margin-top:4px;}"
            + ".stack-badges{display:flex;flex-wrap:wrap;gap:4px;}"
            + ".stack-badge{font-size:11px;padding:3px 7px;border-radius:999px;background-color:#eff6ff;color:#1d4ed8;border:1px solid rgba(37,99,235,0.25);}"
            + ".link-chip{font-size:11px;color:#1d4ed8;text-decoration:none;display:inline-flex;align-items:center;gap:4px;padding:3px 7px;border-radius:999px;background-color:#eff6ff;border:1px solid rgba(37,99,235,0.25);}"
            + ".link-chip span{font-size:11px;}"
            + ".timeline{position:relative;padding-left:14px;margin-top:4px;}"
            + ".timeline::before{content:'';position:absolute;left:4px;top:2px;bottom:4px;width:1px;background:linear-gradient(to bottom,#e5e7eb,#d1d5db);}"
            + ".timeline-item{position:relative;padding-left:12px;margin-bottom:10px;}"
            + ".timeline-dot{position:absolute;left:-2px;top:4px;width:7px;height:7px;border-radius:999px;background-color:var(--accent);box-shadow:0 0 0 3px #e5edff;}"
            + ".timeline-title{font-size:12px;font-weight:600;color:#0f172a;}"
            + ".timeline-date{font-size:11px;color:#6b7280;margin-top:1px;}"
            + ".timeline-desc{font-size:12px;color:#4b5563;margin-top:2px;}"
            + ".achievements-box{border-radius:10px;border:1px solid #fed7aa;background:#fffbeb;padding:10px 12px;}"
            + ".achievements-title{display:flex;align-items:center;gap:6px;font-size:13px;font-weight:600;color:#92400e;margin-bottom:4px;}"
            + ".achievements-list{font-size:12px;color:#7c2d12;padding-left:16px;}"
            + "footer{margin-top:18px;font-size:11px;color:#6b7280;text-align:right;border-top:1px solid rgba(226,232,240,0.9);padding-top:8px;}"
            + "footer a{color:#1d4ed8;text-decoration:none;margin-left:8px;}"
            + "footer a:hover{text-decoration:underline;}"
            + "@media (max-width:768px){.card-inner{flex-direction:column;}.sidebar{width:100%;border-right:none;border-bottom:1px solid rgba(148,163,184,0.25);}.main{padding:20px 18px 22px;}.name{font-size:22px;}.section{margin-bottom:18px;}.project-card{padding:9px 10px 9px 12px;}}"
            + "@media print{body{background-color:#ffffff;}.page{margin:0;padding:0;}.card{box-shadow:none;border-radius:0;border:none;}.sidebar{border-right:1px solid #e5e7eb;}.project-card{box-shadow:none;}a{color:#000000;text-decoration:none;}}";

    /**
     * Archive prompt tail: embeds {@link #CSS} verbatim so the model copies the same stylesheet as server export.
     */
    private String buildArchivePromptTail() {
        return "\n```\n\n"
                + "---\n\n"
                + "# STEP 3: RULES — MUST FOLLOW\n\n"
                + "## RULE 1: Ordering of evidence (match the narrative to STEP 2 section order)\n"
                + "1. After intro/context, present **mileage_list** items in the order given (already sorted by semester, then category).\n"
                + "2. Then **activities** in the order given (chronological by start date).\n"
                + "3. Then **github_repos** in the order given (newest `updated_at` first when present).\n"
                + "4. **Tech stack** and **bio** support the story; do not reorder STEP 2 blocks when quoting structure.\n\n"
                + "## RULE 2: Honest reflection (not resume hype)\n"
                + "- Describe what the data actually shows: what they did, what they likely learned, where evidence is thin.\n"
                + "- Do **not** use recruiter-style puffery or the \"language calibration\" rewrite table from sales CVs.\n\n"
                + "## RULE 3: Gaps and growth (evidence-bound)\n"
                + "- Call out **gaps** only from what is missing, empty, or weak in STEP 2.\n"
                + "- Suggest **growth areas** as concrete, student-appropriate next steps — not invented achievements.\n\n"
                + "## RULE 4: Zero fabrication\n"
                + "- Never invent metrics, employers, awards, or repos not in STEP 2.\n"
                + "- If a section has no lines, omit it or note clearly that nothing was selected.\n\n"
                + "---\n\n"
                + "# STEP 4: HTML — **blueStyle** (mandatory)\n\n"
                + "## 4-A: CSS — COPY THIS BLOCK VERBATIM. Do not change any value, color, or class name.\n\n"
                + "```html\n"
                + "<style>\n"
                + CSS
                + "\n</style>\n"
                + "```\n\n"
                + "## 4-B: Head — use exactly this structure\n"
                + "```html\n"
                + "<!DOCTYPE html>\n"
                + "<html lang=\"ko\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\" />\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
                + "  <title>[name] · Reflection Profile</title>\n"
                + "  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />\n"
                + "  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />\n"
                + "  <link href=\"https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap\" rel=\"stylesheet\" />\n"
                + "  [PASTE THE VERBATIM <style> BLOCK FROM 4-A HERE]\n"
                + "</head>\n"
                + "```\n\n"
                + "## 4-C: Body skeleton — use **exactly** these class names\n"
                + "```html\n"
                + "<body>\n"
                + "  <div class=\"page\">\n"
                + "    <div class=\"card\">\n"
                + "      <div class=\"card-inner\">\n"
                + "        <aside class=\"sidebar\">\n"
                + "          <div class=\"profile\">\n"
                + "            <div class=\"name\">[이름]</div>\n"
                + "            <div class=\"role\">[직무/방향]</div>\n"
                + "            <div class=\"school\">[학과]</div>\n"
                + "            <div class=\"meta-line\">[학년/학기]</div>\n"
                + "            <div class=\"summary-chip\"><span>[핵심 한줄]</span></div>\n"
                + "            <!-- TECH STACK -->\n"
                + "            <div class=\"section\" style=\"margin-bottom:0;\">\n"
                + "              <div class=\"section-header\" style=\"margin-bottom:6px;\">\n"
                + "                <div class=\"section-marker\"></div>\n"
                + "                <div><div class=\"section-title\" style=\"font-size:13px;\">TECH STACK</div></div>\n"
                + "              </div>\n"
                + "              <div class=\"pill-group\">\n"
                + "                <div class=\"pill\">[tech1]</div>\n"
                + "                <!-- repeat per tech -->\n"
                + "              </div>\n"
                + "            </div>\n"
                + "            <!-- CONTACT -->\n"
                + "            <div class=\"contact-block\">\n"
                + "              <div class=\"contact-label\">Contact</div>\n"
                + "              <div class=\"contact-item\"><span class=\"icon\">📧</span><a href=\"mailto:[email]\">[email]</a></div>\n"
                + "              <div class=\"contact-item\"><span class=\"icon\">🐙</span>\n"
                + "                <a href=\"[github]\" class=\"link-chip\" target=\"_blank\" rel=\"noreferrer\">\n"
                + "                  <span>GitHub</span><span>[github display]</span></a></div>\n"
                + "            </div>\n"
                + "          </div>\n"
                + "        </aside>\n"
                + "        <main class=\"main\">\n"
                + "          <!-- Each reflective section: -->\n"
                + "          <section class=\"section\">\n"
                + "            <div class=\"section-header\">\n"
                + "              <div class=\"section-marker\"></div>\n"
                + "              <div>\n"
                + "                <div class=\"section-title\">[SECTION TITLE]</div>\n"
                + "                <div class=\"section-subtitle\">[subtitle]</div>\n"
                + "              </div>\n"
                + "            </div>\n"
                + "            <div class=\"section-body\">\n"
                + "              <!-- use .about-text+<p> for prose -->\n"
                + "              <!-- use .timeline + .timeline-item for lists -->\n"
                + "              <!-- use .achievements-box for gaps/warnings -->\n"
                + "              <!-- use .project-card for project entries -->\n"
                + "            </div>\n"
                + "          </section>\n"
                + "          <footer>[footer text]</footer>\n"
                + "        </main>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "</body>\n"
                + "```\n\n"
                + "## 4-D: Section title renaming allowed\n"
                + "You may rename section **titles** for reflective tone (e.g. \"STRENGTHS\", \"GAPS & NEXT STEPS\", \"TECH EVIDENCE\") "
                + "but **never change class names**.\n\n"
                + "---\n\n"
                + "# STEP 5: CHECKLIST (silent, before you output HTML)\n"
                + "- [ ] One-shot: no questions to the user\n"
                + "- [ ] `<style>` block is **copied verbatim** from 4-A — not rewritten\n"
                + "- [ ] Sidebar uses: `.name`, `.role`, `.school`, `.meta-line`, `.summary-chip`, `.pill-group`+`.pill`, `.contact-block`+`.contact-item`+`a.link-chip`\n"
                + "- [ ] Main uses: `.section`, `.section-header`, `.section-marker`, `.section-title`, `.section-subtitle`, `.section-body`\n"
                + "- [ ] Prose → `.about-text` + `<p>` | Lists → `.timeline` + `.timeline-item` | Highlights → `.achievements-box` | Projects → `.project-card`\n"
                + "- [ ] No fabricated facts; gaps/growth tied to missing data only\n"
                + "- [ ] Single self-contained HTML file\n";
    }
}
