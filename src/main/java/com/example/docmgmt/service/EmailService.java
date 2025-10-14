package com.example.docmgmt.service;

import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.GridFsRepository;

import jakarta.mail.*;
import jakarta.mail.Flags.Flag;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Properties;

/**
 * EmailService (IMAP thật): đọc thư Unread, tải file đính kèm, lưu GridFS và tạo văn bản.
 * An toàn: nếu set DM_EMAIL_SUBJECT_PREFIX, chỉ nhận thư có tiêu đề chứa prefix này.
 */
public class EmailService {
    private final DocumentRepository docRepo;
    private final GridFsRepository gridFsRepo;

    public EmailService(DocumentRepository docRepo, GridFsRepository gridFsRepo) {
        this.docRepo = docRepo;
        this.gridFsRepo = gridFsRepo;
    }

    public int fetchEmailsFromGmail(String email, String appPassword) {
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", "imap.gmail.com");
            props.put("mail.imaps.port", "993");
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.connectiontimeout", "5000");  // 5 giây
            props.put("mail.imaps.timeout", "10000");           // 10 giây
            props.put("mail.imaps.readtimeout", "10000");       // 10 giây

            Session session = Session.getInstance(props);
            try (Store store = session.getStore("imaps")) {
                // Test connection với timeout ngắn
                store.connect("imap.gmail.com", email, appPassword);
                
                // Chỉ test kết nối, không đọc email
                if (!store.isConnected()) {
                    throw new RuntimeException("Không thể kết nối đến Gmail IMAP");
                }
                
                return 0; // Chỉ test kết nối, không xử lý email
            }
        } catch (AuthenticationFailedException ex) {
            throw new RuntimeException("Đăng nhập thất bại: kiểm tra App Password hoặc 2FA", ex);
        } catch (Exception ex) {
            throw new RuntimeException("Lỗi kết nối: " + ex.getMessage(), ex);
        }
    }

    public int fetchAndProcessEmails(String email, String appPassword) {
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", "imap.gmail.com");
            props.put("mail.imaps.port", "993");
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.connectiontimeout", "10000");
            props.put("mail.imaps.timeout", "20000");

            Session session = Session.getInstance(props);
            try (Store store = session.getStore("imaps")) {
                store.connect("imap.gmail.com", email, appPassword);
                try (Folder inbox = store.getFolder("INBOX")) {
                    inbox.open(Folder.READ_WRITE);

                    // Unread filter
                    SearchTerm unread = new FlagTerm(new Flags(Flag.SEEN), false);
                    String prefix = System.getenv("DM_EMAIL_SUBJECT_PREFIX");
                    SearchTerm term = unread;
                    if (prefix != null && !prefix.isBlank()) {
                        term = new AndTerm(unread, new SubjectTerm(prefix));
                    }

                    Message[] messages = inbox.search(term);
                    int processed = 0;
                    for (Message m : messages) {
                        String subject = safeSubject(m);
                        String fileId = saveFirstAttachment(m);
                        if (fileId == null) {
                            // Không có file đính kèm → bỏ qua nhưng đánh dấu đã đọc để không lặp
                            m.setFlag(Flag.SEEN, true);
                            continue;
                        }

                        // Tạo văn bản ở trạng thái mặc định (TIEP_NHAN)
                        docRepo.insert(subject != null ? subject : "Văn bản từ email", fileId);
                        processed++;

                        // Đánh dấu đã đọc
                        m.setFlag(Flag.SEEN, true);
                    }
                    return processed;
                }
            }
        } catch (AuthenticationFailedException ex) {
            throw new RuntimeException("Đăng nhập thất bại: kiểm tra App Password hoặc 2FA", ex);
        } catch (Exception ex) {
            throw new RuntimeException("Lỗi nhận email: " + ex.getMessage(), ex);
        }
    }

    private String safeSubject(Message m) {
        try { return m.getSubject(); } catch (Exception ignore) { return null; }
    }

    private String saveFirstAttachment(Message m) {
        try {
            if (m.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) m.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    String fileName = bp.getFileName();
                    boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition()) || fileName != null;
                    if (isAttachment) {
                        try (InputStream is = bp.getInputStream()) {
                            return gridFsRepo.saveFile(fileName != null ? fileName : ("email_" + OffsetDateTime.now() + ".bin"), is);
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}