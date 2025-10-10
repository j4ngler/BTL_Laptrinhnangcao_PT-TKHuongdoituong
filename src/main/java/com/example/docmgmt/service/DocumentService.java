package com.example.docmgmt.service;

import com.example.docmgmt.config.Config;
import com.example.docmgmt.domain.Models.Document;
import com.example.docmgmt.domain.Models.AuditLog;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.GridFsRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

public final class DocumentService implements AutoCloseable {
    private final DocumentRepository docRepo;
    private final GridFsRepository fsRepo;

    public DocumentService(Config config) throws SQLException {
        this.docRepo = new DocumentRepository(config.dataSource);
        this.fsRepo = new GridFsRepository(config.mongoClient, config.mongoDb, config.mongoBucket);
        this.docRepo.migrate();
    }

    public long createDocument(String title, Path path) throws IOException, SQLException {
        String fileId = fsRepo.upload(path, title);
        return docRepo.insert(title, fileId);
    }

    public List<Document> listDocuments() throws SQLException {
        return docRepo.list();
    }

    public void exportDocument(long docId, Path outPath) throws SQLException, IOException {
        var doc = docRepo.getById(docId);
        if (doc == null) throw new IllegalArgumentException("Không tìm thấy văn bản id=" + docId);
        String fileId = doc.latestFileId();
        fsRepo.download(fileId, outPath);
    }

    public long addVersion(long docId, String title, Path file) throws IOException, SQLException {
        int next = docRepo.nextVersionNo(docId);
        String fileId = fsRepo.upload(file, title + "#v" + next);
        docRepo.addVersion(docId, fileId, next);
        docRepo.updateLatestFileId(docId, fileId);
        return next;
    }

    public void exportVersion(long docId, int versionNo, Path outPath) throws SQLException, IOException {
        String fileId = docRepo.getFileIdByVersion(docId, versionNo);
        if (fileId == null) throw new IllegalArgumentException("Không có phiên bản " + versionNo + " cho văn bản " + docId);
        fsRepo.download(fileId, outPath);
    }

    public List<Document> searchByTitle(String keyword) throws SQLException {
        return docRepo.searchByTitle(keyword);
    }
    
    public Document getDocumentById(long id) throws SQLException {
        return docRepo.getById(id);
    }
    
    public List<AuditLog> getAuditLogs(long docId) throws SQLException {
        return docRepo.getAuditLogs(docId);
    }

    public javax.sql.DataSource getDataSource() {
        return docRepo.getDataSource();
    }

    @Override
    public void close() {
        // no-op, resources managed externally
    }
}

