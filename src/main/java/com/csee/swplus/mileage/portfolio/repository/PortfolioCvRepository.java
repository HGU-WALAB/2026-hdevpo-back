package com.csee.swplus.mileage.portfolio.repository;

import com.csee.swplus.mileage.portfolio.entity.PortfolioCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioCvRepository extends JpaRepository<PortfolioCv, Long> {

    List<PortfolioCv> findByUser_IdOrderByRegdateDesc(Long userId);

    Optional<PortfolioCv> findByIdAndUser_Id(Long id, Long userId);

    Optional<PortfolioCv> findByPublicToken(String publicToken);
}
