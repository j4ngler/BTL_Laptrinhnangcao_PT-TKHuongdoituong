package com.example.docmgmt.service;

import com.example.docmgmt.domain.Models.Document;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.PendingEmailRepository;
import com.example.docmgmt.repo.GridFsRepository;

import java.sql.SQLException;
import java.util.List;

/**
 * Service xử lý email chờ xác nhận
 */
public class PendingEmailService {
    private final PendingEmailRepository pendingEmailRepo;
    private final DocumentRepository docRepo;
    private final EmailService emailService;
    private final GridFsRepository gridFsRepo;

    public PendingEmailService(PendingEmailRepository pendingEmailRepo, 
                               DocumentRepository docRepo,
                               EmailService emailService,
                               GridFsRepository gridFsRepo) {
        this.pendingEmailRepo = pendingEmailRepo;
        this.docRepo = docRepo;
        this.emailService = emailService;
        this.gridFsRepo = gridFsRepo;
    }

    /**
     * Lấy danh sách email chờ xác nhận
     */
    public List<PendingEmailRepository.PendingEmail> listPending() throws SQLException {
        return pendingEmailRepo.listPending();
    }

    /**
     * Lấy chi tiết một email chờ xác nhận
     */
    public PendingEmailRepository.PendingEmail getById(long id) throws SQLException {
        return pendingEmailRepo.getById(id);
    }

    /**
     * Xác nhận email và tạo document với phân loại do người dùng chọn
     */
    public Document approveEmail(long pendingEmailId, String reviewedBy, 
                                 String classification, String securityLevel, String priority) throws SQLException {
        PendingEmailRepository.PendingEmail pending = pendingEmailRepo.getById(pendingEmailId);
        if (pending == null) {
            throw new IllegalArgumentException("Không tìm thấy email chờ xác nhận với id=" + pendingEmailId);
        }
        
        if (!"PENDING".equals(pending.status())) {
            throw new IllegalStateException("Email này đã được xử lý rồi: " + pending.status());
        }
        
        // Tạo document từ pending email với thông tin phân loại từ người dùng
        Document doc = emailService.createDocumentFromPendingEmail(pending, classification, securityLevel, priority);
        if (doc == null) {
            throw new RuntimeException("Không thể tạo document từ email");
        }
        
        // Lưu document vào database
        Document savedDoc = docRepo.insert(doc);
        
        // Cập nhật trạng thái pending email
        pendingEmailRepo.approve(pendingEmailId, savedDoc.id(), reviewedBy);
        
        // Lưu Message-ID vào processed_emails để tránh trùng lặp
        emailService.saveProcessedEmailId(pending.messageId(), savedDoc.id());
        
        return savedDoc;
    }

    /**
     * Từ chối email (không tạo document)
     */
    public void rejectEmail(long pendingEmailId, String reviewedBy, String note) throws SQLException {
        PendingEmailRepository.PendingEmail pending = pendingEmailRepo.getById(pendingEmailId);
        if (pending == null) {
            throw new IllegalArgumentException("Không tìm thấy email chờ xác nhận với id=" + pendingEmailId);
        }
        
        if (!"PENDING".equals(pending.status())) {
            throw new IllegalStateException("Email này đã được xử lý rồi: " + pending.status());
        }
        
        pendingEmailRepo.reject(pendingEmailId, reviewedBy, note);
    }
    
    /**
     * Đọc nội dung file attachment
     */
    public String readAttachmentContent(String fileId) {
        try {
            return gridFsRepo.readFileAsString(fileId);
        } catch (Exception e) {
            return "Không thể đọc file: " + e.getMessage();
        }
    }
}

