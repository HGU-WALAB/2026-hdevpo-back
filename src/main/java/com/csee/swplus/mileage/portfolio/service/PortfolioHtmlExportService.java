package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.portfolio.dto.*;
import com.csee.swplus.mileage.profile.entity.Profile;
import com.csee.swplus.mileage.profile.repository.ProfileRepository;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
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
        sb.append("- 한줄 자기소개: ").append(nullToEmpty(userInfo.getBio())).append("\n\n");

        sb.append("[tech_stack]\n");
        if (techStack.getTech_stack() != null) {
            for (TechStackItem t : techStack.getTech_stack()) {
                String line = t.getName() != null ? t.getName() : "";
                if (t.getDomain() != null && !t.getDomain().isEmpty()) line += " (" + t.getDomain() + ")";
                if (t.getLevel() != null) line += " " + t.getLevel() + "%";
                sb.append("- ").append(line).append("\n");
            }
        }
        sb.append("\n");

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

    private String nullToEmpty(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Prefer languages list; fall back to single language. */
    private java.util.List<RepoLanguageDto> getRepoLanguagesForDisplay(RepoEntryResponse r) {
        if (r.getLanguages() != null && !r.getLanguages().isEmpty()) {
            return r.getLanguages();
        }
        if (r.getLanguage() != null && !r.getLanguage().isEmpty()) {
            return java.util.Collections.singletonList(
                    RepoLanguageDto.builder().name(r.getLanguage()).percentage(null).build());
        }
        return java.util.Collections.emptyList();
    }

    /** Format languages for plain text (e.g. "Java (65.2%), Python (24.1%)" or "Java, Python"). */
    private String formatRepoLanguages(RepoEntryResponse r) {
        java.util.List<RepoLanguageDto> langList = getRepoLanguagesForDisplay(r);
        return langList.stream()
                .map(l -> l.getPercentage() != null
                        ? l.getName() + " (" + l.getPercentage() + "%)"
                        : l.getName())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String buildHtml(UserInfoResponse userInfo, TechStackResponse techStack,
            List<RepoEntryResponse> repos, List<MileageEntryResponse> mileageList,
            List<ActivityResponse> activities, String githubUrl, String email) {

        String name = escape(userInfo.getName());
        String schoolDept = schoolDept(userInfo);
        String bio = escape(trimToParagraph(userInfo.getBio()));
        String profileImgSrc = buildProfileImageSrc(userInfo.getProfile_image_url());

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        sb.append("  <title>").append(name).append(" - Portfolio</title>\n");
        sb.append("  <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&family=Noto+Sans+KR:wght@400;500;700&display=swap\" rel=\"stylesheet\">\n");
        sb.append("  <style>\n");
        sb.append(CSS);
        sb.append("  </style>\n</head>\n<body>\n");

        // Header
        sb.append("  <header class=\"header\">\n");
        if (profileImgSrc != null) {
            sb.append("    <img src=\"").append(profileImgSrc).append("\" alt=\"Profile\" class=\"profile-img\">\n");
        }
        sb.append("    <h1>").append(name).append("</h1>\n");
        if (schoolDept != null && !schoolDept.isEmpty()) {
            sb.append("    <p class=\"subtitle\">").append(schoolDept).append("</p>\n");
        }
        sb.append("    <div class=\"contact-icons\">");
        if (githubUrl != null) sb.append(" <a href=\"").append(escape(githubUrl)).append("\" target=\"_blank\" rel=\"noopener\">GitHub</a>");
        if (email != null && !email.isEmpty()) sb.append(" <a href=\"mailto:").append(escape(email)).append("\">Email</a>");
        sb.append("</div>\n  </header>\n");

        // About Me
        if (bio != null && !bio.isEmpty()) {
            sb.append("  <section class=\"section\"><h2>About Me</h2><p>").append(bio).append("</p></section>\n");
        }

        // Tech Stack
        if (techStack.getTech_stack() != null && !techStack.getTech_stack().isEmpty()) {
            sb.append("  <section class=\"section\"><h2>Tech Stack</h2><div class=\"tech-tags\">");
            for (TechStackItem t : techStack.getTech_stack()) {
                String techName = t.getName() != null ? t.getName() : "";
                String title = techName;
                if (t.getDomain() != null && !t.getDomain().isEmpty()) title += " · " + t.getDomain();
                if (t.getLevel() != null) title += " · " + t.getLevel() + "%";
                sb.append("<span class=\"tech-tag\" title=\"").append(escape(title)).append("\">")
                  .append(escape(techName));
                if (t.getLevel() != null) sb.append(" <small>(").append(t.getLevel()).append("%)</small>");
                sb.append("</span>");
            }
            sb.append("</div></section>\n");
        }

        // Projects (visible repos, max 3)
        List<RepoEntryResponse> projectRepos = repos != null ? repos.stream().limit(3).collect(Collectors.toList()) : List.of();
        if (!projectRepos.isEmpty()) {
            sb.append("  <section class=\"section\"><h2>Projects</h2>");
            for (RepoEntryResponse r : projectRepos) {
                String title = r.getCustom_title() != null && !r.getCustom_title().isEmpty() ? r.getCustom_title() : r.getName();
                if (title == null) title = "Repository";
                String desc = r.getDescription() != null ? r.getDescription() : "";
                java.util.List<RepoLanguageDto> langList = getRepoLanguagesForDisplay(r);
                String link = r.getHtml_url() != null ? r.getHtml_url() : "#";
                sb.append("<div class=\"project-card\">");
                sb.append("<h3><a href=\"").append(escape(link)).append("\" target=\"_blank\" rel=\"noopener\">").append(escape(title)).append("</a></h3>");
                if (!desc.isEmpty()) sb.append("<p>").append(escape(desc)).append("</p>");
                sb.append("<div class=\"tech-tags\">");
                for (RepoLanguageDto lang : langList) {
                    if (lang != null && lang.getName() != null && !lang.getName().isEmpty()) {
                        String display = lang.getPercentage() != null
                                ? lang.getName() + " (" + lang.getPercentage().intValue() + "%)"
                                : lang.getName();
                        sb.append("<span class=\"tech-tag\">").append(escape(display)).append("</span>");
                    }
                }
                if (r.getCommit_count() != null) {
                    sb.append("<span class=\"tech-tag commit-count\">").append(r.getCommit_count()).append(" commits</span>");
                }
                if (r.getStargazers_count() != null) {
                    sb.append("<span class=\"tech-tag star-count\">").append(r.getStargazers_count()).append(" ★</span>");
                }
                if (r.getForks_count() != null) {
                    sb.append("<span class=\"tech-tag fork-count\">").append(r.getForks_count()).append(" forks</span>");
                }
                sb.append("</div></div>");
            }
            sb.append("</section>\n");
        }

        // Mileage (교과/비교과)
        if (mileageList != null && !mileageList.isEmpty()) {
            sb.append("  <section class=\"section\"><h2>마일리지</h2>");
            for (MileageEntryResponse m : mileageList) {
                String sem = m.getSemester() != null ? m.getSemester() : "";
                String cat = m.getCategoryName() != null ? m.getCategoryName() : "";
                String sub = m.getSubitemName() != null ? m.getSubitemName() : "";
                String d1 = m.getDescription1() != null ? m.getDescription1() : "";
                String add = m.getAdditional_info() != null ? m.getAdditional_info() : "";
                String line = sem + " " + cat + " - " + sub + " · " + (add.isEmpty() ? d1 : add);
                sb.append("<div class=\"mileage-item\">").append(escape(line.trim())).append("</div>");
            }
            sb.append("</section>\n");
        }

        // Activities
        if (activities != null && !activities.isEmpty()) {
            sb.append("  <section class=\"section\"><h2>Activities & Experience</h2><div class=\"timeline\">");
            for (ActivityResponse a : activities) {
                String title = a.getTitle() != null ? a.getTitle() : "";
                String desc = a.getDescription() != null ? a.getDescription() : "";
                String start = a.getStart_date() != null ? a.getStart_date().toString() : "";
                String end = a.getEnd_date() != null ? a.getEnd_date().toString() : "";
                String range = start + " ~ " + end;
                sb.append("<div class=\"timeline-item\">");
                sb.append("<h3>").append(escape(title)).append("</h3>");
                sb.append("<span class=\"date\">").append(escape(range)).append("</span>");
                if (!desc.isEmpty()) sb.append("<p>").append(escape(desc)).append("</p>");
                sb.append("</div>");
            }
            sb.append("</div></section>\n");
        }

        // Footer
        sb.append("  <footer class=\"footer\">");
        if (githubUrl != null) sb.append(" <a href=\"").append(escape(githubUrl)).append("\" target=\"_blank\" rel=\"noopener\">GitHub</a>");
        if (email != null && !email.isEmpty()) sb.append(" <a href=\"mailto:").append(escape(email)).append("\">").append(escape(email)).append("</a>");
        sb.append("</footer>\n</body>\n</html>");

        return sb.toString();
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

    /**
     * Builds profile image src: base64 data URL if file exists (self-contained), else URL.
     */
    private String buildProfileImageSrc(String filename) {
        if (filename == null || filename.isEmpty()) return null;
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

    private String trimToParagraph(String s) {
        if (s == null) return "";
        String t = s.trim();
        int first = t.indexOf("\n");
        return first > 0 ? t.substring(0, first) : t;
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
            "* { box-sizing: border-box; margin: 0; padding: 0; } "
            + "body { font-family: 'Noto Sans KR', 'Inter', sans-serif; background: #fff; color: #1a1a2e; line-height: 1.6; max-width: 800px; margin: 0 auto; padding: 24px; } "
            + ".header { text-align: center; padding: 24px 0; border-bottom: 2px solid #2563eb; } "
            + ".profile-img { width: 100px; height: 100px; border-radius: 50%; object-fit: cover; margin-bottom: 12px; } "
            + ".header h1 { font-size: 1.75rem; font-weight: 700; color: #1a1a2e; } "
            + ".subtitle { color: #64748b; font-size: 0.95rem; margin-top: 4px; } "
            + ".contact-icons { margin-top: 8px; } "
            + ".contact-icons a { color: #2563eb; text-decoration: none; margin: 0 8px; font-size: 0.9rem; } "
            + ".section { margin: 24px 0; } "
            + ".section h2 { font-size: 1.25rem; color: #1a1a2e; border-left: 4px solid #2563eb; padding-left: 12px; margin-bottom: 12px; } "
            + ".tech-tags { display: flex; flex-wrap: wrap; gap: 8px; } "
            + ".tech-tag { background: #e0e7ff; color: #3730a3; padding: 4px 12px; border-radius: 999px; font-size: 0.85rem; } "
            + ".project-card { border-left: 4px solid #2563eb; padding: 12px 0 12px 16px; margin-bottom: 12px; } "
            + ".project-card h3 { font-size: 1rem; } "
            + ".project-card h3 a { color: #2563eb; text-decoration: none; } "
            + ".project-card p { font-size: 0.9rem; color: #475569; margin: 4px 0; } "
            + ".mileage-item { padding: 6px 0; font-size: 0.9rem; } "
            + ".timeline { border-left: 2px solid #e2e8f0; padding-left: 16px; } "
            + ".timeline-item { margin-bottom: 16px; } "
            + ".timeline-item h3 { font-size: 1rem; } "
            + ".timeline-item .date { font-size: 0.85rem; color: #64748b; } "
            + ".timeline-item p { font-size: 0.9rem; margin-top: 4px; } "
            + ".footer { text-align: center; padding: 24px 0; border-top: 1px solid #e2e8f0; font-size: 0.9rem; } "
            + ".footer a { color: #2563eb; text-decoration: none; margin: 0 8px; } "
            + "@media print { body { padding: 16px; } .header, .section { break-inside: avoid; } } ";
}
