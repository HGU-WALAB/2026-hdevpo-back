package com.csee.swplus.mileage.portfolio.repository;

import com.csee.swplus.mileage.portfolio.entity.PortfolioActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioActivityRepository extends JpaRepository<PortfolioActivity, Long> {

    List<PortfolioActivity> findByPortfolio_IdOrderByDisplayOrderAscStartDateDesc(Long portfolioId);

    List<PortfolioActivity> findByPortfolio_IdAndCategoryInOrderByDisplayOrderAscStartDateDesc(Long portfolioId, List<Integer> categories);

    Optional<PortfolioActivity> findByIdAndPortfolio_Id(Long id, Long portfolioId);
}
