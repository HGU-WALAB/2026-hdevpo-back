package com.csee.swplus.mileage.portfolio.repository;

import com.csee.swplus.mileage.portfolio.entity.PortfolioMileageEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioMileageEntryRepository extends JpaRepository<PortfolioMileageEntry, Long> {

    List<PortfolioMileageEntry> findByPortfolio_IdOrderByDisplayOrderAsc(Long portfolioId);

    Optional<PortfolioMileageEntry> findByIdAndPortfolio_Id(Long id, Long portfolioId);

    boolean existsByPortfolio_IdAndMileageId(Long portfolioId, Long mileageId);
}
