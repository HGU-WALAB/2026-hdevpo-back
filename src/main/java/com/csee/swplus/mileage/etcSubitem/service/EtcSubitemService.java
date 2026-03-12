package com.csee.swplus.mileage.etcSubitem.service;

import com.csee.swplus.mileage.etcSubitem.domain.EtcSubitem;
import com.csee.swplus.mileage.etcSubitem.file.EtcSubitemFile;
import com.csee.swplus.mileage.etcSubitem.file.EtcSubitemFileRepository;
import com.csee.swplus.mileage.etcSubitem.file.EtcSubitemFileService;
import com.csee.swplus.mileage.etcSubitem.dto.StudentInputSubitemResponseDto;
import com.csee.swplus.mileage.etcSubitem.dto.EtcSubitemResponseDto;
import com.csee.swplus.mileage.etcSubitem.mapper.EtcSubitemMapper;
import com.csee.swplus.mileage.etcSubitem.repository.EtcSubitemRepository;
import com.csee.swplus.mileage.setting.service.ManagerService;
import com.csee.swplus.mileage.util.message.dto.MessageResponseDto;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor // not null 또는 final 인 필드를 받는 생성자
@Slf4j
public class EtcSubitemService {
    private final EtcSubitemMapper etcSubitemMapper;
    private final EtcSubitemRepository etcSubitemRepository;
    private final EtcSubitemFileRepository fileRepository;
    private final EtcSubitemFileService fileService;
    private final ManagerService managerService;

    /**
     * Returns the file metadata if the current user is authorized to download it.
     * Verifies file exists in DB and belongs to a record owned by the user.
     */
    public java.util.Optional<EtcSubitemFile> getFileIfAuthorized(String filename, String currentUserId) {
        if (filename == null || filename.isEmpty() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return java.util.Optional.empty();
        }
        List<EtcSubitemFile> files = fileRepository.findByFilename(filename);
        for (EtcSubitemFile f : files) {
            EtcSubitem record = etcSubitemRepository.findById(f.getRecordId()).orElse(null);
            if (record != null && currentUserId.equals(record.getSnum())) {
                return java.util.Optional.of(f);
            }
        }
        return java.util.Optional.empty();
    }

    public List<StudentInputSubitemResponseDto> getStudentInputSubitems() {
        String currentSemester = managerService.getCurrentSemester();
        log.info("📝 getCurrentSemester 결과 - current semester: " + currentSemester);
        List<StudentInputSubitemResponseDto> res = etcSubitemMapper.findAllStudentInputSubitems(currentSemester);
        log.info("📝 findAllStudentInputSubitems 결과 - res: {}", res);
        return res;
    }

    public List<EtcSubitemResponseDto> getEtcSubitems(String studentId) {
        String currentSemester = managerService.getCurrentSemester();
        log.info("📝 getCurrentSemester 결과 - current semester: " + currentSemester);
        List<EtcSubitemResponseDto> res = etcSubitemMapper.findAllEtcSubitems(studentId, currentSemester);
        log.info("📝 getRequestedEtcSubitems 결과 - res: {}", res);
        return res;
    }

    @Transactional
    public MessageResponseDto postEtcSubitem(String studentId, String semester, String description1, String description2, int subitemId, MultipartFile file) {
        try {
//            1. EtcSubitem 엔티티 생성 및 저장
            EtcSubitem etcSubitem = new EtcSubitem();

//            앞단에서 전달 받는 값
            etcSubitem.setSemester(semester);
            etcSubitem.setSubitemId(subitemId);
            etcSubitem.setDescription1(description1);
            etcSubitem.setDescription2(description2);

            String snum = studentId;
            etcSubitem.setSnum(snum);

//           고정적인 값
            etcSubitem.setCategoryId(240);
            etcSubitem.setValue(1);
            etcSubitem.setExtraPoint(0);
            etcSubitem.setModdate(LocalDateTime.now());
            etcSubitem.setRegdate(LocalDateTime.now());

//            db 로부터 가져오는 값
            String sname = etcSubitemMapper.getSname(studentId);
            etcSubitem.setSname(sname);

//            마일리지 포인트
            int mPoint = etcSubitemMapper.getMPoint(subitemId);
            etcSubitem.setMPoint(mPoint);

            EtcSubitem savedEtcSubitem = etcSubitemRepository.save(etcSubitem);

//            2. 파일 처리 및 저장
            if (file != null && !file.isEmpty()) {
//                실제 파일 저장
                String savedFileName = fileService.saveFile(file);
                log.info("savedFileName: {}", savedFileName);

//                파일 관련 정보 DB에 저장
                EtcSubitemFile newFile = new EtcSubitemFile();
                newFile.setRecordId(savedEtcSubitem.getId());
                newFile.setOriginalFilename(file.getOriginalFilename());
                newFile.setFilename(savedFileName);
//                filesize DB에서 varchar 타입임
                newFile.setFilesize(fileService.formatFileSize(file.getSize()));
                newFile.setSemester(semester);

                newFile.setRegdate(LocalDateTime.now());

                fileRepository.save(newFile);
            }

//            3. 성공 메세지 반환
            return new MessageResponseDto("기타 항목이 등록되었습니다.");
        } catch (Exception e) {
            log.error("⚠️ 기타 항목 등록 중 오류 발생: ", e);
            throw new RuntimeException("기타 항목 등록 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public MessageResponseDto patchEtcSubitem(String studentId, int recordId, String description1, String description2, int subitemId, MultipartFile file) {
        try {
//            1. 기존 항목 조회
            EtcSubitem etcSubitem = etcSubitemRepository.findById(recordId)
                    .orElseThrow(() -> new RuntimeException("해당 항목을 찾을 수 없습니다."));

//            2. 항목 정보 업데이트
            etcSubitem.setDescription1(description1);
            etcSubitem.setDescription2(description2);
            etcSubitem.setModdate(LocalDateTime.now());
            etcSubitemRepository.save(etcSubitem);

//            3. 파일 업데이트
            if (file != null && !file.isEmpty()) {
                // 기존 파일 삭제
                List<EtcSubitemFile> existingFiles = fileRepository.findByRecordId(recordId);
                for (EtcSubitemFile existingFile : existingFiles) {
                    fileService.deleteFile(existingFile.getFilename());
                }
                fileRepository.deleteByRecordId(recordId);

                // 새 파일 저장
                String savedFileName = fileService.saveFile(file);
                EtcSubitemFile newFile = new EtcSubitemFile();
                newFile.setRecordId(recordId);
                newFile.setOriginalFilename(file.getOriginalFilename());
                newFile.setFilename(savedFileName);
                newFile.setFilesize(fileService.formatFileSize(file.getSize()));
                newFile.setSemester(etcSubitem.getSemester());

                fileRepository.save(newFile);
            }

            return new MessageResponseDto("기타 항목이 수정되었습니다.");
        } catch (Exception e) {
            log.error("⚠️ 기타 항목 수정 중 오류 발생: ", e);
            throw new RuntimeException("기타 항목 수정 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public MessageResponseDto deleteEtcSubitem(String studentId, int recordId) {
        try {
//            1. 파일 삭제
            List<EtcSubitemFile> files = fileRepository.findByRecordId(recordId);
            for (EtcSubitemFile file : files) {
                fileService.deleteFile(file.getFilename());
            }
            fileRepository.deleteByRecordId(recordId);

//            2. 항목 삭제
            etcSubitemRepository.deleteById(recordId);

            return new MessageResponseDto("기타 항목이 삭제되었습니다.");
        } catch(Exception e) {
            log.error("⚠️ 기타 항목 삭제 중 오류 발생: ", e);
            throw new RuntimeException("기타 항목 삭제 중 오류가 발생했습니다.");
        }
    }
}