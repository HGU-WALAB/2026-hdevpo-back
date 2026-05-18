package com.csee.swplus.mileage.portfolio.repository;

import com.csee.swplus.mileage.portfolio.entity.PortfolioCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioCvRepository extends JpaRepository<PortfolioCv, Long> {

    List<PortfolioCv> findByUser_IdAndIsDeletedFalseOrderByRegdateDesc(Long userId);

    List<PortfolioCv> findByUser_IdAndIsDeletedFalseOrderByIsFavoriteDescRegdateDesc(Long userId);

    Optional<PortfolioCv> findByIdAndUser_IdAndIsDeletedFalse(Long id, Long userId);

    /** Includes deleted rows; used for restore flows. */
    Optional<PortfolioCv> findByIdAndUser_Id(Long id, Long userId);

    Optional<PortfolioCv> findByPublicTokenAndIsDeletedFalse(String publicToken);
}
