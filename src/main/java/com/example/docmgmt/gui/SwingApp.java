package com.example.docmgmt.gui;

import com.example.docmgmt.config.Config;
import com.example.docmgmt.domain.Models;
import com.example.docmgmt.domain.Models.Document;
import com.example.docmgmt.domain.Models.AuditLog;
import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.UserRepository;
import com.example.docmgmt.service.DocumentService;
import com.example.docmgmt.service.WorkflowService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class SwingApp {
    private final DocumentService docService;
    private final WorkflowService workflowService;
    private final JFrame frame;
    private final JTable table;
    private final DefaultTableModel model;
    private final JTextField searchField;

    public SwingApp() throws Exception {
        Config config = Config.fromEnv();
        this.docService = new DocumentService(config);
        var repo = new DocumentRepository(config.dataSource);
        var ur = new UserRepository(config.dataSource); ur.migrate();
        this.workflowService = new WorkflowService(repo, ur);

        frame = new JFrame("Quản lý văn bản (Swing)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(900, 520);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAdd = new JButton("Thêm");
        JButton btnExport = new JButton("Xuất");
        JButton btnRefresh = new JButton("Làm mới");
        JButton btnSubmit = new JButton("Submit");
        JButton btnClassify = new JButton("Classify");
        JButton btnApprove = new JButton("Approve");
        JButton btnIssue = new JButton("Issue");
        JButton btnArchive = new JButton("Archive");
        JButton btnDetails = new JButton("Chi tiết");
        searchField = new JTextField(20);
        JButton btnSearch = new JButton("Tìm");
        top.add(btnAdd); top.add(btnExport); top.add(btnDetails); top.add(new JSeparator(SwingConstants.VERTICAL));
        top.add(btnRefresh); top.add(new JSeparator(SwingConstants.VERTICAL));
        top.add(btnSubmit); top.add(btnClassify); top.add(btnApprove); top.add(btnIssue); top.add(btnArchive);
        top.add(new JSeparator(SwingConstants.VERTICAL)); top.add(searchField); top.add(btnSearch);

        model = new DefaultTableModel(new Object[]{"ID","Tiêu đề","Trạng thái","Tạo lúc","Số/VB"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(table);

        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(scroll, BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> reload());
        btnSearch.addActionListener(e -> doSearch());
        btnAdd.addActionListener(e -> doAdd());
        btnExport.addActionListener(e -> doExport());
        btnDetails.addActionListener(e -> doDetails());
        btnSubmit.addActionListener(e -> doTransition("SUBMIT"));
        btnClassify.addActionListener(e -> doTransition("CLASSIFY"));
        btnApprove.addActionListener(e -> doTransition("APPROVE"));
        btnIssue.addActionListener(e -> doTransition("ISSUE"));
        btnArchive.addActionListener(e -> doTransition("ARCHIVE"));

        reload();
    }

    private void reload() {
        try {
            List<Models.Document> docs = docService.listDocuments();
            model.setRowCount(0);
            for (var d : docs) {
                String so = ""; // sẽ load số từ DB khi cần; hiển thị trống nếu chưa ban hành
                model.addRow(new Object[]{ d.id(), d.title(), d.state().name(), d.createdAt().toString(), so });
            }
        } catch (Exception ex) { showError(ex); }
    }

    private void doSearch() {
        try {
            String kw = searchField.getText();
            List<Models.Document> docs = (kw == null || kw.isBlank()) ? docService.listDocuments() : docService.searchByTitle(kw);
            model.setRowCount(0);
            for (var d : docs) {
                String so = "";
                model.addRow(new Object[]{ d.id(), d.title(), d.state().name(), d.createdAt().toString(), so });
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
                workflowService.classify(id, actor, fullNote);
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
                workflowService.submit(id, actor, note);
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
            String decision = decisionApprove.isSelected() ? "duyệt" : "từ chối";
            String note = noteField.getText().trim();
            
            if (actor.isEmpty()) {
                info("Nhập người duyệt");
                return;
            }
            
            String fullNote = decision + " - " + note;
            try {
                workflowService.approve(id, actor, fullNote);
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
                workflowService.issue(id, actor, fullNote);
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
                workflowService.archive(id, actor, fullNote);
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

    public void show() { frame.setVisible(true); }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            try {
                new SwingApp().show();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, e.getMessage());
            }
        });
    }
}


