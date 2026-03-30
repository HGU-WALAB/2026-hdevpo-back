package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.auth.exception.DoNotExistException;
import com.csee.swplus.mileage.portfolio.dto.*;
import com.csee.swplus.mileage.portfolio.entity.PortfolioCv;
import com.csee.swplus.mileage.portfolio.repository.PortfolioCvRepository;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private final PortfolioCvRepository cvRepository;
    private final PortfolioHtmlExportService htmlExportService;

    /**
     * Builds the CV prompt and creates a CV record with empty html.
     * Returns prompt + cv_id. Frontend uses PATCH /cv/{id} to submit html_content in the next step.
     */
    public CvBuildPromptResponse buildPrompt(Users user, CvBuildPromptRequest request) {
        String prompt = htmlExportService.buildCvPrompt(user, request);
        String title = resolveDefaultCvTitle(request);
        PortfolioCv cv = PortfolioCv.builder()
                .user(user)
                .title(title)
                .jobPosting(request.getJob_posting())
                .targetPosition(request.getTarget_position())
                .additionalNotes(request.getAdditional_notes())
                .prompt(prompt)
                .htmlContent("")
                .build();
        cv = cvRepository.save(cv);
        return CvBuildPromptResponse.builder()
                .prompt(prompt)
                .cv_id(cv.getId())
                .build();
    }

    /**
     * Lists all CVs for the user, ordered by creation date descending.
     */
    public CvListResponse list(Users user) {
        List<PortfolioCv> list = cvRepository.findByUser_IdOrderByRegdateDesc(user.getId());
        List<CvListItem> items = list.stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        return CvListResponse.builder().cvs(items).build();
    }

    /**
     * Uses explicit {@code title} when non-blank; otherwise "{@code target_position} · yyyy-MM-dd"
     * (Asia/Seoul), or "새 이력서 · yyyy-MM-dd" when 지원 직무도 비어 있음. Truncates to DB column length.
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
        PortfolioCv cv = cvRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new DoNotExistException("해당 이력서를 찾을 수 없습니다."));
        return toResponse(cv);
    }

    /**
     * Updates title and/or html_content only. User must own the CV.
     */
    public CvResponse patch(Users user, Long id, CvPatchRequest request) {
        PortfolioCv cv = cvRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new DoNotExistException("해당 이력서를 찾을 수 없습니다."));
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            cv.setTitle(request.getTitle().trim());
        }
        if (request.getHtml_content() != null) {
            cv.setHtmlContent(request.getHtml_content());
        }
        cvRepository.save(cv);
        return toResponse(cv);
    }

    /**
     * Deletes a CV. User must own the CV.
     */
    public void delete(Users user, Long id) {
        PortfolioCv cv = cvRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new DoNotExistException("해당 이력서를 찾을 수 없습니다."));
        cvRepository.delete(cv);
    }

    private CvResponse toResponse(PortfolioCv cv) {
        return CvResponse.builder()
                .id(cv.getId())
                .title(cv.getTitle())
                .job_posting(cv.getJobPosting())
                .target_position(cv.getTargetPosition())
                .additional_notes(cv.getAdditionalNotes())
                .prompt(cv.getPrompt())
                .html_content(cv.getHtmlContent())
                .created_at(cv.getRegdate())
                .updated_at(cv.getModdate())
                .build();
    }

    private CvListItem toListItem(PortfolioCv cv) {
        return CvListItem.builder()
                .id(cv.getId())
                .title(cv.getTitle())
                .job_posting(cv.getJobPosting())
                .target_position(cv.getTargetPosition())
                .additional_notes(cv.getAdditionalNotes())
                .created_at(cv.getRegdate())
                .updated_at(cv.getModdate())
                .build();
    }
}
