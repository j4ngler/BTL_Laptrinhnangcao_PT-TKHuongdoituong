package com.example.docmgmt.repo;

import com.example.docmgmt.domain.Models.Document;
import com.example.docmgmt.domain.Models.DocState;
import com.example.docmgmt.domain.Models.AuditLog;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class DocumentRepository {
    private final DataSource ds;

    public DocumentRepository(DataSource ds) {
        this.ds = ds;
    }

    public DataSource getDataSource() {
        return ds;
    }

    public void migrate() throws SQLException {
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS documents (\n" +
                    "id BIGSERIAL PRIMARY KEY,\n" +
                    "title TEXT NOT NULL,\n" +
                    "created_at TIMESTAMPTZ NOT NULL DEFAULT now(),\n" +
                    "latest_file_id TEXT,\n" +
                    "state TEXT NOT NULL DEFAULT 'TIEP_NHAN',\n" +
                    "classification TEXT,\n" +
                    "security_level TEXT,\n" +
                    "doc_number INT,\n" +
                    "doc_year INT,\n" +
                    "deadline TIMESTAMPTZ,\n" +
                    "assigned_to TEXT,\n" +
                    "priority TEXT DEFAULT 'NORMAL'\n" +
                    ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS document_versions (\n" +
                    "id BIGSERIAL PRIMARY KEY,\n" +
                    "document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,\n" +
                    "file_id TEXT NOT NULL,\n" +
                    "version_no INT NOT NULL,\n" +
                    "created_at TIMESTAMPTZ NOT NULL DEFAULT now()\n" +
                    ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS audit_logs (\n" +
                    "id BIGSERIAL PRIMARY KEY,\n" +
                    "document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,\n" +
                    "action TEXT NOT NULL,\n" +
                    "actor TEXT NOT NULL,\n" +
                    "at TIMESTAMPTZ NOT NULL DEFAULT now(),\n" +
                    "note TEXT\n" +
                    ")");
            
            // Migration: Update old states to new states
            try {
                st.executeUpdate("UPDATE documents SET state = 'TIEP_NHAN' WHERE state = 'DRAFT'");
                st.executeUpdate("UPDATE documents SET state = 'DANG_KY' WHERE state = 'SUBMITTED'");
                st.executeUpdate("UPDATE documents SET state = 'CHO_XEM_XET' WHERE state = 'CLASSIFIED'");
                st.executeUpdate("UPDATE documents SET state = 'DA_PHAN_CONG' WHERE state = 'APPROVED'");
                st.executeUpdate("UPDATE documents SET state = 'DANG_XU_LY' WHERE state = 'ISSUED'");
                st.executeUpdate("UPDATE documents SET state = 'HOAN_THANH' WHERE state = 'ARCHIVED'");
            } catch (SQLException e) {
                // Ignore if columns don't exist yet
                System.out.println("Migration completed (some states may not exist yet)");
            }
        }
    }

    public long insert(String title, String latestFileId) throws SQLException {
        String sql = "INSERT INTO documents(title, latest_file_id, state) VALUES(?, ?, 'TIEP_NHAN') RETURNING id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, latestFileId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public long insert(String title, String latestFileId, OffsetDateTime deadline, String assignedTo, String priority) throws SQLException {
        String sql = "INSERT INTO documents(title, latest_file_id, deadline, assigned_to, priority, state) VALUES(?, ?, ?, ?, ?, 'TIEP_NHAN') RETURNING id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, latestFileId);
            ps.setObject(3, deadline);
            ps.setString(4, assignedTo);
            ps.setString(5, priority);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public Document insert(Document doc) throws SQLException {
        String sql = "INSERT INTO documents(title, latest_file_id, state, classification, security_level, doc_number, doc_year, deadline, assigned_to, priority) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, doc.title());
            ps.setString(2, doc.latestFileId());
            ps.setString(3, doc.state().name());
            ps.setString(4, doc.classification());
            ps.setString(5, doc.securityLevel());
            ps.setObject(6, doc.docNumber());
            ps.setObject(7, doc.docYear());
            ps.setObject(8, doc.deadline());
            ps.setString(9, doc.assignedTo());
            ps.setString(10, doc.priority());
            try (var rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                return new Document(id, doc.title(), doc.createdAt(), doc.latestFileId(), doc.state(),
                                  doc.classification(), doc.securityLevel(), doc.docNumber(), doc.docYear(),
                                  doc.deadline(), doc.assignedTo(), doc.priority());
            }
        }
    }

    public void addVersion(long docId, String fileId, int versionNo) throws SQLException {
        String sql = "INSERT INTO document_versions(document_id, file_id, version_no) VALUES(?, ?, ?)";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setLong(1, docId);
            ps.setString(2, fileId);
            ps.setInt(3, versionNo);
            ps.executeUpdate();
        }
    }

    public List<Document> list() throws SQLException {
        String sql = "SELECT id, title, created_at, latest_file_id, state, classification, security_level, doc_number, doc_year, deadline, assigned_to, priority FROM documents ORDER BY created_at DESC";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            try (var rs = ps.executeQuery()) {
                List<Document> out = new ArrayList<>();
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String title = rs.getString("title");
                    Timestamp ts = rs.getTimestamp("created_at");
                    String fileId = rs.getString("latest_file_id");
                    String state = rs.getString("state");
                    String classification = rs.getString("classification");
                    String securityLevel = rs.getString("security_level");
                    Integer docNumber = rs.getObject("doc_number", Integer.class);
                    Integer docYear = rs.getObject("doc_year", Integer.class);
                    Timestamp deadlineTs = rs.getTimestamp("deadline");
                    OffsetDateTime deadline = deadlineTs != null ? deadlineTs.toInstant().atOffset(ZoneOffset.UTC) : null;
                    String assignedTo = rs.getString("assigned_to");
                    String priority = rs.getString("priority");
                    OffsetDateTime odt = ts.toInstant().atOffset(ZoneOffset.UTC);
                    out.add(new Document(id, title, odt, fileId, DocState.valueOf(state), classification, securityLevel, docNumber, docYear, deadline, assignedTo, priority));
                }
                return out;
            }
        }
    }

    public void updateLatestFileId(long id, String fileId) throws SQLException {
        String sql = "UPDATE documents SET latest_file_id = ? WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, fileId);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void updateState(long id, DocState state) throws SQLException {
        String sql = "UPDATE documents SET state = ? WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, state.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void setClassification(long id, String classification, String securityLevel) throws SQLException {
        String sql = "UPDATE documents SET classification = ?, security_level = ? WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, classification);
            ps.setString(2, securityLevel);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public int nextVersionNo(long docId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(version_no), 0) + 1 FROM document_versions WHERE document_id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setLong(1, docId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void addAudit(long docId, String action, String actor, String note) throws SQLException {
        String sql = "INSERT INTO audit_logs(document_id, action, actor, note) VALUES(?, ?, ?, ?)";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setLong(1, docId);
            ps.setString(2, action);
            ps.setString(3, actor);
            ps.setString(4, note);
            ps.executeUpdate();
        }
    }

    public Document getById(long id) throws SQLException {
        String sql = "SELECT id, title, created_at, latest_file_id, state, classification, security_level, doc_number, doc_year, deadline, assigned_to, priority FROM documents WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long did = rs.getLong("id");
                String title = rs.getString("title");
                Timestamp ts = rs.getTimestamp("created_at");
                String fileId = rs.getString("latest_file_id");
                String state = rs.getString("state");
                String classification = rs.getString("classification");
                String securityLevel = rs.getString("security_level");
                Integer docNumber = rs.getObject("doc_number", Integer.class);
                Integer docYear = rs.getObject("doc_year", Integer.class);
                OffsetDateTime odt = ts.toInstant().atOffset(ZoneOffset.UTC);
                Timestamp deadlineTs = rs.getTimestamp("deadline");
                OffsetDateTime deadline = deadlineTs != null ? deadlineTs.toInstant().atOffset(ZoneOffset.UTC) : null;
                String assignedTo = rs.getString("assigned_to");
                String priority = rs.getString("priority");
                return new Document(did, title, odt, fileId, DocState.valueOf(state), classification, securityLevel, docNumber, docYear, deadline, assignedTo, priority);
            }
        }
    }

    public List<Document> searchByTitle(String keyword) throws SQLException {
        String sql = "SELECT id, title, created_at, latest_file_id, state, classification, security_level, doc_number, doc_year, deadline, assigned_to, priority FROM documents WHERE title ILIKE ? ORDER BY created_at DESC";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            try (var rs = ps.executeQuery()) {
                List<Document> out = new ArrayList<>();
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String title = rs.getString("title");
                    Timestamp ts = rs.getTimestamp("created_at");
                    String fileId = rs.getString("latest_file_id");
                    String state = rs.getString("state");
                    String classification = rs.getString("classification");
                    String securityLevel = rs.getString("security_level");
                    Integer docNumber = rs.getObject("doc_number", Integer.class);
                    Integer docYear = rs.getObject("doc_year", Integer.class);
                    OffsetDateTime odt = ts.toInstant().atOffset(ZoneOffset.UTC);
                    Timestamp deadlineTs = rs.getTimestamp("deadline");
                    OffsetDateTime deadline = deadlineTs != null ? deadlineTs.toInstant().atOffset(ZoneOffset.UTC) : null;
                    String assignedTo = rs.getString("assigned_to");
                    String priority = rs.getString("priority");
                    out.add(new Document(id, title, odt, fileId, DocState.valueOf(state), classification, securityLevel, docNumber, docYear, deadline, assignedTo, priority));
                }
                return out;
            }
        }
    }

    public String getFileIdByVersion(long docId, int versionNo) throws SQLException {
        String sql = "SELECT file_id FROM document_versions WHERE document_id = ? AND version_no = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setLong(1, docId);
            ps.setInt(2, versionNo);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    public List<Integer> listVersions(long docId) throws SQLException {
        String sql = "SELECT version_no FROM document_versions WHERE document_id = ? ORDER BY version_no";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setLong(1, docId);
            try (var rs = ps.executeQuery()) {
                List<Integer> out = new ArrayList<>();
                while (rs.next()) out.add(rs.getInt(1));
                return out;
            }
        }
    }

    public int nextDocNumberForYear(int year) throws SQLException {
        String sql = "SELECT COALESCE(MAX(doc_number), 0) + 1 FROM documents WHERE doc_year = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void assignIssueNumber(long id, int number, int year) throws SQLException {
        String sql = "UPDATE documents SET doc_number = ?, doc_year = ? WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setInt(1, number);
            ps.setInt(2, year);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }
    
    public List<AuditLog> getAuditLogs(long docId) throws SQLException {
        String sql = "SELECT id, document_id, action, actor, at, note FROM audit_logs WHERE document_id = ? ORDER BY at";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setLong(1, docId);
            try (var rs = ps.executeQuery()) {
                List<AuditLog> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(new AuditLog(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getString("action"),
                        rs.getString("actor"),
                        rs.getObject("at", OffsetDateTime.class),
                        rs.getString("note")
                    ));
                }
                return logs;
            }
        }
    }

    public void updateDocumentAssignment(long id, String assignedTo, String priority, OffsetDateTime deadline, String instructions) throws SQLException {
        String sql = "UPDATE documents SET assigned_to = ?, priority = ?, deadline = ? WHERE id = ?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, assignedTo);
            ps.setString(2, priority);
            ps.setObject(3, deadline);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
        
        // Add audit log
        addAudit(id, "ASSIGN", "System", "Phân phối cho: " + assignedTo + 
                (instructions != null && !instructions.isEmpty() ? " | Hướng dẫn: " + instructions : ""));
    }

    /**
     * Cập nhật người được phân công
     */
    public void updateAssignedTo(long id, String assignedTo) throws SQLException {
        try (var conn = ds.getConnection()) {
            var sql = "UPDATE documents SET assigned_to = ? WHERE id = ?";
            try (var ps = conn.prepareStatement(sql)) {
                ps.setString(1, assignedTo);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        }
    }
}

