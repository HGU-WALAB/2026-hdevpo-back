package com.csee.swplus.mileage.portfolio.repository;

import com.csee.swplus.mileage.portfolio.entity.PortfolioRepoEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepoEntryRepository extends JpaRepository<PortfolioRepoEntry, Long> {

    List<PortfolioRepoEntry> findByPortfolio_IdOrderByDisplayOrderAsc(Long portfolioId);

    Optional<PortfolioRepoEntry> findByIdAndPortfolio_Id(Long id, Long portfolioId);

    Optional<PortfolioRepoEntry> findByPortfolio_IdAndRepoId(Long portfolioId, Long repoId);

    void deleteByPortfolio_Id(Long portfolioId);
}
