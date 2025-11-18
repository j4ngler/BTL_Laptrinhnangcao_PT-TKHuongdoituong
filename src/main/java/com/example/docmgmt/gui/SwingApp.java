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

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Locale;
import java.util.List;

public class SwingApp {
    private final DocumentService docService;
    private final WorkflowService workflowService;
    private final AuthenticationService authService;
    private final com.example.docmgmt.service.EmailService emailService;
    private com.example.docmgmt.service.PendingEmailService pendingEmailService;
    private com.example.docmgmt.service.SimpleMultiGmailManager autoSyncManager;
    private JFrame frame;
    private JTable table;
    private DefaultTableModel model;
    private JTextField searchField;
    private JLabel userInfoLabel;
    private JPanel topPanel;
    private JScrollPane scrollPane;
    private JButton btnDashboard;

    public SwingApp() throws Exception {
        System.out.println("Dang khoi tao SwingApp...");
        
        try {
            System.out.println("Dang ket noi database...");
            Config config = Config.fromEnv();
            System.out.println("Database connected successfully");
            
            System.out.println("Dang khoi tao services...");
            this.docService = new DocumentService(config);
            var repo = new DocumentRepository(config.dataSource);
            var ur = new UserRepository(config.dataSource); 
            ur.migrate();
            this.workflowService = new WorkflowService(repo, ur);
            this.authService = new AuthenticationService(ur);
            var gridRepo = new com.example.docmgmt.repo.GridFsRepository(config.mongoClient, "docmgmt", "files");
            // Ưu tiên EmailService (IMAP thật) nếu khả dụng, fallback simple
            this.emailService = new com.example.docmgmt.service.EmailService(repo, gridRepo, config);
            // Khởi tạo PendingEmailService
            var pendingEmailRepo = new com.example.docmgmt.repo.PendingEmailRepository(config.dataSource);
            pendingEmailRepo.migrate();
            this.pendingEmailService = new com.example.docmgmt.service.PendingEmailService(
                pendingEmailRepo, repo, this.emailService, gridRepo);
            // Auto-sync Gmail: load accounts từ DB và chạy nền nếu có
            try {
                var gaRepo = new com.example.docmgmt.repo.GmailAccountRepository(config.dataSource);
                gaRepo.migrate();
                this.autoSyncManager = new com.example.docmgmt.service.SimpleMultiGmailManager(repo, gridRepo, gaRepo, 5, 5, "is:unread");
                // Không tự động chạy auto-sync trừ khi bật qua biến môi trường DM_AUTOSYNC=true
                String autoEnv = System.getenv("DM_AUTOSYNC");
                if ("true".equalsIgnoreCase(autoEnv) && !gaRepo.listActive().isEmpty()) {
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
        model = new DefaultTableModel(new Object[]{"ID","Tiêu đề","Trạng thái","Tạo lúc","Độ ưu tiên","Phân công","Ghi chú","Quy trình"}, 0) {
            public boolean isCellEditable(int r, int c) { return c == 7; }
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
        // Set full màn hình (maximize)
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        // Dừng auto-sync khi đóng
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (autoSyncManager != null) {
                    autoSyncManager.stopAutoSync();
                    autoSyncManager.shutdown();
                }
            }
        });

        // Top panel với header đẹp
        Role currentRole = authService.getCurrentUser().role();
        JPanel top = createTopPanel(currentRole);
        
        // Style table
        styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(table);
        
        // Lưu reference để có thể restore
        topPanel = top;
        scrollPane = scroll;

        // Set background cho frame
        frame.getContentPane().setBackground(new Color(245, 247, 250));

        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(scroll, BorderLayout.CENTER);
        // Renderer/Editor cho nút Quy trình
        int workflowColIndex = model.findColumn("Quy trình");
        table.getColumnModel().getColumn(workflowColIndex).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(workflowColIndex).setCellEditor(new ButtonEditor(new JCheckBox()));
        // Cấu hình độ rộng cột sau khi tạo bảng
        configureColumnWidths();
        // Double-click mở chi tiết ngay
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    doDetails();
                }
            }
        });
        
        // Menu bar với các thao tác
        frame.setJMenuBar(buildMenuBar(currentRole));

        // Nếu là admin, hiển thị Dashboard
        if (currentRole == Role.QUAN_TRI) {
            showAdminDashboard();
        } else {
            reload();
        }
    }

    private void showAdminDashboard() {
        try {
            var docRepo = new DocumentRepository(docService.getDataSource());
            var userRepo = new UserRepository(docService.getDataSource());
            userRepo.migrate();
            
            AdminDashboard dashboard = new AdminDashboard(frame, docRepo, userRepo, this::showDocumentView, this::doLogout);
            // Ẩn menu bar khi hiển thị dashboard
            frame.setJMenuBar(null);
            frame.getContentPane().removeAll();
            frame.getContentPane().add(dashboard, BorderLayout.CENTER);
            frame.getContentPane().revalidate();
            frame.getContentPane().repaint();
        } catch (Exception e) {
            showError(e);
        }
    }
    
    public void showDocumentView() {
        // Restore menu bar và document view
        Role currentRole = authService.getCurrentUser().role();
        frame.setJMenuBar(buildMenuBar(currentRole));
        
        // Recreate topPanel để đảm bảo có đầy đủ listeners và references
        topPanel = createTopPanel(currentRole);
        
        frame.getContentPane().removeAll();
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(topPanel, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        
        // Setup lại table renderers và editors nếu chưa có
        int workflowColIndex = model.findColumn("Quy trình");
        if (workflowColIndex >= 0) {
            table.getColumnModel().getColumn(workflowColIndex).setCellRenderer(new ButtonRenderer());
            table.getColumnModel().getColumn(workflowColIndex).setCellEditor(new ButtonEditor(new JCheckBox()));
        }
        
        // Cấu hình lại độ rộng cột
        configureColumnWidths();
        
        frame.getContentPane().revalidate();
        frame.getContentPane().repaint();
        
        // Load dữ liệu
        reload();
    }

    private JPanel createTopPanel(Role currentRole) {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(25, 42, 86));
        top.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        // Left panel với title và role info
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftPanel.setOpaque(false);
        
        JLabel iconLabel = new JLabel("■");
        iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        iconLabel.setForeground(Color.WHITE);
        
        JLabel titleLabel = new JLabel("QUẢN LÝ VĂN BẢN ĐẾN");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        
        // Role badge
        String roleName = authService.getCurrentUserRoleName();
        Color roleColor = getRoleColor(currentRole);
        JLabel roleLabel = new JLabel(roleName);
        roleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        roleLabel.setForeground(Color.WHITE);
        roleLabel.setBackground(roleColor);
        roleLabel.setOpaque(true);
        roleLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 255, 255, 150), 1),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        
        leftPanel.add(iconLabel);
        leftPanel.add(titleLabel);
        leftPanel.add(Box.createHorizontalStrut(20));
        leftPanel.add(roleLabel);
        
        // Dashboard button (chỉ cho admin)
        if (currentRole == Role.QUAN_TRI) {
            btnDashboard = new JButton("Dashboard");
            btnDashboard.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btnDashboard.setBackground(new Color(34, 139, 34));
            btnDashboard.setForeground(Color.WHITE);
            btnDashboard.setBorderPainted(false);
            btnDashboard.setFocusPainted(false);
            btnDashboard.setPreferredSize(new Dimension(140, 35));
            btnDashboard.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnDashboard.addActionListener(e -> showAdminDashboard());
            btnDashboard.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    btnDashboard.setBackground(new Color(24, 129, 24));
                }
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    btnDashboard.setBackground(new Color(34, 139, 34));
                }
            });
            leftPanel.add(Box.createHorizontalStrut(10));
            leftPanel.add(btnDashboard);
        }
        
        // Right panel với các nút thao tác, search, user info và logout
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);
        
        // Các nút thao tác
        JButton btnRefresh = new JButton("Làm mới");
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRefresh.setBackground(new Color(30, 144, 255));
        btnRefresh.setForeground(Color.WHITE);
        btnRefresh.setBorderPainted(false);
        btnRefresh.setFocusPainted(false);
        btnRefresh.setPreferredSize(new Dimension(100, 35));
        btnRefresh.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> reload());
        btnRefresh.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnRefresh.setBackground(new Color(20, 134, 245));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnRefresh.setBackground(new Color(30, 144, 255));
            }
        });
        
        JButton btnSearch = new JButton("Tìm kiếm...");
        btnSearch.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSearch.setBackground(new Color(30, 144, 255));
        btnSearch.setForeground(Color.WHITE);
        btnSearch.setBorderPainted(false);
        btnSearch.setFocusPainted(false);
        btnSearch.setPreferredSize(new Dimension(120, 35));
        btnSearch.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSearch.addActionListener(e -> doSearchInput());
        btnSearch.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnSearch.setBackground(new Color(20, 134, 245));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnSearch.setBackground(new Color(30, 144, 255));
            }
        });
        
        JButton btnAdd = new JButton("Thêm văn bản...");
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnAdd.setBackground(new Color(34, 139, 34));
        btnAdd.setForeground(Color.WHITE);
        btnAdd.setBorderPainted(false);
        btnAdd.setFocusPainted(false);
        btnAdd.setPreferredSize(new Dimension(140, 35));
        btnAdd.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAdd.addActionListener(e -> doAdd());
        btnAdd.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnAdd.setBackground(new Color(24, 129, 24));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnAdd.setBackground(new Color(34, 139, 34));
            }
        });
        
        JButton btnExport = new JButton("Xuất...");
        btnExport.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnExport.setBackground(new Color(255, 152, 0));
        btnExport.setForeground(Color.WHITE);
        btnExport.setBorderPainted(false);
        btnExport.setFocusPainted(false);
        btnExport.setPreferredSize(new Dimension(100, 35));
        btnExport.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnExport.addActionListener(e -> doExport());
        btnExport.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnExport.setBackground(new Color(245, 142, 0));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnExport.setBackground(new Color(255, 152, 0));
            }
        });
        
        JButton btnDelete = new JButton("Xóa");
        btnDelete.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDelete.setBackground(new Color(220, 20, 60));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.setBorderPainted(false);
        btnDelete.setFocusPainted(false);
        btnDelete.setPreferredSize(new Dimension(100, 35));
        btnDelete.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDelete.addActionListener(e -> {
            Long id = selectedId();
            if (id != null) {
                doDelete(id);
            } else {
                info("Chọn một dòng trước");
            }
        });
        btnDelete.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnDelete.setBackground(new Color(200, 0, 40));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnDelete.setBackground(new Color(220, 20, 60));
            }
        });
        
        rightPanel.add(btnRefresh);
        rightPanel.add(btnSearch);
        rightPanel.add(btnAdd);
        rightPanel.add(btnExport);
        rightPanel.add(btnDelete);
        rightPanel.add(Box.createHorizontalStrut(10));
        
        // Search field với placeholder
        if (searchField == null) {
            searchField = new JTextField(20);
        } else {
            searchField.setText(""); // Clear any existing text
        }
        String searchPlaceholder = "Tìm kiếm văn bản...";
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 255, 255, 100), 2),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        searchField.setBackground(new Color(255, 255, 255, 200));
        searchField.setText(searchPlaceholder);
        searchField.setForeground(new Color(150, 150, 150));
        searchField.setToolTipText(searchPlaceholder);
        
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (searchField.getText().equals(searchPlaceholder)) {
                    searchField.setText("");
                    searchField.setForeground(new Color(33, 37, 41));
                }
            }
            
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (searchField.getText().trim().isEmpty()) {
                    searchField.setText(searchPlaceholder);
                    searchField.setForeground(new Color(150, 150, 150));
                }
            }
        });
        
        searchField.addActionListener(e -> {
            if (!searchField.getText().equals(searchPlaceholder)) {
                doSearch();
            }
        });
        
        rightPanel.add(searchField);
        
        // User info
        userInfoLabel = new JLabel(authService.getCurrentUser().username());
        userInfoLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userInfoLabel.setForeground(Color.WHITE);
        rightPanel.add(userInfoLabel);
        
        // Logout button
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogout.setBackground(new Color(220, 20, 60));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setBorderPainted(false);
        btnLogout.setFocusPainted(false);
        btnLogout.setPreferredSize(new Dimension(110, 35));
        btnLogout.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogout.addActionListener(e -> doLogout());
        btnLogout.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnLogout.setBackground(new Color(200, 0, 40));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnLogout.setBackground(new Color(220, 20, 60));
            }
        });
        rightPanel.add(btnLogout);
        
        top.add(leftPanel, BorderLayout.WEST);
        top.add(rightPanel, BorderLayout.EAST);
        
        return top;
    }
    
    private Color getRoleColor(Role role) {
        return switch (role) {
            case QUAN_TRI -> new Color(25, 42, 86);
            case VAN_THU -> new Color(34, 139, 34);
            case LANH_DAO_CAP_TREN -> new Color(255, 152, 0);
            case LANH_DAO_PHONG -> new Color(255, 193, 7);
            case CHANH_VAN_PHONG -> new Color(156, 39, 176);
            case CAN_BO_CHUYEN_MON -> new Color(33, 150, 243);
        };
    }
    
    private void styleTable(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(35);
        table.setSelectionBackground(new Color(30, 144, 255));
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(new Color(230, 230, 230));
        table.setShowGrid(true);
        
        // Header styling
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(25, 42, 86));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));
        table.getTableHeader().setReorderingAllowed(false);
        
        // Alternating row colors
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    if (row % 2 == 0) {
                        setBackground(Color.WHITE);
                    } else {
                        setBackground(new Color(250, 250, 250));
                    }
                }
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return this;
            }
        });
    }
    
    private void configureColumnWidths() {
        // ID
        int colId = model.findColumn("ID");
        table.getColumnModel().getColumn(colId).setPreferredWidth(60);
        table.getColumnModel().getColumn(colId).setMaxWidth(70);
        // Tiêu đề
        int colTitle = model.findColumn("Tiêu đề");
        table.getColumnModel().getColumn(colTitle).setPreferredWidth(360);
        // Trạng thái
        int colState = model.findColumn("Trạng thái");
        table.getColumnModel().getColumn(colState).setPreferredWidth(110);
        // Tạo lúc
        int colCreated = model.findColumn("Tạo lúc");
        table.getColumnModel().getColumn(colCreated).setPreferredWidth(170);
        // Độ ưu tiên
        int colPriority = model.findColumn("Độ ưu tiên");
        table.getColumnModel().getColumn(colPriority).setPreferredWidth(90);
        // Phân công
        int colAssign = model.findColumn("Phân công");
        table.getColumnModel().getColumn(colAssign).setPreferredWidth(150);
        // Ghi chú
        int colNote = model.findColumn("Ghi chú");
        table.getColumnModel().getColumn(colNote).setPreferredWidth(220);
        // Quy trình (nút ▼)
        int colFlow = model.findColumn("Quy trình");
        table.getColumnModel().getColumn(colFlow).setPreferredWidth(72);
        table.getColumnModel().getColumn(colFlow).setMaxWidth(84);
    }

    private JMenuBar buildMenuBar(Role currentRole) {
        JMenuBar menuBar = new JMenuBar();
        
        // Menu Quản trị (chỉ hiển thị cho QUAN_TRI)
        if (currentRole == Role.QUAN_TRI) {
            JMenu menuAdmin = new JMenu("Quản trị");
            JMenuItem miUserMgmt = new JMenuItem("Quản lý người dùng...");
            miUserMgmt.addActionListener(e -> {
                try {
                    var ur = new UserRepository(docService.getDataSource());
                    ur.migrate();
                    new UserManagementDialog(frame, ur).setVisible(true);
                } catch (Exception ex) { showError(ex); }
            });
            menuAdmin.add(miUserMgmt);
            menuBar.add(menuAdmin);
        }
        
        JMenu menuEmail = new JMenu("Email");
        JMenuItem miFetch = new JMenuItem("Nhận từ Gmail...");
        miFetch.addActionListener(e -> doEmail());
        menuEmail.add(miFetch);
        JMenuItem miPending = new JMenuItem("Email chờ xác nhận...");
        miPending.addActionListener(e -> showPendingEmails());
        menuEmail.add(miPending);
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
                String priority = d.priority() != null ? getPriorityDisplayName(d.priority()) : "Thường";
                String assignedTo = d.assignedTo() != null ? d.assignedTo() : "Chưa phân công";
                String note = d.note() != null ? d.note() : "";
                model.addRow(new Object[]{
                    d.id(), d.title(), getStateDisplayName(d.state().name()),
                    d.createdAt().toString().substring(0, 19),
                    priority, assignedTo, note, "▼"
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
                String priority = d.priority() != null ? getPriorityDisplayName(d.priority()) : "Thường";
                String assignedTo = d.assignedTo() != null ? d.assignedTo() : "Chưa phân công";
                String note = d.note() != null ? d.note() : "";
                model.addRow(new Object[]{
                    d.id(), d.title(), getStateDisplayName(d.state().name()),
                    d.createdAt().toString().substring(0, 19),
                    priority, assignedTo, note, "▼"
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
                String priority = d.priority() != null ? getPriorityDisplayName(d.priority()) : "Thường";
                String assignedTo = d.assignedTo() != null ? d.assignedTo() : "Chưa phân công";
                String note = d.note() != null ? d.note() : "";
                model.addRow(new Object[]{ d.id(), d.title(), getStateDisplayName(d.state().name()), d.createdAt().toString().substring(0, 19), priority, assignedTo, note, "▼" });
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
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));
            fc.setAcceptAllFileFilterUsed(false);
            if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            File file = fc.getSelectedFile();
            if (file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                JOptionPane.showMessageDialog(frame, "Vui lòng chọn tệp PDF (.pdf) hợp lệ.", "Sai định dạng", JOptionPane.WARNING_MESSAGE);
                return;
            }
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

    private void doDelete(long docId) {
        try {
            // Lấy thông tin văn bản để hiển thị trong dialog xác nhận
            var doc = docService.getDocumentById(docId);
            String title = doc != null ? doc.title() : "ID: " + docId;
            
            // Xác nhận xóa
            int result = JOptionPane.showConfirmDialog(
                frame,
                "Bạn có chắc chắn muốn xóa văn bản:\n\"" + title + "\"?\n\nHành động này không thể hoàn tác!",
                "Xác nhận xóa văn bản",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                docService.deleteDocument(docId);
                info("Đã xóa văn bản thành công");
                reload();
            }
        } catch (Exception ex) {
            showError(ex);
        }
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

    private void doTiepNhan() {
        Long id = selectedId(); if (id == null) { info("Chọn một dòng trước"); return; }
        JTextField actorField = new JTextField(authService.getCurrentUser().username(), 15);
        JTextField noteField = new JTextField(20);
        JPanel p = new JPanel(new GridLayout(2,2,6,6));
        p.add(new JLabel("Người thực hiện:")); p.add(actorField);
        p.add(new JLabel("Ghi chú:")); p.add(noteField);
        int ok = JOptionPane.showConfirmDialog(frame, p, "Tiếp nhận văn bản", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            try {
                workflowService.tiepNhan(id, actorField.getText().trim(), noteField.getText().trim());
                reload();
                info("Đã tiếp nhận văn bản");
            } catch (Exception ex) { showError(ex); }
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
                workflowService.chiDaoXuLy(id, actor, "System", fullNote);
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
                workflowService.chiDaoXuLy(id, actor, assignedTo, fullNote);
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
                workflowService.thucHienXuLy(id, actor, fullNote);
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
                workflowService.xetDuyet(id, actor, fullNote);
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
        infoPanel.add(new JLabel(getStateDisplayName(doc.state().name())), gbc);
        
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
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(400);
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
            EmailConfigDialog configDialog = new EmailConfigDialog(frame, emailService);
            configDialog.setVisible(true);
            
            if (!configDialog.isConfigSaved()) {
                return;
            }
            
            String email = configDialog.getEmail();
            String password = configDialog.getPassword();
            
            // Show progress dialog (chuẩn bị UI)
            JDialog progressDialog = new JDialog(frame, "Đang nhận văn bản từ email...", true);
            progressDialog.setSize(400, 150);
            progressDialog.setLocationRelativeTo(frame);
            JPanel progressPanel = new JPanel(new BorderLayout());
            progressPanel.add(new JLabel("Đang kết nối và nhận văn bản từ Gmail...", JLabel.CENTER), BorderLayout.CENTER);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressPanel.add(progressBar, BorderLayout.SOUTH);
            progressDialog.add(progressPanel);

            // Chạy nền bằng SwingWorker để không chặn EDT
            SwingWorker<Integer, Void> worker = new SwingWorker<>() {
                @Override
                protected Integer doInBackground() {
                    // Đọc thật và tạo văn bản
                    return emailService.fetchAndProcessEmails(email, password);
                }

                @Override
                protected void done() {
                    progressDialog.dispose();
                    try {
                        int count = get();
                        info("Đã nhận " + count + " văn bản từ email thành công!");
                        reload();
                    } catch (Exception e) {
                        showError(e);
                    }
                }
            };

            // Bắt đầu background rồi mới hiển thị dialog modal
            worker.execute();
            progressDialog.setVisible(true);
            
        } catch (Exception e) {
            showError(e);
        }
    }

    /**
     * Hiển thị dialog email chờ xác nhận
     */
    private void showPendingEmails() {
        try {
            String currentUser = authService.getCurrentUser().username();
            PendingEmailsDialog dialog = new PendingEmailsDialog(frame, pendingEmailService, currentUser);
            dialog.setVisible(true);
            // Reload danh sách văn bản sau khi đóng dialog (có thể đã tạo văn bản mới)
            reload();
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
            // Lấy trạng thái hiện tại của văn bản để hiển thị trong thông báo lỗi
            var doc = docService.getDocumentById(docId);
            String currentState = doc != null ? getStateDisplayName(doc.state().name()) : "Không xác định";
            
            switch (action) {
                case "DANG_KY":
                    String note1 = JOptionPane.showInputDialog(frame, "Ghi chú đăng ký:", "Đăng ký văn bản", JOptionPane.QUESTION_MESSAGE);
                    if (note1 == null) return; // Người dùng hủy
                    workflowService.dangKy(docId, actor, note1);
                    break;
                case "TRINH_LANH_DAO":
                    String note2 = JOptionPane.showInputDialog(frame, "Ghi chú trình lãnh đạo:", "Trình lãnh đạo", JOptionPane.QUESTION_MESSAGE);
                    if (note2 == null) return; // Người dùng hủy
                    workflowService.trinhLanhDao(docId, actor, note2);
                    break;
                case "CHI_DAO_XU_LY":
                    String assignedTo = JOptionPane.showInputDialog(frame, "Phân công cho ai:", "Chỉ đạo xử lý", JOptionPane.QUESTION_MESSAGE);
                    if (assignedTo != null && !assignedTo.trim().isEmpty()) {
                        String note3 = JOptionPane.showInputDialog(frame, "Hướng dẫn xử lý:", "Chỉ đạo xử lý", JOptionPane.QUESTION_MESSAGE);
                        if (note3 == null) return; // Người dùng hủy
                        workflowService.chiDaoXuLy(docId, actor, assignedTo, note3);
                    }
                    break;
                case "THUC_HIEN_XU_LY":
                    String note4 = JOptionPane.showInputDialog(frame, "Báo cáo kết quả xử lý:", "Thực hiện xử lý", JOptionPane.QUESTION_MESSAGE);
                    if (note4 == null) return; // Người dùng hủy
                    workflowService.thucHienXuLy(docId, actor, note4);
                    break;
                case "PHAN_CONG_CAN_BO":
                    String assignedTo2 = JOptionPane.showInputDialog(frame, "Phân công cho cán bộ:", "Phân công cán bộ", JOptionPane.QUESTION_MESSAGE);
                    if (assignedTo2 != null && !assignedTo2.trim().isEmpty()) {
                        String note4b = JOptionPane.showInputDialog(frame, "Hướng dẫn xử lý:", "Phân công cán bộ", JOptionPane.QUESTION_MESSAGE);
                        if (note4b == null) return; // Người dùng hủy
                        workflowService.phanCongCanBo(docId, actor, assignedTo2, note4b);
                    }
                    break;
                case "XET_DUYET":
                    String note5 = JOptionPane.showInputDialog(frame, "Ghi chú duyệt:", "Xét duyệt", JOptionPane.QUESTION_MESSAGE);
                    if (note5 == null) return; // Người dùng hủy
                    workflowService.xetDuyet(docId, actor, note5);
                    break;
            }
            
            info("Thực hiện " + getActionDisplayName(action) + " thành công!");
            reload();
            
        } catch (IllegalStateException e) {
            // Hiển thị thông báo lỗi thân thiện hơn cho lỗi trạng thái
            String currentState = "Không xác định";
            try {
                var doc = docService.getDocumentById(docId);
                if (doc != null) {
                    currentState = getStateDisplayName(doc.state().name());
                }
            } catch (Exception ex) {
                // Nếu không lấy được trạng thái, giữ nguyên "Không xác định"
            }
            String message = e.getMessage() + "\n\nTrạng thái hiện tại của văn bản: " + currentState;
            JOptionPane.showMessageDialog(frame, message, "Không thể thực hiện thao tác", JOptionPane.WARNING_MESSAGE);
        } catch (SecurityException e) {
            // Lỗi quyền truy cập
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Không có quyền thực hiện", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            showError(e);
        }
    }

    public void show() { frame.setVisible(true); }

    private String getStateDisplayName(String state) {
        if (state == null) return "";
        return switch (state) {
            case "TIEP_NHAN" -> "Tiếp nhận";
            case "DANG_KY" -> "Đăng ký";
            case "CHO_XEM_XET" -> "Chờ xem xét";
            case "DA_PHAN_CONG" -> "Đã phân công";
            case "DANG_XU_LY" -> "Đang xử lý";
            case "HOAN_THANH" -> "Hoàn thành";
            default -> state;
        };
    }

    private String getActionDisplayName(String action) {
        if (action == null) return "";
        return switch (action) {
            case "DANG_KY" -> "Đăng ký";
            case "TRINH_LANH_DAO" -> "Trình lãnh đạo";
            case "CHI_DAO_XU_LY" -> "Chỉ đạo xử lý";
            case "PHAN_CONG_CAN_BO" -> "Phân công cán bộ";
            case "THUC_HIEN_XU_LY" -> "Thực hiện xử lý";
            case "XET_DUYET" -> "Xét duyệt";
            default -> action;
        };
    }

    // Renderer cho nút trong bảng
    private static class ButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
            setMargin(new Insets(0,0,0,0));
            setFocusable(false);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText("▼");
            setPreferredSize(new Dimension(24, Math.max(18, table.getRowHeight()-8)));
            return this;
        }
    }

    // Editor cho nút trong bảng, khi click mở menu chọn quy trình
    private class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private Long currentDocId;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);
            button.setMargin(new Insets(0,0,0,0));
            button.setFocusable(false);
            button.addActionListener(e -> {
                // Hiển thị menu ngay trên nút; không dừng editing trước để tránh invoker chưa hiển thị
                SwingUtilities.invokeLater(() -> showWorkflowMenu(button, currentDocId));
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            button.setText("▼");
            Object idVal = model.getValueAt(row, 0);
            currentDocId = (idVal instanceof Number) ? ((Number) idVal).longValue() : Long.parseLong(String.valueOf(idVal));
            return button;
        }

        @Override
        public Object getCellEditorValue() { return button.getText(); }
    }

    private void showWorkflowMenu(Component invoker, long docId) {
        JPopupMenu menu = new JPopupMenu();
        Role currentRole = authService.getCurrentUser().role();
        if (currentRole == Role.VAN_THU) {
            JMenuItem m2 = new JMenuItem("1) Đăng ký");
            m2.addActionListener(e -> { selectRowById(docId); doWorkflowAction("DANG_KY"); });
            JMenuItem m3 = new JMenuItem("2) Trình lãnh đạo");
            m3.addActionListener(e -> { selectRowById(docId); doWorkflowAction("TRINH_LANH_DAO"); });
            menu.add(m2); menu.add(m3);
        } else if (currentRole == Role.LANH_DAO_CAP_TREN) {
            JMenuItem m4 = new JMenuItem("1) Chỉ đạo xử lý");
            m4.addActionListener(e -> { selectRowById(docId); doWorkflowAction("CHI_DAO_XU_LY"); });
            menu.add(m4);
        } else if (currentRole == Role.LANH_DAO_PHONG) {
            JMenuItem m4b = new JMenuItem("1) Phân công cán bộ");
            m4b.addActionListener(e -> { selectRowById(docId); doWorkflowAction("PHAN_CONG_CAN_BO"); });
            JMenuItem m6 = new JMenuItem("2) Xét duyệt");
            m6.addActionListener(e -> { selectRowById(docId); doWorkflowAction("XET_DUYET"); });
            menu.add(m4b); menu.add(m6);
        } else if (currentRole == Role.CAN_BO_CHUYEN_MON) {
            JMenuItem m5 = new JMenuItem("1) Thực hiện xử lý");
            m5.addActionListener(e -> { selectRowById(docId); doWorkflowAction("THUC_HIEN_XU_LY"); });
            menu.add(m5);
        } else if (currentRole == Role.QUAN_TRI) {
            // QUAN_TRI có tất cả menu
            JMenuItem m2 = new JMenuItem("1) Đăng ký");
            m2.addActionListener(e -> { selectRowById(docId); doWorkflowAction("DANG_KY"); });
            JMenuItem m3 = new JMenuItem("2) Trình lãnh đạo");
            m3.addActionListener(e -> { selectRowById(docId); doWorkflowAction("TRINH_LANH_DAO"); });
            JMenuItem m4 = new JMenuItem("3) Chỉ đạo xử lý");
            m4.addActionListener(e -> { selectRowById(docId); doWorkflowAction("CHI_DAO_XU_LY"); });
            JMenuItem m4b = new JMenuItem("4) Phân công cán bộ");
            m4b.addActionListener(e -> { selectRowById(docId); doWorkflowAction("PHAN_CONG_CAN_BO"); });
            JMenuItem m5 = new JMenuItem("5) Thực hiện xử lý");
            m5.addActionListener(e -> { selectRowById(docId); doWorkflowAction("THUC_HIEN_XU_LY"); });
            JMenuItem m6 = new JMenuItem("6) Xét duyệt");
            m6.addActionListener(e -> { selectRowById(docId); doWorkflowAction("XET_DUYET"); });
            menu.add(m2); menu.add(m3);
            menu.add(m4); menu.add(m4b); menu.add(m5); menu.add(m6);
        }
        // CHANH_VAN_PHONG: Không có menu xử lý (chỉ xem, giám sát)
        
        if (!invoker.isShowing()) {
            // fallback: hiển thị tương đối tại (0,0) của bảng để tránh IllegalComponentStateException
            Component fallback = table;
            menu.show(fallback, 10, 10);
        } else {
            menu.show(invoker, 0, invoker.getHeight());
        }
    }

    private void selectRowById(long docId) {
        for (int i = 0; i < model.getRowCount(); i++) {
            Object v = model.getValueAt(i, 0);
            long id = (v instanceof Number) ? ((Number) v).longValue() : Long.parseLong(String.valueOf(v));
            if (id == docId) {
                table.getSelectionModel().setSelectionInterval(i, i);
                break;
            }
        }
    }

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


