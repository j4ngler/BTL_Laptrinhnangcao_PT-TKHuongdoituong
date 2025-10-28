package com.example.docmgmt.gui;

import com.example.docmgmt.repo.UserRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class UserManagementDialog extends JDialog {
    private final UserRepository userRepo;
    private DefaultTableModel model;
    private JTable table;

    public UserManagementDialog(JFrame parent, UserRepository userRepo) {
        super(parent, "Quản lý người dùng", true);
        this.userRepo = userRepo;
        init();
        loadUsers();
    }

    private void init() {
        setSize(800, 500);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout());

        // Table
        model = new DefaultTableModel(new Object[]{"Username", "Vai trò", "Chức vụ", "Đơn vị", "Trạng thái"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        JScrollPane scroll = new JScrollPane(table);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton btnApprove = new JButton("Duyệt");
        JButton btnReject = new JButton("Từ chối");
        JButton btnRefresh = new JButton("Làm mới");
        JButton btnClose = new JButton("Đóng");
        
        btnApprove.addActionListener(e -> doApprove());
        btnReject.addActionListener(e -> doReject());
        btnRefresh.addActionListener(e -> loadUsers());
        btnClose.addActionListener(e -> dispose());

        buttonPanel.add(btnApprove);
        buttonPanel.add(btnReject);
        buttonPanel.add(btnRefresh);
        buttonPanel.add(btnClose);

        add(scroll, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadUsers() {
        try {
            model.setRowCount(0);
            userRepo.list().forEach(user -> {
                model.addRow(new Object[]{
                    user.username(),
                    getRoleDisplayName(user.role()),
                    user.position() != null ? user.position() : "",
                    user.organization() != null ? user.organization() : "",
                    user.status() != null ? getStatusDisplayName(user.status()) : "N/A"
                });
            });
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi tải danh sách: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doApprove() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Chọn người dùng cần duyệt!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String username = (String) model.getValueAt(row, 0);
        try {
            userRepo.approveUser(username);
            JOptionPane.showMessageDialog(this, "Đã duyệt: " + username, "Thành công", JOptionPane.INFORMATION_MESSAGE);
            loadUsers();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doReject() {
        JOptionPane.showMessageDialog(this, "Chức năng từ chối đang được phát triển", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    private String getRoleDisplayName(com.example.docmgmt.domain.Models.Role role) {
        return switch (role) {
            case QUAN_TRI -> "Quản trị";
            case VAN_THU -> "Văn thư";
            case LANH_DAO -> "Lãnh đạo";
            case CAN_BO_CHUYEN_MON -> "Cán bộ chuyên môn";
        };
    }

    private String getStatusDisplayName(com.example.docmgmt.domain.Models.UserStatus status) {
        return switch (status) {
            case PENDING -> "Chờ duyệt";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Đã từ chối";
        };
    }
}

