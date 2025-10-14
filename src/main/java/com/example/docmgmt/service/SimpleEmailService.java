package com.example.docmgmt.service;

import com.example.docmgmt.domain.Models.Document;
import com.example.docmgmt.domain.Models.DocState;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.GridFsRepository;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;

/**
 * Simple Email Service - Mock implementation for testing
 * Không sử dụng javax.mail để tránh dependency issues
 */
public class SimpleEmailService {
    private final DocumentRepository docRepo;
    private final GridFsRepository gridFsRepo;

    public SimpleEmailService(DocumentRepository docRepo, GridFsRepository gridFsRepo) {
        this.docRepo = docRepo;
        this.gridFsRepo = gridFsRepo;
    }

    /**
     * Mock fetch emails from Gmail
     */
    public int fetchEmailsFromGmail(String email, String password) {
        System.out.println("Mock fetching emails from " + email + "...");
        
        // Mock email data for testing
        String[] mockSubjects = {
            "Quyết định số 123/2024 về chính sách mới",
            "Thông báo họp hội đồng quản trị", 
            "Báo cáo tài chính quý 1/2024",
            "Công văn số 456/2024 về đào tạo nhân sự",
            "Nghị quyết số 789/2024 về cơ cấu tổ chức"
        };
        
        int processedCount = 0;
        
        for (String subject : mockSubjects) {
            if (isDocumentEmail(subject, email)) {
                Document doc = createDocumentFromEmail(subject, email);
                if (doc != null) {
                    try {
                        docRepo.insert(doc);
                        processedCount++;
                        System.out.println("Created document: " + doc.title());
                    } catch (Exception e) {
                        System.err.println("Error saving document: " + e.getMessage());
                    }
                }
            }
        }
        
        System.out.println("Processed " + processedCount + " mock emails from " + email);
        return processedCount;
    }

    /**
     * Kiểm tra email có phải văn bản không
     */
    private boolean isDocumentEmail(String subject, String from) {
        if (subject == null || subject.trim().isEmpty()) {
            return false;
        }
        
        String lowerSubject = subject.toLowerCase();
        String[] keywords = {
            "văn bản", "công văn", "quyết định", "thông báo", "báo cáo",
            "nghị quyết", "chỉ thị", "tờ trình", "đề án", "kế hoạch",
            "van ban", "cong van", "quyet dinh", "thong bao", "bao cao"
        };
        
        for (String keyword : keywords) {
            if (lowerSubject.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Tạo document từ email
     */
    private Document createDocumentFromEmail(String subject, String from) {
        try {
            String priority = determinePriority(subject, from);
            String classification = determineClassification(subject);
            String securityLevel = determineSecurityLevel(subject, from);
            
            // Lưu mock file vào GridFS
            String fileId = saveMockFile(subject, from);
            
            Document doc = new Document(
                0, // ID sẽ được tạo bởi database
                subject != null ? subject : "Văn bản từ email",
                OffsetDateTime.now(),
                fileId,
                DocState.TIEP_NHAN,
                classification,
                securityLevel,
                null, // Doc number
                null, // Doc year
                null, // Deadline
                null, // Assigned to
                priority
            );
            
            return doc;
        } catch (Exception e) {
            System.err.println("Error creating document from email: " + e.getMessage());
            return null;
        }
    }

    /**
     * Xác định độ ưu tiên
     */
    private String determinePriority(String subject, String from) {
        if (subject == null) return "NORMAL";
        
        String lowerSubject = subject.toLowerCase();
        
        if (lowerSubject.contains("khẩn cấp") || lowerSubject.contains("thượng khẩn") ||
            lowerSubject.contains("hỏa tốc") || lowerSubject.contains("urgent")) {
            return "EMERGENCY";
        }
        
        if (lowerSubject.contains("khẩn") || lowerSubject.contains("gấp")) {
            return "URGENT";
        }
        
        return "NORMAL";
    }

    /**
     * Xác định phân loại
     */
    private String determineClassification(String subject) {
        if (subject == null) return "Khác";
        
        String lowerSubject = subject.toLowerCase();
        
        if (lowerSubject.contains("quyết định")) return "Quyết định";
        if (lowerSubject.contains("thông báo")) return "Thông báo";
        if (lowerSubject.contains("công văn")) return "Công văn";
        if (lowerSubject.contains("báo cáo")) return "Báo cáo";
        if (lowerSubject.contains("nghị quyết")) return "Nghị quyết";
        if (lowerSubject.contains("chỉ thị")) return "Chỉ thị";
        if (lowerSubject.contains("tờ trình")) return "Tờ trình";
        if (lowerSubject.contains("đề án")) return "Đề án";
        if (lowerSubject.contains("kế hoạch")) return "Kế hoạch";
        
        return "Khác";
    }

    /**
     * Xác định độ mật
     */
    private String determineSecurityLevel(String subject, String from) {
        if (subject == null) return "Thường";
        
        String lowerSubject = subject.toLowerCase();
        
        if (lowerSubject.contains("mật") || lowerSubject.contains("bí mật") ||
            lowerSubject.contains("tuyệt mật")) {
            return "Mật";
        }
        
        if (lowerSubject.contains("nội bộ") || lowerSubject.contains("hạn chế")) {
            return "Hạn chế";
        }
        
        return "Thường";
    }

    /**
     * Lưu mock file vào GridFS
     */
    private String saveMockFile(String subject, String from) {
        try {
            String fileName = "email_" + System.currentTimeMillis() + ".txt";
            String content = "Subject: " + subject + "\n" +
                           "From: " + from + "\n" +
                           "Date: " + OffsetDateTime.now() + "\n" +
                           "Content: Mock email content for testing\n" +
                           "This is a simulated email for testing the document management system.";
            
            try (ByteArrayInputStream bis = new ByteArrayInputStream(content.getBytes())) {
                return gridFsRepo.saveFile(fileName, bis);
            }
        } catch (Exception e) {
            System.err.println("Error saving mock file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Test connection
     */
    public boolean testConnection(String email, String password) {
        System.out.println("Testing mock connection to " + email + "...");
        try {
            Thread.sleep(1000); // Simulate connection test
            System.out.println("Mock connection test successful for " + email);
            return true;
        } catch (Exception e) {
            System.err.println("Mock connection test failed for " + email + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Print setup instructions
     */
    public static void printGmailSetupInstructions() {
        System.out.println("=== GMAIL SETUP INSTRUCTIONS ===");
        System.out.println("1. Enable 2-Factor Authentication in Google Account");
        System.out.println("2. Generate App Password:");
        System.out.println("   - Go to Google Account Settings");
        System.out.println("   - Security > 2-Step Verification > App passwords");
        System.out.println("   - Select 'Mail' and 'Other'");
        System.out.println("   - Enter app name: 'Document Management'");
        System.out.println("   - Copy the 16-character password");
        System.out.println("3. Enable IMAP in Gmail Settings");
        System.out.println("4. Use the App Password (not your regular password)");
        System.out.println("=================================");
    }
}
