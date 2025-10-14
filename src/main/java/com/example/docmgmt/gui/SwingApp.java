package com.example.docmgmt.gui;

import com.example.docmgmt.config.Config;
import com.example.docmgmt.domain.Models;
import com.example.docmgmt.domain.Models.Document;
import com.example.docmgmt.domain.Models.AuditLog;
import com.example.docmgmt.domain.Models.Role;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.UserRepository;
import com.example.docmgmt.service.DocumentService;
import com.example.docmgmt.service.WorkflowService;
import com.example.docmgmt.service.AuthenticationService;
import com.example.docmgmt.service.SimpleEmailService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;

public class SwingApp {
    private final DocumentService docService;
    private final WorkflowService workflowService;
    private final AuthenticationService authService;
    private final SimpleEmailService emailService;
    private com.example.docmgmt.service.SimpleMultiGmailManager autoSyncManager;
    private JFrame frame;
    private JTable table;
    private DefaultTableModel model;
    private JTextField searchField;
    private JLabel userInfoLabel;

    public SwingApp() throws Exception {
        System.out.println("Đang khởi tạo SwingApp...");
        
        try {
            System.out.println("Đang kết nối database...");
            Config config = Config.fromEnv();
            System.out.println("Database connected successfully");
            
            System.out.println("Đang khởi tạo services...");
            this.docService = new DocumentService(config);
            var repo = new DocumentRepository(config.dataSource);
            var ur = new UserRepository(config.dataSource); 
            ur.migrate();
            this.workflowService = new WorkflowService(repo, ur);
            this.authService = new AuthenticationService(ur);
            var gridRepo = new com.example.docmgmt.repo.GridFsRepository(config.mongoClient, "docmgmt", "files");
            this.emailService = new SimpleEmailService(repo, gridRepo);
            // Auto-sync Gmail: load accounts từ DB và chạy nền nếu có
            try {
                var gaRepo = new com.example.docmgmt.repo.GmailAccountRepository(config.dataSource);
                gaRepo.migrate();
                this.autoSyncManager = new com.example.docmgmt.service.SimpleMultiGmailManager(repo, gridRepo, gaRepo, 5, 5, "is:unread");
                if (!gaRepo.listActive().isEmpty()) {
                    this.autoSyncManager.startAutoSync();
                }
            } catch (Exception ex) {
                System.err.println("Auto-sync init failed: " + ex.getMessage());
            }
            System.out.println("Services initialized successfully");
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo database: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Khởi tạo các biến cần thiết trước
        frame = new JFrame("Quản lý văn bản đến");
        model = new DefaultTableModel(new Object[]{"ID","Tiêu đề","Trạng thái","Tạo lúc","Số/VB","Thời hạn","Độ ưu tiên","Phân công"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        searchField = new JTextField(20);
        
        // Hiển thị dialog đăng nhập trước
        if (!showLoginDialog()) {
            System.exit(0);
            return;
        }

        // Cập nhật title sau khi đăng nhập thành công
        frame.setTitle("Quản lý văn bản đến - " + authService.getCurrentUserRoleName());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        // Dừng auto-sync khi đóng
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (autoSyncManager != null) {
                    autoSyncManager.stopAutoSync();
                    autoSyncManager.shutdown();
                }
            }
        });

        // Top panel chỉ hiển thị thông tin người dùng (các thao tác đưa vào Menu)
        JPanel top = new JPanel(new BorderLayout());
        
        // User info panel (right side)
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        userInfoLabel = new JLabel("Người dùng: " + authService.getCurrentUser().username() + 
                                  " (" + authService.getCurrentUserRoleName() + ")");
        userInfoLabel.setFont(new Font("Arial", Font.BOLD, 12));
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setBackground(new Color(220, 20, 60));
        btnLogout.setForeground(Color.WHITE);
        userPanel.add(userInfoLabel);
        userPanel.add(new JSeparator(SwingConstants.VERTICAL));
        userPanel.add(btnLogout);
        
        // Add panels to top
        top.add(userPanel, BorderLayout.EAST);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(table);

        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(scroll, BorderLayout.CENTER);
        
        // Menu bar với các thao tác
        Role currentRole = authService.getCurrentUser().role();
        frame.setJMenuBar(buildMenuBar(currentRole));
        
        btnLogout.addActionListener(e -> doLogout());

        reload();
    }

    private JMenuBar buildMenuBar(Role currentRole) {
        JMenuBar menuBar = new JMenuBar();
        
        JMenu menuFile = new JMenu("Tệp");
        JMenuItem miAdd = new JMenuItem("Thêm văn bản...");
        JMenuItem miExport = new JMenuItem("Xuất...");
        JMenuItem miDetails = new JMenuItem("Chi tiết");
        JMenuItem miExit = new JMenuItem("Thoát");
        miAdd.addActionListener(e -> doAdd());
        miExport.addActionListener(e -> doExport());
        miDetails.addActionListener(e -> doDetails());
        miExit.addActionListener(e -> frame.dispose());
        menuFile.add(miAdd);
        menuFile.add(miExport);
        menuFile.add(miDetails);
        menuFile.addSeparator();
        menuFile.add(miExit);
        menuBar.add(menuFile);
        
        JMenu menuActions = new JMenu("Thao tác");
        JMenuItem miRefresh = new JMenuItem("Làm mới");
        JMenuItem miSearch = new JMenuItem("Tìm kiếm...");
        miRefresh.addActionListener(e -> reload());
        miSearch.addActionListener(e -> doSearchInput());
        menuActions.add(miRefresh);
        menuActions.add(miSearch);
        menuBar.add(menuActions);
        
        JMenu menuWorkflow = new JMenu("Workflow");
        if (currentRole == Role.VAN_THU) {
            JMenuItem miDangKy = new JMenuItem("Đăng ký");
            miDangKy.addActionListener(e -> doWorkflowAction("DANG_KY"));
            menuWorkflow.add(miDangKy);
        } else if (currentRole == Role.LANH_DAO) {
            JMenuItem miXemXet = new JMenuItem("Xem xét");
            JMenuItem miPhanCong = new JMenuItem("Phân công");
            miXemXet.addActionListener(e -> doWorkflowAction("XEM_XET"));
            miPhanCong.addActionListener(e -> doWorkflowAction("PHAN_CONG"));
            menuWorkflow.add(miXemXet);
            menuWorkflow.add(miPhanCong);
        } else if (currentRole == Role.CAN_BO_CHUYEN_MON) {
            JMenuItem miBatDau = new JMenuItem("Bắt đầu xử lý");
            JMenuItem miHoanThanh = new JMenuItem("Hoàn thành");
            miBatDau.addActionListener(e -> doWorkflowAction("BAT_DAU_XU_LY"));
            miHoanThanh.addActionListener(e -> doWorkflowAction("HOAN_THANH"));
            menuWorkflow.add(miBatDau);
            menuWorkflow.add(miHoanThanh);
        }
        menuBar.add(menuWorkflow);
        
        JMenu menuEmail = new JMenu("Email");
        JMenuItem miFetch = new JMenuItem("Nhận từ Gmail...");
        miFetch.addActionListener(e -> doEmail());
        menuEmail.add(miFetch);
        JMenuItem miManage = new JMenuItem("Quản lý accounts...");
        miManage.addActionListener(e -> {
            try {
                var gaRepo = new com.example.docmgmt.repo.GmailAccountRepository(docService.getDataSource());
                gaRepo.migrate();
                new GmailAccountsDialog(frame, gaRepo).setVisible(true);
            } catch (Exception ex) { showError(ex); }
        });
        menuEmail.add(miManage);
        menuBar.add(menuEmail);
        
        return menuBar;
    }

    private void reload() {
        try {
            List<Models.Document> docs = docService.listDocuments();
            model.setRowCount(0);
            for (var d : docs) {
                String docNumber = d.docNumber() != null ? d.docNumber() + "/" + d.docYear() : "";
                String deadline = d.deadline() != null ? d.deadline().toString().substring(0, 16) : "";
                String priority = d.priority() != null ? getPriorityDisplayName(d.priority()) : "Thường";
                String assignedTo = d.assignedTo() != null ? d.assignedTo() : "Chưa phân công";
                
                model.addRow(new Object[]{
                    d.id(), d.title(), d.state().name(), 
                    d.createdAt().toString().substring(0, 19), docNumber,
                    deadline, priority, assignedTo
                });
            }
        } catch (Exception ex) { showError(ex); }
    }

    private String getPriorityDisplayName(String priority) {
        if (priority == null) return "Thường";
        return switch (priority) {
            case "NORMAL" -> "Thường";
            case "URGENT" -> "Khẩn";
            case "EMERGENCY" -> "Thượng khẩn";
            case "FIRE" -> "Hỏa tốc";
            default -> priority;
        };
    }

    @SuppressWarnings("unused")
    private void doSearch() {
        try {
            String kw = searchField.getText();
            List<Models.Document> docs = (kw == null || kw.isBlank()) ? docService.listDocuments() : docService.searchByTitle(kw);
            model.setRowCount(0);
            for (var d : docs) {
                String docNumber = d.docNumber() != null ? d.docNumber() + "/" + d.docYear() : "";
                String deadline = d.deadline() != null ? d.deadline().toString().substring(0, 16) : "";
                String priority = d.priority() != null ? getPriorityDisplayName(d.priority()) : "Thường";
                String assignedTo = d.assignedTo() != null ? d.assignedTo() : "Chưa phân công";
                
                model.addRow(new Object[]{
                    d.id(), d.title(), d.state().name(), 
                    d.createdAt().toString().substring(0, 19), docNumber,
                    deadline, priority, assignedTo
                });
            }
        } catch (Exception ex) { showError(ex); }
    }

    private void doSearchInput() {
        String kw = JOptionPane.showInputDialog(frame, "Nhập từ khóa cần tìm:", "Tìm kiếm", JOptionPane.QUESTION_MESSAGE);
        if (kw == null) return;
        try {
            List<Models.Document> docs = (kw.isBlank()) ? docService.listDocuments() : docService.searchByTitle(kw);
            model.setRowCount(0);
            for (var d : docs) {
                String docNumber = d.docNumber() != null ? d.docNumber() + "/" + d.docYear() : "";
                String deadline = d.deadline() != null ? d.deadline().toString().substring(0, 16) : "";
                String priority = d.priority() != null ? getPriorityDisplayName(d.priority()) : "Thường";
                String assignedTo = d.assignedTo() != null ? d.assignedTo() : "Chưa phân công";
                model.addRow(new Object[]{ d.id(), d.title(), d.state().name(), d.createdAt().toString().substring(0, 19), docNumber, deadline, priority, assignedTo });
            }
        } catch (Exception ex) { showError(ex); }
    }

    private Long selectedId() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        Object v = model.getValueAt(row, 0);
        return (v instanceof Number) ? ((Number) v).longValue() : Long.parseLong(String.valueOf(v));
    }

    private void doAdd() {
        try {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            File file = fc.getSelectedFile();
            String title = JOptionPane.showInputDialog(frame, "Nhập tiêu đề:", "Văn bản mới");
            if (title == null || title.isBlank()) return;
            long id = docService.createDocument(title, file.toPath());
            reload();
            JOptionPane.showMessageDialog(frame, "Đã thêm, id=" + id);
        } catch (Exception ex) { showError(ex); }
    }

    private void doExport() {
        Long id = selectedId(); if (id == null) { info("Chọn một dòng trước"); return; }
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        try {
            docService.exportDocument(id, out.toPath());
            info("Đã xuất");
        } catch (Exception ex) { showError(ex); }
    }

    private void doDetails() {
        Long id = selectedId(); 
        if (id == null) { 
            info("Chọn một dòng trước"); 
            return; 
        }
        
        try {
            // Lấy thông tin văn bản
            var doc = docService.getDocumentById(id);
            if (doc == null) {
                info("Không tìm thấy văn bản");
                return;
            }
            
            // Lấy audit logs
            var logs = docService.getAuditLogs(id);
            
            // Hiển thị dialog chi tiết
            showDetailsDialog(doc, logs);
            
        } catch (Exception ex) {
            showError(ex);
        }
    }

    @SuppressWarnings("unused")
    private void doDashboard() {
        info("Tính năng Dashboard đang được phát triển");
    }

    @SuppressWarnings("unused")
    private void doDistribute() {
        info("Tính năng phân phối văn bản đang được phát triển");
    }

    @SuppressWarnings("unused")
    private void doRecall() {
        info("Tính năng thu hồi văn bản đang được phát triển");
    }


    @SuppressWarnings("unused")
    private void doTransition(String action) {
        Long id = selectedId(); if (id == null) { info("Chọn một dòng trước"); return; }
        
        switch (action) {
            case "SUBMIT" -> showSubmitDialog(id);
            case "CLASSIFY" -> showClassifyDialog(id);
            case "APPROVE" -> showApproveDialog(id);
            case "ISSUE" -> showIssueDialog(id);
            case "ARCHIVE" -> showArchiveDialog(id);
        }
    }

    private void showClassifyDialog(Long id) {
        JDialog dialog = new JDialog(frame, "Phân loại văn bản", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(frame);
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Actor
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Người thực hiện:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField actorField = new JTextField("Bao", 15);
        panel.add(actorField, gbc);
        
        // Classification
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Phân loại:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel classPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton classInternal = new JRadioButton("Nội bộ", true);
        JRadioButton classPublic = new JRadioButton("Công khai");
        ButtonGroup classGroup = new ButtonGroup();
        classGroup.add(classInternal);
        classGroup.add(classPublic);
        classPanel.add(classInternal);
        classPanel.add(classPublic);
        panel.add(classPanel, gbc);
        
        // Security Level
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Độ mật:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel securityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton securityNormal = new JRadioButton("Thường", true);
        JRadioButton securitySecret = new JRadioButton("Mật");
        ButtonGroup securityGroup = new ButtonGroup();
        securityGroup.add(securityNormal);
        securityGroup.add(securitySecret);
        securityPanel.add(securityNormal);
        securityPanel.add(securitySecret);
        panel.add(securityPanel, gbc);
        
        // Note
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Ghi chú:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField noteField = new JTextField(15);
        panel.add(noteField, gbc);
        
        // Buttons
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Hủy");
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        panel.add(buttonPanel, gbc);
        
        dialog.add(panel);
        
        okBtn.addActionListener(e -> {
            String actor = actorField.getText().trim();
            String classification = classInternal.isSelected() ? "nội bộ" : "công khai";
            String security = securityNormal.isSelected() ? "thường" : "mật";
            String note = noteField.getText().trim();
            
            if (actor.isEmpty()) {
                info("Nhập người thực hiện");
                return;
            }
            
            String fullNote = classification + "|" + security + "|" + note;
            try {
                workflowService.xemXet(id, actor, fullNote);
                reload();
                dialog.dispose();
            } catch (Exception ex) {
                showError(ex);
            }
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        
        dialog.setVisible(true);
    }

    private void showSubmitDialog(Long id) {
        JDialog dialog = new JDialog(frame, "Trình văn bản", true);
        dialog.setSize(350, 200);
        dialog.setLocationRelativeTo(frame);
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Actor
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Người thực hiện:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField actorField = new JTextField("Bao", 15);
        panel.add(actorField, gbc);
        
        // Note
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Ghi chú:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField noteField = new JTextField(15);
        panel.add(noteField, gbc);
        
        // Buttons
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Hủy");
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        panel.add(buttonPanel, gbc);
        
        dialog.add(panel);
        
        okBtn.addActionListener(e -> {
            String actor = actorField.getText().trim();
            String note = noteField.getText().trim();
            
            if (actor.isEmpty()) {
                info("Nhập người thực hiện");
                return;
            }
            
            try {
                workflowService.dangKy(id, actor, note);
                reload();
                dialog.dispose();
            } catch (Exception ex) {
                showError(ex);
            }
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    private void showApproveDialog(Long id) {
        JDialog dialog = new JDialog(frame, "Duyệt văn bản", true);
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(frame);
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Actor
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Người duyệt:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField actorField = new JTextField("Bao", 15);
        panel.add(actorField, gbc);
        
        // Decision
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Quyết định:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel decisionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton decisionApprove = new JRadioButton("Duyệt", true);
        JRadioButton decisionReject = new JRadioButton("Từ chối");
        ButtonGroup decisionGroup = new ButtonGroup();
        decisionGroup.add(decisionApprove);
        decisionGroup.add(decisionReject);
        decisionPanel.add(decisionApprove);
        decisionPanel.add(decisionReject);
        panel.add(decisionPanel, gbc);
        
        // Assigned To
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Phân công cho:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField assignedToField = new JTextField(15);
        panel.add(assignedToField, gbc);
        
        // Note
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Ghi chú:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField noteField = new JTextField(15);
        panel.add(noteField, gbc);
        
        // Buttons
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Hủy");
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        panel.add(buttonPanel, gbc);
        
        dialog.add(panel);
        
        okBtn.addActionListener(e -> {
            String actor = actorField.getText().trim();
            String decision = decisionApprove.isSelected() ? "duyệt" : "từ chối";
            String assignedTo = assignedToField.getText().trim();
            String note = noteField.getText().trim();
            
            if (actor.isEmpty()) {
                info("Nhập người duyệt");
                return;
            }
            
            if (assignedTo.isEmpty()) {
                info("Nhập người được phân công");
                return;
            }
            
            String fullNote = decision + " - " + note;
            try {
                workflowService.phanCong(id, actor, assignedTo, fullNote);
                reload();
                dialog.dispose();
            } catch (Exception ex) {
                showError(ex);
            }
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    private void showIssueDialog(Long id) {
        JDialog dialog = new JDialog(frame, "Ban hành văn bản", true);
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(frame);
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Actor
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Người ban hành:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField actorField = new JTextField("Bao", 15);
        panel.add(actorField, gbc);
        
        // Issue Type
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Loại ban hành:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton typeOfficial = new JRadioButton("Chính thức", true);
        JRadioButton typeTrial = new JRadioButton("Thử nghiệm");
        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(typeOfficial);
        typeGroup.add(typeTrial);
        typePanel.add(typeOfficial);
        typePanel.add(typeTrial);
        panel.add(typePanel, gbc);
        
        // Note
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Ghi chú:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField noteField = new JTextField(15);
        panel.add(noteField, gbc);
        
        // Buttons
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Hủy");
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        panel.add(buttonPanel, gbc);
        
        dialog.add(panel);
        
        okBtn.addActionListener(e -> {
            String actor = actorField.getText().trim();
            String type = typeOfficial.isSelected() ? "ban hành chính thức" : "ban hành thử nghiệm";
            String note = noteField.getText().trim();
            
            if (actor.isEmpty()) {
                info("Nhập người ban hành");
                return;
            }
            
            String fullNote = type + " - " + note;
            try {
                workflowService.batDauXuLy(id, actor, fullNote);
                reload();
                dialog.dispose();
            } catch (Exception ex) {
                showError(ex);
            }
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    private void showArchiveDialog(Long id) {
        JDialog dialog = new JDialog(frame, "Lưu trữ văn bản", true);
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(frame);
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Actor
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Người lưu trữ:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField actorField = new JTextField("Bao", 15);
        panel.add(actorField, gbc);
        
        // Archive Type
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Loại lưu trữ:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel archivePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton archiveNormal = new JRadioButton("Thường", true);
        JRadioButton archivePermanent = new JRadioButton("Vĩnh viễn");
        ButtonGroup archiveGroup = new ButtonGroup();
        archiveGroup.add(archiveNormal);
        archiveGroup.add(archivePermanent);
        archivePanel.add(archiveNormal);
        archivePanel.add(archivePermanent);
        panel.add(archivePanel, gbc);
        
        // Note
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Ghi chú:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField noteField = new JTextField(15);
        panel.add(noteField, gbc);
        
        // Buttons
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Hủy");
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        panel.add(buttonPanel, gbc);
        
        dialog.add(panel);
        
        okBtn.addActionListener(e -> {
            String actor = actorField.getText().trim();
            String type = archiveNormal.isSelected() ? "lưu trữ thường" : "lưu trữ vĩnh viễn";
            String note = noteField.getText().trim();
            
            if (actor.isEmpty()) {
                info("Nhập người lưu trữ");
                return;
            }
            
            String fullNote = type + " - " + note;
            try {
                workflowService.hoanThanh(id, actor, fullNote);
                reload();
                dialog.dispose();
            } catch (Exception ex) {
                showError(ex);
            }
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    private void showDetailsDialog(Document doc, List<AuditLog> logs) {
        JDialog dialog = new JDialog(frame, "Chi tiết văn bản", true);
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(frame);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Thông tin cơ bản
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        infoPanel.add(new JLabel("ID:"), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(String.valueOf(doc.id())), gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        infoPanel.add(new JLabel("Tiêu đề:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextArea titleArea = new JTextArea(doc.title(), 2, 30);
        titleArea.setEditable(false);
        titleArea.setLineWrap(true);
        titleArea.setWrapStyleWord(true);
        infoPanel.add(titleArea, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        infoPanel.add(new JLabel("Trạng thái:"), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(doc.state().toString()), gbc);
        
        gbc.gridx = 0; gbc.gridy = 3;
        infoPanel.add(new JLabel("Tạo lúc:"), gbc);
        gbc.gridx = 1;
        infoPanel.add(new JLabel(doc.createdAt().toString()), gbc);
        
        if (doc.docNumber() != null) {
            gbc.gridx = 0; gbc.gridy = 4;
            infoPanel.add(new JLabel("Số văn bản:"), gbc);
            gbc.gridx = 1;
            infoPanel.add(new JLabel(doc.docNumber() + "/" + doc.docYear()), gbc);
        }
        
        if (doc.classification() != null) {
            gbc.gridx = 0; gbc.gridy = 5;
            infoPanel.add(new JLabel("Phân loại:"), gbc);
            gbc.gridx = 1;
            infoPanel.add(new JLabel(doc.classification()), gbc);
        }
        
        if (doc.securityLevel() != null) {
            gbc.gridx = 0; gbc.gridy = 6;
            infoPanel.add(new JLabel("Độ mật:"), gbc);
            gbc.gridx = 1;
            infoPanel.add(new JLabel(doc.securityLevel()), gbc);
        }
        
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        
        // Lịch sử thay đổi
        JLabel historyLabel = new JLabel("Lịch sử thay đổi:");
        mainPanel.add(historyLabel, BorderLayout.CENTER);
        
        String[] columns = {"Thời gian", "Hành động", "Người thực hiện", "Ghi chú"};
        Object[][] data = new Object[logs.size()][4];
        for (int i = 0; i < logs.size(); i++) {
            AuditLog log = logs.get(i);
            data[i][0] = log.at().toString();
            data[i][1] = log.action();
            data[i][2] = log.actor();
            data[i][3] = log.note() != null ? log.note() : "";
        }
        
        JTable historyTable = new JTable(data, columns);
        historyTable.setEnabled(false);
        JScrollPane historyScroll = new JScrollPane(historyTable);
        historyScroll.setPreferredSize(new Dimension(0, 200));
        mainPanel.add(historyScroll, BorderLayout.CENTER);
        
        // Nút đóng
        JButton closeBtn = new JButton("Đóng");
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(closeBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    private void showError(Throwable ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(frame, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void info(String msg) { JOptionPane.showMessageDialog(frame, msg, "Thông báo", JOptionPane.INFORMATION_MESSAGE); }

    /**
     * Hiển thị dialog đăng nhập
     */
    private boolean showLoginDialog() {
        LoginDialog loginDialog = new LoginDialog(null, authService);
        loginDialog.setVisible(true);
        return loginDialog.isLoginSuccessful();
    }

    /**
     * Xử lý nhận văn bản từ email
     */
    private void doEmail() {
        try {
            // Hiển thị dialog cấu hình email
            EmailConfigDialog configDialog = new EmailConfigDialog(frame);
            configDialog.setVisible(true);
            
            if (!configDialog.isConfigSaved()) {
                return;
            }
            
            String email = configDialog.getEmail();
            String password = configDialog.getPassword();
            
            // Show progress dialog
            JDialog progressDialog = new JDialog(frame, "Đang nhận văn bản từ email...", true);
            progressDialog.setSize(400, 150);
            progressDialog.setLocationRelativeTo(frame);
            
            JPanel progressPanel = new JPanel(new BorderLayout());
            progressPanel.add(new JLabel("Đang kết nối và nhận văn bản từ Gmail...", JLabel.CENTER), BorderLayout.CENTER);
            
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressPanel.add(progressBar, BorderLayout.SOUTH);
            
            progressDialog.add(progressPanel);
            progressDialog.setVisible(true);
            
            // Run email fetching in background
            SwingUtilities.invokeLater(() -> {
                try {
                    int count = emailService.fetchEmailsFromGmail(email, password);
                    progressDialog.dispose();
                    info("Đã nhận " + count + " văn bản từ email thành công!");
                    reload();
                } catch (Exception e) {
                    progressDialog.dispose();
                    showError(e);
                }
            });
            
        } catch (Exception e) {
            showError(e);
        }
    }

    /**
     * Xử lý đăng xuất
     */
    private void doLogout() {
        int result = JOptionPane.showConfirmDialog(frame, "Bạn có chắc muốn đăng xuất?", 
                                                "Xác nhận đăng xuất", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            authService.logout();
            frame.dispose();
            // Restart application
            try {
                SwingApp newApp = new SwingApp();
                newApp.show();
            } catch (Exception e) {
                showError(e);
            }
        }
    }

    /**
     * Xử lý các hành động workflow
     */
    private void doWorkflowAction(String action) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(frame, "Vui lòng chọn văn bản cần xử lý!", 
                                        "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        long docId = (Long) model.getValueAt(row, 0);
        String actor = authService.getCurrentUser().username();
        
        try {
            switch (action) {
                case "DANG_KY":
                    String note1 = JOptionPane.showInputDialog(frame, "Ghi chú đăng ký:", "Đăng ký văn bản", JOptionPane.QUESTION_MESSAGE);
                    workflowService.dangKy(docId, actor, note1);
                    break;
                case "XEM_XET":
                    String note2 = JOptionPane.showInputDialog(frame, "Ghi chú xem xét:", "Xem xét văn bản", JOptionPane.QUESTION_MESSAGE);
                    workflowService.xemXet(docId, actor, note2);
                    break;
                case "PHAN_CONG":
                    String assignedTo = JOptionPane.showInputDialog(frame, "Phân công cho ai:", "Phân công xử lý", JOptionPane.QUESTION_MESSAGE);
                    if (assignedTo != null && !assignedTo.trim().isEmpty()) {
                        String note3 = JOptionPane.showInputDialog(frame, "Hướng dẫn xử lý:", "Phân công xử lý", JOptionPane.QUESTION_MESSAGE);
                        workflowService.phanCong(docId, actor, assignedTo, note3);
                    }
                    break;
                case "BAT_DAU_XU_LY":
                    String note4 = JOptionPane.showInputDialog(frame, "Ghi chú bắt đầu xử lý:", "Bắt đầu xử lý", JOptionPane.QUESTION_MESSAGE);
                    workflowService.batDauXuLy(docId, actor, note4);
                    break;
                case "HOAN_THANH":
                    String note5 = JOptionPane.showInputDialog(frame, "Báo cáo kết quả xử lý:", "Hoàn thành xử lý", JOptionPane.QUESTION_MESSAGE);
                    workflowService.hoanThanh(docId, actor, note5);
                    break;
            }
            
            info("Thực hiện " + action + " thành công!");
            reload();
            
        } catch (Exception e) {
            showError(e);
        }
    }

    public void show() { frame.setVisible(true); }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            try {
                SwingApp app = new SwingApp();
                app.show();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Lỗi khởi động GUI: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}


