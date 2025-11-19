package com.example.docmgmt.gui;

import com.example.docmgmt.repo.PendingEmailRepository;
import com.example.docmgmt.service.PendingEmailService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Dialog để xem và xác nhận email chờ xác nhận
 */
public class PendingEmailsDialog extends JDialog {
    private final PendingEmailService pendingEmailService;
    private final String currentUser;
    private DefaultTableModel tableModel;
    private JTable table;
    private JTextArea contentArea;
    private JLabel attachmentLabel;
    private JButton approveBtn;
    private JButton rejectBtn;
    private JButton viewAttachmentBtn;
    private PendingEmailRepository.PendingEmail selectedEmail;

    public PendingEmailsDialog(Frame parent, PendingEmailService pendingEmailService, String currentUser) {
        super(parent, "Email chờ xác nhận", true);
        this.pendingEmailService = pendingEmailService;
        this.currentUser = currentUser;
        initComponents();
        setupLayout();
        loadPendingEmails();
    }

    private void initComponents() {
        // Table model
        tableModel = new DefaultTableModel(new Object[]{"ID", "Subject", "From", "Ngày nhận"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onEmailSelected();
            }
        });

        // Content area
        contentArea = new JTextArea(10, 50);
        contentArea.setEditable(false);
        contentArea.setWrapStyleWord(true);
        contentArea.setLineWrap(true);

        // Attachment label
        attachmentLabel = new JLabel("Không có attachment");

        // Buttons
        approveBtn = new JButton("Xác nhận");
        approveBtn.setBackground(new Color(34, 139, 34));
        approveBtn.setForeground(Color.WHITE);
        approveBtn.addActionListener(e -> approveEmail());

        rejectBtn = new JButton("Từ chối");
        rejectBtn.setBackground(new Color(220, 20, 60));
        rejectBtn.setForeground(Color.WHITE);
        rejectBtn.addActionListener(e -> rejectEmail());

        viewAttachmentBtn = new JButton("Xem attachment");
        viewAttachmentBtn.addActionListener(e -> viewAttachment());
    }

    private void setupLayout() {
        setLayout(new BorderLayout());
        setSize(900, 700);
        setLocationRelativeTo(getParent());

        // Top panel - Table
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Danh sách email chờ xác nhận"));
        topPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Center panel - Email details
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Chi tiết email"));

        JPanel detailsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Subject
        gbc.gridx = 0; gbc.gridy = 0;
        detailsPanel.add(new JLabel("Subject:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField subjectField = new JTextField();
        subjectField.setEditable(false);
        detailsPanel.add(subjectField, gbc);

        // From
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailsPanel.add(new JLabel("From:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField fromField = new JTextField();
        fromField.setEditable(false);
        detailsPanel.add(fromField, gbc);

        // Content
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailsPanel.add(new JLabel("Nội dung:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        detailsPanel.add(new JScrollPane(contentArea), gbc);

        // Attachments
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.weighty = 0;
        detailsPanel.add(new JLabel("Attachments:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JPanel attachmentPanel = new JPanel(new BorderLayout());
        attachmentPanel.add(attachmentLabel, BorderLayout.WEST);
        attachmentPanel.add(viewAttachmentBtn, BorderLayout.EAST);
        detailsPanel.add(attachmentPanel, gbc);

        // Store references for updates
        this.addPropertyChangeListener("selectedEmail", evt -> {
            PendingEmailRepository.PendingEmail email = (PendingEmailRepository.PendingEmail) evt.getNewValue();
            if (email != null) {
                subjectField.setText(email.subject());
                fromField.setText(email.fromEmail());
                contentArea.setText(email.emailContent() != null ? email.emailContent() : "");
                
                if (email.attachmentFileIds() != null && email.attachmentFileIds().length > 0) {
                    attachmentLabel.setText(email.attachmentFileIds().length + " file(s)");
                    viewAttachmentBtn.setEnabled(true);
                } else {
                    attachmentLabel.setText("Không có attachment");
                    viewAttachmentBtn.setEnabled(false);
                }
            }
        });

        centerPanel.add(detailsPanel, BorderLayout.CENTER);

        // Bottom panel - Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout());
        bottomPanel.add(approveBtn);
        bottomPanel.add(rejectBtn);
        bottomPanel.add(new JLabel("  "));
        bottomPanel.add(new JButton("Làm mới") {{
            addActionListener(e -> loadPendingEmails());
        }});
        bottomPanel.add(new JButton("Đóng") {{
            addActionListener(e -> {
                try {
                    int count = pendingEmailService.listPending().size();
                    String message = count > 0 
                        ? "Hiện có " + count + " email đang chờ xử lý."
                        : "Không có email nào đang chờ xử lý.";
                    JOptionPane.showMessageDialog(PendingEmailsDialog.this, message,
                        "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                } catch (SQLException ex) {
                    // Nếu có lỗi, vẫn đóng dialog
                    System.err.println("Lỗi đếm email chờ xử lý: " + ex.getMessage());
                }
                dispose();
            });
        }});

        // Add panels
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadPendingEmails() {
        try {
            List<PendingEmailRepository.PendingEmail> emails = pendingEmailService.listPending();
            tableModel.setRowCount(0);
            
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            
            for (PendingEmailRepository.PendingEmail email : emails) {
                String dateStr = email.receivedAt() != null 
                    ? sdf.format(java.util.Date.from(email.receivedAt().toInstant()))
                    : "";
                tableModel.addRow(new Object[]{
                    email.id(),
                    email.subject(),
                    email.fromEmail(),
                    dateStr
                });
            }
            
            if (emails.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Không có email nào chờ xác nhận", 
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Lỗi tải danh sách email: " + e.getMessage(),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEmailSelected() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            long id = ((Number) tableModel.getValueAt(row, 0)).longValue();
            try {
                selectedEmail = pendingEmailService.getById(id);
                firePropertyChange("selectedEmail", null, selectedEmail);
                approveBtn.setEnabled(true);
                rejectBtn.setEnabled(true);
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Lỗi tải chi tiết email: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void approveEmail() {
        if (selectedEmail == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn email để xác nhận",
                "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Hiển thị dialog để người dùng chọn phân loại và mức độ mật
        DocumentClassificationDialog classificationDialog = new DocumentClassificationDialog(this);
        
        classificationDialog.setVisible(true);
        
        if (!classificationDialog.isConfirmed()) {
            return; // Người dùng hủy
        }
        
        String classification = classificationDialog.getClassification();
        String securityLevel = classificationDialog.getSecurityLevel();
        String priority = classificationDialog.getPriority();
        
        try {
            pendingEmailService.approveEmail(selectedEmail.id(), currentUser, 
                classification, securityLevel, priority);
            JOptionPane.showMessageDialog(this, "Đã xác nhận email và tạo văn bản thành công!",
                "Thành công", JOptionPane.INFORMATION_MESSAGE);
            loadPendingEmails();
            selectedEmail = null;
            contentArea.setText("");
            attachmentLabel.setText("Không có attachment");
            approveBtn.setEnabled(false);
            rejectBtn.setEnabled(false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi xác nhận email: " + e.getMessage(),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void rejectEmail() {
        if (selectedEmail == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn email để từ chối",
                "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String note = JOptionPane.showInputDialog(this,
            "Nhập lý do từ chối (tùy chọn):",
            "Từ chối email", JOptionPane.QUESTION_MESSAGE);
        
        if (note == null) {
            return; // User cancelled
        }

        try {
            pendingEmailService.rejectEmail(selectedEmail.id(), currentUser, note);
            JOptionPane.showMessageDialog(this, "Đã từ chối email thành công!",
                "Thành công", JOptionPane.INFORMATION_MESSAGE);
            loadPendingEmails();
            selectedEmail = null;
            contentArea.setText("");
            attachmentLabel.setText("Không có attachment");
            approveBtn.setEnabled(false);
            rejectBtn.setEnabled(false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi từ chối email: " + e.getMessage(),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void viewAttachment() {
        if (selectedEmail == null || selectedEmail.attachmentFileIds() == null || 
            selectedEmail.attachmentFileIds().length == 0) {
            JOptionPane.showMessageDialog(this, "Email này không có attachment",
                "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Show dialog với danh sách attachments và nội dung
        JDialog attachmentDialog = new JDialog(this, "Xem attachment", true);
        attachmentDialog.setSize(800, 600);
        attachmentDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout());
        
        // List attachments
        JList<String> attachmentList = new JList<>(
            selectedEmail.attachmentFileIds());
        attachmentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JTextArea contentArea = new JTextArea(20, 60);
        contentArea.setEditable(false);
        contentArea.setWrapStyleWord(true);
        contentArea.setLineWrap(true);

        attachmentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String fileId = attachmentList.getSelectedValue();
                if (fileId != null) {
                    String content = pendingEmailService.readAttachmentContent(fileId);
                    contentArea.setText(content);
                }
            }
        });

        panel.add(new JLabel("Chọn file để xem:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(attachmentList), BorderLayout.WEST);
        panel.add(new JScrollPane(contentArea), BorderLayout.CENTER);
        panel.add(new JButton("Đóng") {{
            addActionListener(e -> attachmentDialog.dispose());
        }}, BorderLayout.SOUTH);

        attachmentDialog.add(panel);
        attachmentDialog.setVisible(true);
    }
}

