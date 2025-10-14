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
     * Văn thư đăng ký văn bản (từ TIEP_NHAN -> DANG_KY)
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
     * Lãnh đạo xem xét và chỉ đạo xử lý (từ DANG_KY -> CHO_XEM_XET)
     */
    public void xemXet(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.DANG_KY) throw new IllegalStateException("Chỉ xem xét sau khi đăng ký");
        ensureRole(actor, Role.LANH_DAO);
        repo.updateState(id, DocState.CHO_XEM_XET);
        repo.addAudit(id, "XEM_XET", actor, note);
    }

    /**
     * Lãnh đạo phân công xử lý (từ CHO_XEM_XET -> DA_PHAN_CONG)
     */
    public void phanCong(long id, String actor, String assignedTo, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.CHO_XEM_XET) throw new IllegalStateException("Chỉ phân công sau khi xem xét");
        ensureRole(actor, Role.LANH_DAO);
        repo.updateAssignedTo(id, assignedTo);
        repo.updateState(id, DocState.DA_PHAN_CONG);
        repo.addAudit(id, "PHAN_CONG", actor, "Phân công cho: " + assignedTo + ". " + note);
    }

    /**
     * Cán bộ chuyên môn bắt đầu xử lý (từ DA_PHAN_CONG -> DANG_XU_LY)
     */
    public void batDauXuLy(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.DA_PHAN_CONG) throw new IllegalStateException("Chỉ bắt đầu xử lý sau khi được phân công");
        ensureRole(actor, Role.CAN_BO_CHUYEN_MON);
        repo.updateState(id, DocState.DANG_XU_LY);
        repo.addAudit(id, "BAT_DAU_XU_LY", actor, note);
    }

    /**
     * Cán bộ chuyên môn hoàn thành xử lý (từ DANG_XU_LY -> HOAN_THANH)
     */
    public void hoanThanh(long id, String actor, String note) throws SQLException {
        var d = repo.getById(id);
        if (d == null) throw new IllegalArgumentException("Không tìm thấy văn bản");
        if (d.state() != DocState.DANG_XU_LY) throw new IllegalStateException("Chỉ hoàn thành sau khi đang xử lý");
        ensureRole(actor, Role.CAN_BO_CHUYEN_MON);
        repo.updateState(id, DocState.HOAN_THANH);
        repo.addAudit(id, "HOAN_THANH", actor, note);
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

