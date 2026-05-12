package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.auth.exception.DoNotExistException;
import com.csee.swplus.mileage.portfolio.dto.*;
import com.csee.swplus.mileage.portfolio.entity.PortfolioCv;
import com.csee.swplus.mileage.portfolio.repository.PortfolioCvRepository;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD and prompt building for Portfolio CV (이력서).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PortfolioCvService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter CV_TITLE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int CV_TITLE_MAX_LEN = 255;
    private static final int PUBLIC_TOKEN_DIGITS = 10;
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final int CV_LIST_JOB_POSTING_MAX = 280;
    private static final int CV_LIST_NOTES_MAX = 200;

    private final PortfolioCvRepository cvRepository;
    private final PortfolioHtmlExportService htmlExportService;
    private final OpenAiClient openAiClient;

    /**
     * Builds the CV prompt and creates a CV record with empty html.
     * Returns prompt + cv_id. Frontend uses PATCH /cv/{id} to submit html_content
     * in the next step.
     */
    public CvBuildPromptResponse buildPrompt(Users user, CvBuildPromptRequest request) {
        CvPromptMode mode = CvPromptMode.fromRequest(request.getMode());
        String prompt = mode == CvPromptMode.CV
                ? htmlExportService.buildCvPrompt(user, request)
                : htmlExportService.buildArchivePrompt(user, request);
        String title = resolveDefaultCvTitle(request);
        PortfolioCv cv = null;
        for (int attempt = 0; attempt < 32; attempt++) {
            try {
                cv = PortfolioCv.builder()
                        .user(user)
                        .title(title)
                        .jobPosting(request.getJob_posting())
                        .targetPosition(request.getTarget_position())
                        .additionalNotes(request.getAdditional_notes())
                        .designPreferences(normalizeDesignPreferences(request.getDesign_preferences()))
                        .selectedRepoIds(copyOrEmpty(request.getSelected_repo_ids()))
                        .selectedMileageIds(copyOrEmpty(request.getSelected_mileage_ids()))
                        .selectedActivityIds(copyOrEmpty(request.getSelected_activity_ids()))
                        .mode(mode.getValue())
                        .prompt(prompt)
                        .htmlContent("")
                        .publicToken(generateNumericPublicToken())
                        .isPublic(false)
                        .build();
                cv = cvRepository.save(cv);
                break;
            } catch (DataIntegrityViolationException e) {
                if (attempt == 31) {
                    throw e;
                }
            }
        }
        return CvBuildPromptResponse.builder()
                .prompt(prompt)
                .cv_id(cv.getId())
                .public_token(cv.getPublicToken())
                .build();
    }

    /**
     * Lists all CVs for the user, ordered by creation date descending.
     */
    public CvListResponse list(Users user) {
        List<PortfolioCv> list = cvRepository.findByUser_IdAndIsDeletedFalseOrderByRegdateDesc(user.getId());
        for (PortfolioCv cv : list) {
            ensurePublicToken(cv);
        }
        List<CvListItem> items = list.stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        return CvListResponse.builder().cvs(items).build();
    }

    /**
     * Uses explicit {@code title} when non-blank; otherwise
     * "{@code target_position} · yyyy-MM-dd"
     * (Asia/Seoul), or "새 이력서 · yyyy-MM-dd" when 지원 직무도 비어 있음. Truncates to DB
     * column length.
     */
    private static String resolveDefaultCvTitle(CvBuildPromptRequest request) {
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            return truncateTitle(request.getTitle().trim());
        }
        String dateStr = LocalDate.now(SEOUL).format(CV_TITLE_DATE);
        String position = request.getTarget_position() != null ? request.getTarget_position().trim() : "";
        String base = position.isEmpty() ? "새 이력서 · " + dateStr : position + " · " + dateStr;
        return truncateTitle(base);
    }

    private static String truncateTitle(String s) {
        if (s.length() <= CV_TITLE_MAX_LEN) {
            return s;
        }
        return s.substring(0, CV_TITLE_MAX_LEN);
    }

    /**
     * Gets a single CV by ID. User must own the CV.
     */
    public CvResponse get(Users user, Long id) {
        PortfolioCv cv = cvRepository.findByIdAndUser_IdAndIsDeletedFalse(id, user.getId())
                .orElseThrow(() -> new DoNotExistException("해당 이력서를 찾을 수 없습니다."));
        ensurePublicToken(cv);
        return toResponse(cv);
    }

    /**
     * Rebuilds the prompt for an existing CV (using its stored selections + job info + a new
     * {@code design_preferences}), then calls OpenAI to render the HTML, and persists everything.
     * Used by the FE "AI Customize" flow ({@code POST /api/portfolio/cv/{id}/generate-html}).
     * <p>
     * Two-phase save semantics: the regenerated prompt is saved <em>before</em> the OpenAI call, so if
     * OpenAI fails (timeout, rate limit, etc.) the user's design intent is not lost — the FE can show
     * the prompt and offer a "재시도" button while still showing the previous HTML.
     * <p>
     * If the CV was created before {@code selected_*_ids} columns existed (legacy: {@code null}),
     * regeneration falls back to "no selections" — the FE should re-call {@code POST /build-prompt}.
     *
     * @throws com.csee.swplus.mileage.portfolio.exception.OpenAiNotConfiguredException 503 when no API key
     * @throws com.csee.swplus.mileage.portfolio.exception.OpenAiTimeoutException        504 when OpenAI hangs
     * @throws com.csee.swplus.mileage.portfolio.exception.OpenAiException               502 for other OpenAI errors
     */
    public CvResponse generateHtml(Users user, Long id, CvRegeneratePromptRequest request) {
        PortfolioCv cv = cvRepository.findByIdAndUser_IdAndIsDeletedFalse(id, user.getId())
                .orElseThrow(() -> new DoNotExistException("해당 이력서를 찾을 수 없습니다."));
        ensurePublicToken(cv);

        DesignPreferencesDto newPrefs = request != null
                ? normalizeDesignPreferences(request.getDesign_preferences())
                : null;
        String modelOverride = request != null ? trimToNull(request.getModel()) : null;

        CvBuildPromptRequest synthetic = new CvBuildPromptRequest();
        synthetic.setMode(cv.getMode());
        synthetic.setJob_posting(cv.getJobPosting());
        synthetic.setTarget_position(cv.getTargetPosition());
        synthetic.setAdditional_notes(cv.getAdditionalNotes());
        synthetic.setTitle(cv.getTitle());
        synthetic.setSelected_repo_ids(copyOrEmpty(cv.getSelectedRepoIds()));
        synthetic.setSelected_mileage_ids(copyOrEmpty(cv.getSelectedMileageIds()));
        synthetic.setSelected_activity_ids(copyOrEmpty(cv.getSelectedActivityIds()));
        synthetic.setDesign_preferences(newPrefs);

        CvPromptMode mode = CvPromptMode.fromRequest(cv.getMode());
        String prompt = mode == CvPromptMode.CV
                ? htmlExportService.buildCvPrompt(user, synthetic)
                : htmlExportService.buildArchivePrompt(user, synthetic);

        cv.setPrompt(prompt);
        cv.setDesignPreferences(newPrefs);
        cvRepository.save(cv);

        OpenAiClient.Result result = openAiClient.generateHtml(prompt, modelOverride);
        cv.setHtmlContent(result.getHtml());
        cv.setModelUsed(result.getModel());
        cv.setTokensUsed(result.getTotalTokens());
        cv.setLastGeneratedAt(LocalDateTime.now());
        cvRepository.save(cv);

        return toResponse(cv);
    }

    /**
     * Updates title and/or html_content only. User must own the CV.
     */
    public CvResponse patch(Users user, Long id, CvPatchRequest request) {
        PortfolioCv cv = cvRepository.findByIdAndUser_IdAndIsDeletedFalse(id, user.getId())
                .orElseThrow(() -> new DoNotExistException("해당 이력서를 찾을 수 없습니다."));
        ensurePublicToken(cv);
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            cv.setTitle(request.getTitle().trim());
        }
        if (request.getHtml_content() != null) {
            cv.setHtmlContent(request.getHtml_content());
        }
        if (request.getIs_public() != null) {
            cv.setPublic(Boolean.TRUE.equals(request.getIs_public()));
        }
        if (request.getPrompt() != null) {
            cv.setPrompt(request.getPrompt());
        }
        if (request.getDesign_preferences() != null) {
            cv.setDesignPreferences(normalizeDesignPreferences(request.getDesign_preferences()));
        }
        cvRepository.save(cv);
        return toResponse(cv);
    }

    /**
     * Soft-deletes a CV. User must own the CV.
     */
    public void delete(Users user, Long id) {
        PortfolioCv cv = cvRepository.findByIdAndUser_IdAndIsDeletedFalse(id, user.getId())
                .orElseThrow(() -> new DoNotExistException("해당 이력서를 찾을 수 없습니다."));
        cv.setDeleted(true);
        cv.setDeletedAt(LocalDateTime.now());
        cvRepository.save(cv);
    }

    /**
     * Restores a previously deleted CV. User must own the CV.
     */
    public CvResponse restore(Users user, Long id) {
        PortfolioCv cv = cvRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new DoNotExistException("해당 이력서를 찾을 수 없습니다."));
        cv.setDeleted(false);
        cv.setDeletedAt(null);
        ensurePublicToken(cv);
        cvRepository.save(cv);
        return toResponse(cv);
    }

    /**
     * Resolves public CV HTML by token. After lookup: {@code is_public == false} → {@link CvPublicHtmlResult.Kind#PRIVATE};
     * {@code is_public == true} and blank {@code html_content} → {@link CvPublicHtmlResult.Kind#PUBLIC_EMPTY} (204);
     * {@code is_public == true} with content → {@link CvPublicHtmlResult.Kind#OK}. Invalid/unknown token → NOT_FOUND / INVALID_TOKEN.
     */
    @Transactional(readOnly = true)
    public CvPublicHtmlResult resolvePublicCvHtml(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (!token.matches("\\d{8,12}")) {
            return CvPublicHtmlResult.invalidToken();
        }
        return cvRepository.findByPublicTokenAndIsDeletedFalse(token)
                .map(cv -> {
                    if (!cv.isPublic()) {
                        return CvPublicHtmlResult.privateCv();
                    }
                    String html = cv.getHtmlContent();
                    if (html == null || html.trim().isEmpty()) {
                        return CvPublicHtmlResult.publicEmpty();
                    }
                    return CvPublicHtmlResult.ok(html);
                })
                .orElse(CvPublicHtmlResult.notFound());
    }

    private void ensurePublicToken(PortfolioCv cv) {
        if (cv.getPublicToken() != null && !cv.getPublicToken().isEmpty()) {
            return;
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            try {
                cv.setPublicToken(generateNumericPublicToken());
                cvRepository.save(cv);
                return;
            } catch (DataIntegrityViolationException e) {
                if (attempt == 31) {
                    throw e;
                }
            }
        }
    }

    /** Ten-digit numeric string, first digit 1–9 (stable length for URLs). */
    private static String generateNumericPublicToken() {
        StringBuilder sb = new StringBuilder(PUBLIC_TOKEN_DIGITS);
        sb.append(1 + TOKEN_RANDOM.nextInt(9));
        for (int i = 1; i < PUBLIC_TOKEN_DIGITS; i++) {
            sb.append(TOKEN_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private CvResponse toResponse(PortfolioCv cv) {
        return CvResponse.builder()
                .id(cv.getId())
                .title(cv.getTitle())
                .job_posting(cv.getJobPosting())
                .target_position(cv.getTargetPosition())
                .additional_notes(cv.getAdditionalNotes())
                .design_preferences(cv.getDesignPreferences())
                .selected_repo_ids(cv.getSelectedRepoIds())
                .selected_mileage_ids(cv.getSelectedMileageIds())
                .selected_activity_ids(cv.getSelectedActivityIds())
                .mode(cv.getMode() != null ? cv.getMode() : CvPromptMode.CV.getValue())
                .prompt(cv.getPrompt())
                .html_content(cv.getHtmlContent())
                .model_used(cv.getModelUsed())
                .tokens_used(cv.getTokensUsed())
                .last_generated_at(cv.getLastGeneratedAt())
                .public_token(cv.getPublicToken())
                .is_public(cv.isPublic())
                .created_at(cv.getRegdate())
                .updated_at(cv.getModdate())
                .build();
    }

    private CvListItem toListItem(PortfolioCv cv) {
        return CvListItem.builder()
                .id(cv.getId())
                .title(cv.getTitle())
                .job_posting(ellipsis(cv.getJobPosting(), CV_LIST_JOB_POSTING_MAX))
                .target_position(cv.getTargetPosition())
                .additional_notes(ellipsis(cv.getAdditionalNotes(), CV_LIST_NOTES_MAX))
                .design_preferences(cv.getDesignPreferences())
                .mode(cv.getMode() != null ? cv.getMode() : CvPromptMode.CV.getValue())
                .public_token(cv.getPublicToken())
                .is_public(cv.isPublic())
                .created_at(cv.getRegdate())
                .updated_at(cv.getModdate())
                .build();
    }

    /**
     * Trim values and drop the whole DTO when every sub-field is blank, so the DB stays {@code null}
     * (instead of storing an "all-blank" JSON object).
     */
    private static DesignPreferencesDto normalizeDesignPreferences(DesignPreferencesDto in) {
        if (in == null) {
            return null;
        }
        String layout = trimToNull(in.getLayout());
        String color = trimToNull(in.getColor_theme());
        String density = trimToNull(in.getDensity());
        String notes = trimToNull(in.getAdditional_notes());
        if (layout == null && color == null && density == null && notes == null) {
            return null;
        }
        return DesignPreferencesDto.builder()
                .layout(layout)
                .color_theme(color)
                .density(density)
                .additional_notes(notes)
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Defensive copy. Returns an empty list (never null) so prompt builders can iterate safely. */
    private static List<Long> copyOrEmpty(List<Long> in) {
        if (in == null || in.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(in);
    }

    private static String ellipsis(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() <= maxLen) {
            return t;
        }
        if (maxLen <= 3) {
            return t.substring(0, Math.max(0, maxLen));
        }
        return t.substring(0, maxLen - 3) + "...";
    }
}
