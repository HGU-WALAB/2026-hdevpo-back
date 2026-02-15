package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.portfolio.dto.UserInfoResponse;
import com.csee.swplus.mileage.portfolio.entity.Portfolio;
import com.csee.swplus.mileage.portfolio.repository.PortfolioRepository;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

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
}
