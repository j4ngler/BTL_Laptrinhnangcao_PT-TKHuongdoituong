package com.example.docmgmt.service;

import com.example.docmgmt.domain.Models.DocState;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.UserRepository;
import com.example.docmgmt.domain.Models.Role;

import java.sql.SQLException;

public final class WorkflowService {
    private final DocumentRepository repo;
    private final UserRepository userRepo;

    public WorkflowService(DocumentRepository repo) {
        this.repo = repo;
        this.userRepo = null;
    }

    public WorkflowService(DocumentRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    public void submit(long id, String actor, String note) throws SQLException {
        // Only from DRAFT
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.DRAFT) throw new IllegalStateException("Chỉ chuyển SUBMITTED từ DRAFT");
        ensureRole(actor, Role.CREATOR);
        repo.updateState(id, DocState.SUBMITTED);
        repo.addAudit(id, "SUBMIT", actor, note);
    }

    public void classify(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.SUBMITTED) throw new IllegalStateException("Chỉ phân loại sau khi SUBMITTED");
        ensureRole(actor, Role.CLASSIFIER);
        // note format: <classification>|<security>|<free-note>
        String classification = null, security = null, free = note;
        if (note != null && !note.isBlank()) {
            String[] parts = note.split("\\|", 3);
            if (parts.length >= 1) classification = parts[0];
            if (parts.length >= 2) security = parts[1];
            if (parts.length == 3) free = parts[2];
        }
        repo.setClassification(id, classification, security);
        repo.updateState(id, DocState.CLASSIFIED);
        repo.addAudit(id, "CLASSIFY", actor, free);
    }

    public void approve(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.CLASSIFIED) throw new IllegalStateException("Chỉ duyệt sau khi CLASSIFIED");
        ensureRole(actor, Role.APPROVER);
        repo.updateState(id, DocState.APPROVED);
        repo.addAudit(id, "APPROVE", actor, note);
    }

    public void issue(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.APPROVED) throw new IllegalStateException("Chỉ ban hành sau khi APPROVED");
        ensureRole(actor, Role.PUBLISHER);
        int year = java.time.OffsetDateTime.now().getYear();
        int number = repo.nextDocNumberForYear(year);
        repo.assignIssueNumber(id, number, year);
        repo.updateState(id, DocState.ISSUED);
        repo.addAudit(id, "ISSUE", actor, note);
    }

    public void archive(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.ISSUED) throw new IllegalStateException("Chỉ lưu trữ sau khi ISSUED");
        ensureRole(actor, Role.ARCHIVER);
        repo.updateState(id, DocState.ARCHIVED);
        repo.addAudit(id, "ARCHIVE", actor, note);
    }

    private void ensureRole(String username, Role required) throws SQLException {
        if (userRepo == null) return; // backward compatible
        var u = userRepo.getByUsername(username);
        if (u == null) throw new IllegalArgumentException("Người dùng không tồn tại: " + username);
        
        var roles = userRepo.getRolesByUsername(username);
        if (!roles.contains(required)) {
            throw new SecurityException("Người dùng " + username + " không có vai trò " + required);
        }
    }
}

