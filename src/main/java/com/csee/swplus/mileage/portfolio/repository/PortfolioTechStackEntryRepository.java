package com.csee.swplus.mileage.portfolio.repository;

import com.csee.swplus.mileage.portfolio.entity.PortfolioTechStackEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioTechStackEntryRepository extends JpaRepository<PortfolioTechStackEntry, Integer> {
}
