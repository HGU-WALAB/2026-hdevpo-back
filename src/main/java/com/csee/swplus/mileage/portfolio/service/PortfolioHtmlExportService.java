package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.portfolio.dto.*;
import com.csee.swplus.mileage.profile.entity.Profile;
import com.csee.swplus.mileage.profile.repository.ProfileRepository;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exports portfolio data as a single-file HTML page (recruiter-optimized, print-friendly).
 * Follows classic resume/portfolio style with Noto Sans KR, navy accent.
 */
@Service
@RequiredArgsConstructor
public class PortfolioHtmlExportService {

    private final PortfolioService portfolioService;
    private final ProfileRepository profileRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

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
                String title = r.getCustom_title() != null && !r.getCustom_title().isEmpty() ? r.getCustom_title() : r.getName();
                if (title == null) title = "Repository";
                String desc = r.getDescription() != null ? r.getDescription() : "";
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
        return PROMPT_HEAD + inputData + PROMPT_TAIL;
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
                String title = r.getCustom_title() != null && !r.getCustom_title().isEmpty() ? r.getCustom_title() : r.getName();
                if (title == null) title = "Repository";
                String desc = r.getDescription() != null ? r.getDescription() : "";
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

        return PROMPT_HEAD + sb.toString() + PROMPT_TAIL;
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
        String schoolDeptEsc = escape(schoolDept(userInfo));
        List<String> bioParagraphs = splitBioParagraphs(userInfo.getBio());
        String roleLine = bioParagraphs.isEmpty() ? "" : bioParagraphs.get(0);
        String summaryChip = roleLine;

        String metaLine = buildMetaLine(userInfo);
        String profileImgSrc = buildProfileImageSrc(userInfo.getProfile_image_url());

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
            sb.append("              <div class=\"pill-group\">\n");
            for (TechStackDomainResponse d : techStack.getDomains()) {
                if (d == null || d.getTech_stacks() == null) {
                    continue;
                }
                String dn = d.getName() != null ? d.getName().trim() : "";
                for (TechStackEntryResponse t : d.getTech_stacks()) {
                    if (t == null) {
                        continue;
                    }
                    String techName = t.getName() != null ? t.getName().trim() : "";
                    if (techName.isEmpty()) {
                        continue;
                    }
                    StringBuilder pillText = new StringBuilder(techName);
                    if (!dn.isEmpty()) {
                        pillText.append(" · ").append(dn);
                    }
                    if (t.getLevel() != null) {
                        pillText.append(" ").append(t.getLevel()).append("%");
                    }
                    sb.append("                <div class=\"pill\">").append(escape(pillText.toString())).append("</div>\n");
                }
            }
            sb.append("              </div>\n");
            sb.append("            </div>\n");
        }

        if ((email != null && !email.trim().isEmpty()) || (githubUrl != null && !githubUrl.trim().isEmpty())) {
            sb.append("            <div class=\"contact-block\">\n");
            sb.append("              <div class=\"contact-label\">Contact</div>\n");
            if (email != null && !email.trim().isEmpty()) {
                String emailEsc = escape(email.trim());
                sb.append("              <div class=\"contact-item\">\n");
                sb.append("                <span class=\"icon\">📧</span>\n");
                sb.append("                <a href=\"mailto:").append(emailEsc).append("\">").append(emailEsc).append("</a>\n");
                sb.append("              </div>\n");
            }
            if (githubUrl != null && !githubUrl.trim().isEmpty()) {
                String gh = githubUrl.trim();
                sb.append("              <div class=\"contact-item\">\n");
                sb.append("                <span class=\"icon\">🐙</span>\n");
                sb.append("                <a href=\"").append(escape(gh)).append("\" class=\"link-chip\" target=\"_blank\" rel=\"noreferrer\">\n");
                sb.append("                  <span>GitHub</span>\n");
                sb.append("                  <span>").append(escape(toGithubDisplayText(gh))).append("</span>\n");
                sb.append("                </a>\n");
                sb.append("              </div>\n");
            }
            sb.append("            </div>\n");
        }

        sb.append("          </div>\n");
        sb.append("        </aside>\n");

        sb.append("        <main class=\"main\">\n");

        if (bioParagraphs != null && !bioParagraphs.isEmpty()) {
            sb.append("          <section class=\"section\">\n");
            sb.append("            <div class=\"section-header\">\n");
            sb.append("              <div class=\"section-marker\"></div>\n");
            sb.append("              <div>\n");
            sb.append("                <div class=\"section-title\">ABOUT ME</div>\n");
            sb.append("                <div class=\"section-subtitle\">소개</div>\n");
            sb.append("              </div>\n");
            sb.append("            </div>\n");
            sb.append("            <div class=\"section-body about-text\">\n");
            for (String p : bioParagraphs) {
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
                        ? r.getCustom_title().trim() : r.getName();
                if (repoTitle == null || repoTitle.trim().isEmpty()) {
                    repoTitle = "Repository";
                }
                String desc = r.getDescription() != null ? r.getDescription().trim() : "";
                String link = r.getHtml_url() != null ? r.getHtml_url().trim() : "#";
                sb.append("                <article class=\"project-card\">\n");
                sb.append("                  <div class=\"project-header\">\n");
                sb.append("                    <div class=\"project-name\">").append(escape(repoTitle)).append("</div>\n");
                sb.append("                    <div class=\"project-meta\">GitHub 레포지토리</div>\n");
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

    private String buildProfileImageSrc(String filename) {
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
            /* fall through to URL */
        }
        return escape(appBaseUrl + "/api/portfolio/user-info/image/" + filename);
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

    private static final String PROMPT_TAIL =
            "\n```\n\n"
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
            + "# STEP 4: HTML OUTPUT REQUIREMENTS\n\n"
            + "Generate a single self-contained .html file with these specifications:\n\n"
            + "### Layout & Structure\n"
            + "```\n"
            + "<header>   — 이름, 지망 직무, 한줄 요약, contact icons\n"
            + "<section>  — About Me\n"
            + "<section>  — Tech Stack (tag/badge style)\n"
            + "<section>  — Projects (card layout, top 3)\n"
            + "<section>  — Activities & Experience (timeline style)\n"
            + "<section>  — Achievements (if any)\n"
            + "<footer>   — GitHub, Email, links\n"
            + "```\n\n"
            + "### Design Rules (Classic Resume Style)\n"
            + "- Color palette: White background, dark navy `#1a1a2e` text, accent `#2563eb` (blue)\n"
            + "- Font: `Noto Sans KR` from Google Fonts for Korean, `Inter` for English/numbers\n"
            + "- No animations — static, print-friendly\n"
            + "- Tech stack: Displayed as inline pill/badge tags\n"
            + "- Projects: Card with border-left accent line, showing stack badges, role, and outcome\n"
            + "- Timeline: Left-bordered vertical timeline for activities\n"
            + "- Achievements: Highlighted box with trophy icon 🏆\n"
            + "- Responsive: Mobile-friendly (single column on small screens)\n"
            + "- Print-ready: `@media print` CSS included\n\n"
            + "### Technical Requirements\n"
            + "- All CSS must be inline within `<style>` tag — no external CSS files\n"
            + "- Use Google Fonts CDN only for fonts\n"
            + "- No JavaScript required (static only)\n"
            + "- All icons via Unicode emoji or inline SVG — no icon libraries\n"
            + "- Must render correctly in Chrome, Safari, Firefox\n\n"
            + "---\n\n"
            + "# STEP 5: QUALITY CHECKLIST (Self-verify silently before outputting HTML)\n"
            + "- [ ] Clarification questions were asked and answered before generating\n"
            + "- [ ] Text preview was confirmed by user before generating HTML\n"
            + "- [ ] No invented data (metrics, links, names not in input)\n"
            + "- [ ] All tech stack badges appear in at least one project or course\n"
            + "- [ ] Max 3 projects, ordered by technical weight\n"
            + "- [ ] About Me is ≤ 3 sentences\n"
            + "- [ ] Empty input fields → sections omitted entirely\n"
            + "- [ ] HTML is a single file, fully self-contained\n"
            + "- [ ] Korean text renders correctly with Noto Sans KR\n"
            + "- [ ] Print stylesheet included\n";

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
            + "footer{margin-top:18px;font-size:11px;color:#6b7280;text-align:right;border-top:1px solid rgba(226,232,240,0.9);padding-top:8px;}"
            + "footer a{color:#1d4ed8;text-decoration:none;margin-left:8px;}"
            + "footer a:hover{text-decoration:underline;}"
            + "@media (max-width:768px){.card-inner{flex-direction:column;}.sidebar{width:100%;border-right:none;border-bottom:1px solid rgba(148,163,184,0.25);}.main{padding:20px 18px 22px;}.name{font-size:22px;}.section{margin-bottom:18px;}.project-card{padding:9px 10px 9px 12px;}}"
            + "@media print{body{background-color:#ffffff;}.page{margin:0;padding:0;}.card{box-shadow:none;border-radius:0;border:none;}.sidebar{border-right:1px solid #e5e7eb;}.project-card{box-shadow:none;}a{color:#000000;text-decoration:none;}}";
}
