package com.csee.swplus.mileage.setting.repository;

import com.csee.swplus.mileage.setting.entity.Manager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ManagerRepository extends JpaRepository<Manager, Long> {
    Optional<Manager> findById(Long id);

    /**
     * Fetches only mypage_announcement so /announcement does not depend on
     * other columns (contact_info, reg_start type, etc.). Returns empty if
     * column or row is missing.
     */
    @Query(value = "SELECT mypage_announcement FROM _sw_manager_setting WHERE id = :id", nativeQuery = true)
    Optional<String> findMypageAnnouncementById(@Param("id") long id);

    /**
     * Fetches only contact_info so /contact does not depend on full entity.
     * Returns empty if column or row is missing (table may not have contact_info
     * yet).
     */
    @Query(value = "SELECT contact_info FROM _sw_manager_setting WHERE id = :id", nativeQuery = true)
    Optional<String> findContactInfoById(@Param("id") long id);

    /**
     * Fetches maintenance_mode as a boolean so /maintenance does not depend on full entity
     * or JDBC tinyint mapping quirks. 1 -> true, other/null -> false.
     */
    @Query(
        value = "SELECT CASE WHEN maintenance_mode = 1 THEN TRUE ELSE FALSE END " +
                "FROM _sw_manager_setting WHERE id = :id",
        nativeQuery = true
    )
    Optional<Boolean> findMaintenanceModeById(@Param("id") long id);

    /**
     * Maintenance message text (nullable).
     */
    @Query(value = "SELECT maintenance_message FROM _sw_manager_setting WHERE id = :id", nativeQuery = true)
    Optional<String> findMaintenanceMessageById(@Param("id") long id);

    /**
     * Maintenance estimated time text (nullable).
     */
    @Query(value = "SELECT maintenance_eta FROM _sw_manager_setting WHERE id = :id", nativeQuery = true)
    Optional<String> findMaintenanceEtaById(@Param("id") long id);

    /**
     * Comma-separated list of allowed user IDs during maintenance (nullable).
     */
    @Query(value = "SELECT maintenance_allowed_ids FROM _sw_manager_setting WHERE id = :id", nativeQuery = true)
    Optional<String> findMaintenanceAllowedIdsById(@Param("id") long id);
}
