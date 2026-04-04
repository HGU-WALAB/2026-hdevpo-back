package com.csee.swplus.mileage.portfolio.repository;

import com.csee.swplus.mileage.portfolio.entity.PortfolioGithubRepoCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioGithubRepoCacheRepository extends JpaRepository<PortfolioGithubRepoCache, Long> {

    List<PortfolioGithubRepoCache> findByPortfolio_Id(Long portfolioId);

    Optional<PortfolioGithubRepoCache> findByPortfolio_IdAndRepoId(Long portfolioId, Long repoId);

    void deleteByPortfolio_Id(Long portfolioId);
}
