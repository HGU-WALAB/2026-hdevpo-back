package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.portfolio.dto.ActivityRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityResponse;
import com.csee.swplus.mileage.portfolio.dto.ActivitiesResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageLinkRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageListResponse;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.RepositoriesResponse;
import com.csee.swplus.mileage.portfolio.dto.SettingsResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackResponse;
import com.csee.swplus.mileage.portfolio.dto.UserInfoResponse;
import com.csee.swplus.mileage.portfolio.entity.Portfolio;
import com.csee.swplus.mileage.portfolio.entity.PortfolioActivity;
import com.csee.swplus.mileage.portfolio.entity.PortfolioMileageEntry;
import com.csee.swplus.mileage.portfolio.entity.PortfolioRepoEntry;
import com.csee.swplus.mileage.portfolio.repository.PortfolioActivityRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioMileageEntryRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioRepoEntryRepository;
import com.csee.swplus.mileage.user.entity.Users;
import com.csee.swplus.mileage.auth.exception.DoNotExistException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioRepoEntryRepository portfolioRepoEntryRepository;
    private final PortfolioActivityRepository portfolioActivityRepository;
    private final PortfolioMileageEntryRepository portfolioMileageEntryRepository;

    /**
     * Returns the portfolio for the user, creating one if it does not exist.
     */
    public Portfolio getOrCreatePortfolio(Users user) {
        return portfolioRepository
                .findByUser_Id(user.getId())
                .orElseGet(() -> portfolioRepository.save(
                        Portfolio.builder()
                                .user(user)
                                .build()));
    }

    /**
     * GET /api/portfolio/user-info – 기본 정보 (학교 정보 + bio).
     */
    public UserInfoResponse getUserInfo(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        return UserInfoResponse.builder()
                .name(user.getName())
                .department(user.getDepartment())
                .major1(user.getMajor1())
                .major2(user.getMajor2())
                .grade(user.getGrade())
                .semester(user.getSemester())
                .bio(portfolio.getBio())
                .build();
    }

    /**
     * PATCH /api/portfolio/user-info – 소개글(bio)만 수정.
     */
    public UserInfoResponse updateBio(Users user, String bio) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        portfolio.setBio(bio);
        portfolioRepository.save(portfolio);
        return getUserInfo(user);
    }

    /**
     * GET /api/portfolio/tech-stack – 기술 스택 목록.
     */
    public TechStackResponse getTechStack(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        return TechStackResponse.builder()
                .tech_stack(portfolio.getTechStack() != null ? portfolio.getTechStack() : java.util.Collections.emptyList())
                .build();
    }

    /**
     * PUT /api/portfolio/tech-stack – 기술 스택 전체 교체 (batch sync).
     */
    public TechStackResponse putTechStack(Users user, java.util.List<String> techStack) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        portfolio.setTechStack(techStack != null ? techStack : java.util.Collections.emptyList());
        portfolioRepository.save(portfolio);
        return getTechStack(user);
    }

    /**
     * GET /api/portfolio/repositories – 노출 설정 + 커스텀 제목 목록.
     */
    public RepositoriesResponse getRepositories(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<PortfolioRepoEntry> entries = portfolioRepoEntryRepository.findByPortfolio_IdOrderByDisplayOrderAsc(portfolio.getId());
        java.util.List<RepoEntryResponse> list = new java.util.ArrayList<>();
        for (PortfolioRepoEntry e : entries) {
            list.add(RepoEntryResponse.builder()
                    .id(e.getId())
                    .repo_id(e.getRepoId())
                    .custom_title(e.getCustomTitle())
                    .is_visible(e.getIsVisible())
                    .display_order(e.getDisplayOrder())
                    .build());
        }
        return RepositoriesResponse.builder().repositories(list).build();
    }

    /**
     * PUT /api/portfolio/repositories – 전체 목록 교체 (batch sync).
     */
    public RepositoriesResponse putRepositories(Users user, java.util.List<RepoEntryRequest> requests) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        portfolioRepoEntryRepository.deleteByPortfolio_Id(portfolio.getId());
        if (requests != null) {
            for (int i = 0; i < requests.size(); i++) {
                RepoEntryRequest r = requests.get(i);
                Boolean visible = r.getIs_visible() != null ? r.getIs_visible() : true;
                portfolioRepoEntryRepository.save(PortfolioRepoEntry.builder()
                        .portfolio(portfolio)
                        .repoId(r.getRepo_id())
                        .customTitle(r.getCustom_title())
                        .isVisible(visible)
                        .displayOrder(i)
                        .build());
            }
        }
        return getRepositories(user);
    }

    /**
     * GET /api/portfolio/activities – 활동 목록.
     */
    public ActivitiesResponse getActivities(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<PortfolioActivity> list = portfolioActivityRepository.findByPortfolio_IdOrderByDisplayOrderAscStartDateDesc(portfolio.getId());
        java.util.List<ActivityResponse> responses = new java.util.ArrayList<>();
        for (PortfolioActivity a : list) {
            responses.add(toActivityResponse(a));
        }
        return ActivitiesResponse.builder().activities(responses).build();
    }

    /**
     * POST /api/portfolio/activities – 활동 추가 (반환된 id로 이후 PUT 가능).
     */
    public ActivityResponse createActivity(Users user, ActivityRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        int nextOrder = portfolioActivityRepository.findByPortfolio_IdOrderByDisplayOrderAscStartDateDesc(portfolio.getId()).size();
        PortfolioActivity activity = PortfolioActivity.builder()
                .portfolio(portfolio)
                .title(request.getTitle())
                .description(request.getDescription())
                .startDate(request.getStart_date())
                .endDate(request.getEnd_date())
                .displayOrder(nextOrder)
                .build();
        activity = portfolioActivityRepository.save(activity);
        return toActivityResponse(activity);
    }

    /**
     * PUT /api/portfolio/activities/{id} – 활동 수정.
     */
    public ActivityResponse updateActivity(Users user, Long id, ActivityRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioActivity activity = portfolioActivityRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 활동을 찾을 수 없습니다."));
        activity.setTitle(request.getTitle());
        activity.setDescription(request.getDescription());
        activity.setStartDate(request.getStart_date());
        activity.setEndDate(request.getEnd_date());
        portfolioActivityRepository.save(activity);
        return toActivityResponse(activity);
    }

    /**
     * DELETE /api/portfolio/activities/{id} – 활동 삭제.
     */
    public void deleteActivity(Users user, Long id) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioActivity activity = portfolioActivityRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 활동을 찾을 수 없습니다."));
        portfolioActivityRepository.delete(activity);
    }

    private static ActivityResponse toActivityResponse(PortfolioActivity a) {
        return ActivityResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .description(a.getDescription())
                .start_date(a.getStartDate())
                .end_date(a.getEndDate())
                .display_order(a.getDisplayOrder())
                .build();
    }

    /**
     * GET /api/portfolio/mileage – 연결된 마일리지 목록.
     */
    public MileageListResponse getMileageList(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<PortfolioMileageEntry> list = portfolioMileageEntryRepository.findByPortfolio_IdOrderByDisplayOrderAsc(portfolio.getId());
        java.util.List<MileageEntryResponse> responses = new java.util.ArrayList<>();
        for (PortfolioMileageEntry e : list) {
            responses.add(toMileageEntryResponse(e));
        }
        return MileageListResponse.builder().mileage(responses).build();
    }

    /**
     * POST /api/portfolio/mileage – 기존 마일리지 연결 (원본 마일리지는 삭제하지 않음).
     */
    public MileageEntryResponse linkMileage(Users user, MileageLinkRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        if (portfolioMileageEntryRepository.existsByPortfolio_IdAndMileageId(portfolio.getId(), request.getMileage_id())) {
            throw new IllegalArgumentException("이미 연결된 마일리지입니다.");
        }
        int nextOrder = portfolioMileageEntryRepository.findByPortfolio_IdOrderByDisplayOrderAsc(portfolio.getId()).size();
        PortfolioMileageEntry entry = PortfolioMileageEntry.builder()
                .portfolio(portfolio)
                .mileageId(request.getMileage_id())
                .additionalInfo(request.getAdditional_info())
                .displayOrder(nextOrder)
                .build();
        entry = portfolioMileageEntryRepository.save(entry);
        return toMileageEntryResponse(entry);
    }

    /**
     * PUT /api/portfolio/mileage/{id} – 추가 설명(additional_info)만 수정 (id = portfolio_mileage link id).
     */
    public MileageEntryResponse updateMileageEntry(Users user, Long id, String additionalInfo) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioMileageEntry entry = portfolioMileageEntryRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 마일리지 연결을 찾을 수 없습니다."));
        entry.setAdditionalInfo(additionalInfo);
        portfolioMileageEntryRepository.save(entry);
        return toMileageEntryResponse(entry);
    }

    /**
     * DELETE /api/portfolio/mileage/{id} – 연결 해제 (원본 마일리지 기록은 삭제하지 않음).
     */
    public void unlinkMileage(Users user, Long id) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioMileageEntry entry = portfolioMileageEntryRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 마일리지 연결을 찾을 수 없습니다."));
        portfolioMileageEntryRepository.delete(entry);
    }

    private static MileageEntryResponse toMileageEntryResponse(PortfolioMileageEntry e) {
        return MileageEntryResponse.builder()
                .id(e.getId())
                .mileage_id(e.getMileageId())
                .additional_info(e.getAdditionalInfo())
                .display_order(e.getDisplayOrder())
                .build();
    }

    private static final java.util.List<String> DEFAULT_SECTION_ORDER = java.util.Arrays.asList("tech", "repo", "activities", "mileage");

    /**
     * GET /api/portfolio/settings – 섹션 순서 (유저 정보는 프론트에서 상단 고정).
     */
    public SettingsResponse getSettings(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<String> order = portfolio.getSectionOrder();
        if (order == null || order.isEmpty()) {
            order = DEFAULT_SECTION_ORDER;
        }
        return SettingsResponse.builder().section_order(order).build();
    }

    /**
     * PUT /api/portfolio/settings – 섹션 순서 변경.
     */
    public SettingsResponse putSettings(Users user, java.util.List<String> sectionOrder) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        portfolio.setSectionOrder(sectionOrder != null && !sectionOrder.isEmpty() ? sectionOrder : DEFAULT_SECTION_ORDER);
        portfolioRepository.save(portfolio);
        return getSettings(user);
    }
}
