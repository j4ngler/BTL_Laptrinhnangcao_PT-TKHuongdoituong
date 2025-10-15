package com.example.docmgmt.service;

import com.example.docmgmt.domain.Models.DocState;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.UserRepository;
import com.example.docmgmt.domain.Models.Role;

import java.sql.SQLException;

/**
 * Service xử lý workflow cho quy trình văn bản đến
 */
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

    /**
     * Bước 1: Văn thư tiếp nhận văn bản (ghi nhận audit, không đổi trạng thái)
     */
    public void tiepNhan(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        ensureRole(actor, Role.VAN_THU);
        repo.addAudit(id, "TIEP_NHAN", actor, note);
    }

    /**
     * Bước 2: Văn thư đăng ký văn bản (từ TIEP_NHAN -> DANG_KY)
     */
    public void dangKy(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.TIEP_NHAN) throw new IllegalStateException("Chỉ đăng ký sau khi tiếp nhận");
        ensureRole(actor, Role.VAN_THU);
        repo.updateState(id, DocState.DANG_KY);
        repo.addAudit(id, "DANG_KY", actor, note);
    }

    /**
     * Bước 3: Văn thư trình lãnh đạo (từ DANG_KY -> CHO_XEM_XET)
     */
    public void trinhLanhDao(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.DANG_KY) throw new IllegalStateException("Chỉ trình sau khi đăng ký");
        ensureRole(actor, Role.VAN_THU);
        repo.updateState(id, DocState.CHO_XEM_XET);
        repo.addAudit(id, "TRINH_LANH_DAO", actor, note);
    }

    /**
     * Bước 4: Lãnh đạo chỉ đạo xử lý (từ CHO_XEM_XET -> DA_PHAN_CONG)
     */
    public void chiDaoXuLy(long id, String actor, String assignedTo, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.CHO_XEM_XET) throw new IllegalStateException("Chỉ chỉ đạo sau khi trình");
        ensureRole(actor, Role.LANH_DAO);
        repo.updateAssignedTo(id, assignedTo);
        repo.updateState(id, DocState.DA_PHAN_CONG);
        repo.addAudit(id, "CHI_DAO_XU_LY", actor, "Chỉ đạo xử lý cho: " + assignedTo + ". " + note);
    }

    /**
     * Bước 5: Cán bộ chuyên môn thực hiện xử lý (từ DA_PHAN_CONG -> DANG_XU_LY -> CHO_DUYET)
     */
    public void thucHienXuLy(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.DA_PHAN_CONG) throw new IllegalStateException("Chỉ thực hiện sau khi được phân công");
        ensureRole(actor, Role.CAN_BO_CHUYEN_MON);
        repo.updateState(id, DocState.CHO_DUYET);
        repo.addAudit(id, "THUC_HIEN_XU_LY", actor, note);
    }

    /**
     * Bước 6: Lãnh đạo xét duyệt (từ CHO_DUYET -> HOAN_THANH)
     */
    public void xetDuyet(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.CHO_DUYET) throw new IllegalStateException("Chỉ duyệt sau khi cán bộ xử lý xong");
        ensureRole(actor, Role.LANH_DAO);
        repo.updateState(id, DocState.HOAN_THANH);
        repo.addAudit(id, "XET_DUYET", actor, note);
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

