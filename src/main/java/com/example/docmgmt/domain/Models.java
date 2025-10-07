package com.example.docmgmt.domain;

import java.time.OffsetDateTime;

public final class Models {
    public enum Role { CREATOR, CLASSIFIER, APPROVER, PUBLISHER, ARCHIVER }
    public enum DocState { DRAFT, SUBMITTED, CLASSIFIED, APPROVED, ISSUED, ARCHIVED }

    public record Document(long id, String title, OffsetDateTime createdAt, String latestFileId, DocState state, 
                          String classification, String securityLevel, Integer docNumber, Integer docYear) {}

    public record DocumentVersion(long id, long documentId, String fileId, int versionNo, OffsetDateTime createdAt) {}

    public record AuditLog(long id, long documentId, String action, String actor, OffsetDateTime at, String note) {}

    public record User(long id, String username, Role role) {}
}

