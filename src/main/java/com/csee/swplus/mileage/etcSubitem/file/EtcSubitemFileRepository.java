package com.csee.swplus.mileage.etcSubitem.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EtcSubitemFileRepository extends JpaRepository<EtcSubitemFile, Integer> {
    List<EtcSubitemFile> findByRecordId(int recordId);
    List<EtcSubitemFile> findByFilename(String filename);
    EtcSubitemFile findById(int id);
    void deleteByRecordId(int recordId);
}
