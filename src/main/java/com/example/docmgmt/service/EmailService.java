package com.example.docmgmt.service;

// import com.example.docmgmt.domain.Models.Document;
// import com.example.docmgmt.domain.Models.DocState;
// import com.example.docmgmt.repo.DocumentRepository;
// import com.example.docmgmt.repo.GridFsRepository;

// import java.io.ByteArrayInputStream;
// import java.time.OffsetDateTime;

/**
 * EmailService - DISABLED DUE TO JAVAX.MAIL DEPENDENCY ISSUES
 * 
 * This service is disabled because javax.mail dependencies are not properly resolved.
 * Please use SimpleEmailService instead for testing and development.
 */
public class EmailService {
    @SuppressWarnings("unused")
    private final Object docRepo;
    @SuppressWarnings("unused")
    private final Object gridFsRepo;
    @SuppressWarnings("unused")
    private final String gmailHost = "imap.gmail.com";
    @SuppressWarnings("unused")
    private final String gmailPort = "993";

    public EmailService(Object docRepo, Object gridFsRepo) {
        this.docRepo = docRepo;
        this.gridFsRepo = gridFsRepo;
    }

    /**
     * Kết nối và lấy email từ Gmail - DISABLED
     */
    public int fetchEmailsFromGmail(String email, String password) {
        System.out.println("EmailService.fetchEmailsFromGmail is disabled due to javax.mail dependency issues");
        System.out.println("Please use SimpleEmailService instead for testing");
        return 0;
    }

    /**
     * Xử lý từng email - DISABLED
     */
    @SuppressWarnings("unused")
    private boolean processEmail(Object message) {
        System.out.println("EmailService.processEmail is disabled due to javax.mail dependency issues");
        return false;
    }

    /**
     * Kiểm tra email có phải là văn bản đến không - DISABLED
     */
    @SuppressWarnings("unused")
    private boolean isDocumentEmail(String subject, String from) {
        System.out.println("EmailService.isDocumentEmail is disabled due to javax.mail dependency issues");
        return false;
    }

    /**
     * Tạo document từ email - DISABLED
     */
    @SuppressWarnings("unused")
    private Object createDocumentFromEmail(Object message) {
        System.out.println("EmailService.createDocumentFromEmail is disabled due to javax.mail dependency issues");
        return null;
    }

    /**
     * Xác định độ ưu tiên - DISABLED
     */
    @SuppressWarnings("unused")
    private String determinePriority(String subject, String from) {
        System.out.println("EmailService.determinePriority is disabled due to javax.mail dependency issues");
        return "NORMAL";
    }

    /**
     * Xác định phân loại - DISABLED
     */
    @SuppressWarnings("unused")
    private String determineClassification(String subject) {
        System.out.println("EmailService.determineClassification is disabled due to javax.mail dependency issues");
        return "Khác";
    }

    /**
     * Xác định độ mật - DISABLED
     */
    @SuppressWarnings("unused")
    private String determineSecurityLevel(String subject, String from) {
        System.out.println("EmailService.determineSecurityLevel is disabled due to javax.mail dependency issues");
        return "Thường";
    }

    /**
     * Lưu attachments vào GridFS - DISABLED
     */
    @SuppressWarnings("unused")
    private String saveEmailAttachments(Object message) {
        System.out.println("EmailService.saveEmailAttachments is disabled due to javax.mail dependency issues");
        return null;
    }

    /**
     * In hướng dẫn cấu hình Gmail
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
        System.out.println("NOTE: This service is currently disabled due to dependency issues.");
        System.out.println("Please use SimpleEmailService for testing.");
    }
}