package com.example.docmgmt.service;

import com.example.docmgmt.domain.Models.Document;
import com.example.docmgmt.domain.Models.DocState;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.GridFsRepository;
import com.example.docmgmt.config.Config;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.Properties;

/**
 * EmailService - Real Gmail IMAP Integration
 * Sử dụng javax.mail để kết nối Gmail thực tế
 */
public class EmailService {
    private final DocumentRepository docRepo;
    private final GridFsRepository gridFsRepo;
    private final String gmailHost = "imap.gmail.com";
    private final String gmailPort = "993";
    private final Config config; // Sử dụng Config chung thay vì tạo mới

    public EmailService(DocumentRepository docRepo, GridFsRepository gridFsRepo, Config config) {
        this.docRepo = docRepo;
        this.gridFsRepo = gridFsRepo;
        this.config = config;
        createProcessedEmailsTable(); // Tạo bảng processed_emails nếu chưa có
    }

    /**
     * Kết nối và lấy email từ Gmail
     */
    public int fetchEmailsFromGmail(String email, String password) {
        int processedCount = 0;
        try {
            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "imaps");
            props.setProperty("mail.imaps.host", gmailHost);
            props.setProperty("mail.imaps.port", gmailPort);
            props.setProperty("mail.imaps.ssl.enable", "true");

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(gmailHost, email, password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Message[] messages = inbox.getMessages();
            System.out.println("Tim thay " + messages.length + " email trong hop thu");
            
            // CHI LAY 10 EMAIL MOI NHAT DE TEST
            int maxEmails = Math.min(10, messages.length);
            System.out.println("Chi xu ly " + maxEmails + " email moi nhat de test");

            for (int i = 0; i < maxEmails; i++) {
                Message message = messages[messages.length - 1 - i]; // Lấy từ cuối (mới nhất)
                if (processEmail(message)) {
                    processedCount++;
                    System.out.println("Da xu ly email " + (i + 1) + "/" + maxEmails);
                }
            }

            inbox.close(false);
            store.close();
            
            System.out.println("Da xu ly " + processedCount + " van ban tu email");

        } catch (Exception e) {
            System.err.println("Loi khi lay email: " + e.getMessage());
            e.printStackTrace();
        }
        
        return processedCount;
    }

    /**
     * Xử lý từng email
     */
    private boolean processEmail(Message message) {
        try {
            String subject = message.getSubject();
            String from = InternetAddress.toString(message.getFrom());
            String messageId = message.getHeader("Message-ID")[0]; // Lấy Message-ID để kiểm tra trùng lặp
            
            System.out.println("Xu ly email: " + subject + " tu " + from);

            // KIEM TRA TRUNG LAP - Kiem tra xem email nay da duoc xu ly chua
            if (isEmailAlreadyProcessed(messageId)) {
                System.out.println("Email da duoc xu ly truoc do: " + messageId);
                return false;
            }

            if (isDocumentEmail(subject, from)) {
                Document doc = createDocumentFromEmail(message);
                if (doc != null) {
                    // Luu Message-ID de tranh trung lap
                    saveProcessedEmailId(messageId, doc.id());
                    docRepo.insert(doc);
                    System.out.println("Da tao van ban: " + doc.title());
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Loi xu ly email: " + e.getMessage());
        }
        return false;
    }

    /**
     * Kiểm tra email có phải là văn bản đến không
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
        
        // Kiểm tra domain gửi từ cơ quan nhà nước
        if (from != null) {
            String lowerFrom = from.toLowerCase();
            String[] govDomains = {
                "gov.vn", "govt.vn", "chinhphu.vn", "moh.gov.vn",
                "mof.gov.vn", "moc.gov.vn", "moet.gov.vn"
            };
            
            for (String domain : govDomains) {
                if (lowerFrom.contains(domain)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Tạo document từ email
     */
    private Document createDocumentFromEmail(Message message) {
        try {
            String subject = message.getSubject();
            String from = InternetAddress.toString(message.getFrom());
            String priority = determinePriority(subject, from);
            String classification = determineClassification(subject);
            String securityLevel = determineSecurityLevel(subject, from);
            
            // Lưu attachments vào GridFS
            String fileId = saveEmailAttachments(message);
            
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
                priority,
                null  // Note
            );
            
            return doc;
        } catch (Exception e) {
            System.err.println("Lỗi tạo document từ email: " + e.getMessage());
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
     * Lưu attachments vào GridFS
     */
    private String saveEmailAttachments(Message message) {
        try {
            Multipart multipart = (Multipart) message.getContent();
            
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                Part part = bodyPart;
                
                if (part.getFileName() != null && !part.getFileName().isEmpty()) {
                    // Lưu attachment
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(
                            bodyPart.getInputStream().readAllBytes())) {
                        return gridFsRepo.saveFile(part.getFileName(), bis);
                    }
                }
            }
            
            // Nếu không có attachment, lưu email body
            String body = message.getContent().toString();
            if (body != null && !body.trim().isEmpty()) {
                String fileName = "email_body_" + System.currentTimeMillis() + ".txt";
                try (ByteArrayInputStream bis = new ByteArrayInputStream(body.getBytes())) {
                    return gridFsRepo.saveFile(fileName, bis);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi lưu attachments: " + e.getMessage());
        }
        return null;
    }

    /**
     * Test connection
     */
    public boolean testConnection(String email, String password) {
        try {
            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "imaps");
            props.setProperty("mail.imaps.host", gmailHost);
            props.setProperty("mail.imaps.port", gmailPort);
            props.setProperty("mail.imaps.ssl.enable", "true");

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(gmailHost, email, password);
            store.close();
            
            System.out.println("Kết nối Gmail thành công cho " + email);
            return true;
        } catch (Exception e) {
            System.err.println("Lỗi kết nối Gmail cho " + email + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Method được gọi từ SwingApp.doEmail()
     */
    public int fetchAndProcessEmails(String email, String password) {
        System.out.println("EmailService.fetchAndProcessEmails called");
        System.out.println("Email: " + email);
        System.out.println("Password: " + (password != null ? "***" : "null"));
        
        // Gọi method hiện có
        return fetchEmailsFromGmail(email, password);
    }

    /**
     * Tạo bảng processed_emails nếu chưa có
     */
    private void createProcessedEmailsTable() {
        try {
            String sql = """
                CREATE TABLE IF NOT EXISTS processed_emails (
                    id BIGSERIAL PRIMARY KEY,
                    message_id VARCHAR(255) UNIQUE NOT NULL,
                    document_id BIGINT NOT NULL,
                    processed_at TIMESTAMP DEFAULT NOW(),
                    created_at TIMESTAMP DEFAULT NOW()
                )
                """;
            
            try (var conn = config.dataSource.getConnection();
                 var stmt = conn.createStatement()) {
                stmt.execute(sql);
                
                // Tạo index
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_processed_emails_message_id ON processed_emails(message_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_processed_emails_document_id ON processed_emails(document_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_processed_emails_processed_at ON processed_emails(processed_at)");
                
                System.out.println("✅ Bang processed_emails da san sang de kiem tra trung lap");
            }
        } catch (Exception e) {
            System.err.println("Loi tao bang processed_emails: " + e.getMessage());
        }
    }

    /**
     * Kiểm tra email đã được xử lý chưa
     */
    private boolean isEmailAlreadyProcessed(String messageId) {
        try {
            String sql = "SELECT COUNT(*) FROM processed_emails WHERE message_id = ?";
            try (var conn = config.dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, messageId);
                try (var rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            System.err.println("Loi kiem tra email da xu ly: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lưu Message-ID của email đã xử lý
     */
    private void saveProcessedEmailId(String messageId, long documentId) {
        try {
            String sql = "INSERT INTO processed_emails (message_id, document_id, processed_at) VALUES (?, ?, NOW())";
            try (var conn = config.dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, messageId);
                stmt.setLong(2, documentId);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Loi luu Message-ID: " + e.getMessage());
        }
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
    }
}