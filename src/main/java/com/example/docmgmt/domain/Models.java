package com.example.docmgmt.domain;

import java.time.OffsetDateTime;

public final class Models {
    public enum Role { 
        // 3 vai trò chính theo quy trình văn bản đến
        VAN_THU,        // Văn thư - Tiếp nhận và đăng ký văn bản
        LANH_DAO,       // Lãnh đạo - Xem xét, chỉ đạo và phê duyệt
        CAN_BO_CHUYEN_MON  // Cán bộ chuyên môn - Thực hiện xử lý văn bản
    }
    public enum DocState { 
        // Quy trình văn bản đến
        TIEP_NHAN,      // Tiếp nhận - Văn thư nhận văn bản từ email/bưu chính
        DANG_KY,        // Đăng ký - Văn thư đăng ký vào hệ thống
        CHO_XEM_XET,    // Chờ xem xét - Trình lãnh đạo xem xét
        DA_PHAN_CONG,   // Đã phân công - Lãnh đạo đã chỉ đạo xử lý
        DANG_XU_LY,     // Đang xử lý - Cán bộ chuyên môn đang thực hiện
        HOAN_THANH      // Hoàn thành - Đã xử lý xong và báo cáo
    }
    public enum Priority { NORMAL, URGENT, EMERGENCY, FIRE } // Thường, Khẩn, Thượng khẩn, Hỏa tốc

    public record Document(long id, String title, OffsetDateTime createdAt, String latestFileId, DocState state, 
                          String classification, String securityLevel, Integer docNumber, Integer docYear,
                          OffsetDateTime deadline, String assignedTo, String priority) {}

    public record DocumentVersion(long id, long documentId, String fileId, int versionNo, OffsetDateTime createdAt) {}

    public record AuditLog(long id, long documentId, String action, String actor, OffsetDateTime at, String note) {}

    public record User(long id, String username, String passwordHash, Role role) {}
}

