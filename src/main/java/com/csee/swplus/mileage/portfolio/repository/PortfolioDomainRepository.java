package com.csee.swplus.mileage.portfolio.repository;

import com.csee.swplus.mileage.portfolio.entity.PortfolioDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioDomainRepository extends JpaRepository<PortfolioDomain, Integer> {

    List<PortfolioDomain> findBySnumOrderByOrderIndexAscIdAsc(String snum);

    void deleteBySnum(String snum);
}
