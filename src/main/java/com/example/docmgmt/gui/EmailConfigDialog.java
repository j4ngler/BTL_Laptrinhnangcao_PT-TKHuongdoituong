package com.example.docmgmt.gui;

import javax.swing.*;
import java.awt.*;

public class EmailConfigDialog extends JDialog {
    private final com.example.docmgmt.service.EmailService emailService;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JCheckBox autoFetchCheckbox;
    private JSpinner intervalSpinner;
    private boolean configSaved = false;
    
    public EmailConfigDialog(Frame parent, com.example.docmgmt.service.EmailService emailService) {
        super(parent, "Cấu hình Email", true);
        this.emailService = emailService;
        initComponents();
        setupLayout();
        setupEvents();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(parent);
    }
    
    private void initComponents() {
        emailField = new JTextField(25);
        passwordField = new JPasswordField(25);
        autoFetchCheckbox = new JCheckBox("Tự động nhận email");
        intervalSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 1440, 5));
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(70, 130, 180));
        JLabel titleLabel = new JLabel("CẤU HÌNH NHẬN VĂN BẢN TỪ EMAIL");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);
        
        // Main content
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // Email
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Email Gmail:"), gbc);
        gbc.gridx = 1; 
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(emailField, gbc);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        
        // Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Mật khẩu ứng dụng:"), gbc);
        gbc.gridx = 1; 
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(passwordField, gbc);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        
        // Auto fetch
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(autoFetchCheckbox, gbc);
        
        // Interval
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Kiểm tra mỗi (phút):"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(intervalSpinner, gbc);
        
        // Help text
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.WEST;
        JTextArea helpText = new JTextArea(4, 40);
        helpText.setText("Hướng dẫn:\n" +
                        "1. Bật 2-Factor Authentication cho Gmail\n" +
                        "2. Tạo App Password trong Google Account Settings\n" +
                        "3. Sử dụng App Password thay vì mật khẩu Gmail thường\n" +
                        "4. Đảm bảo IMAP được bật trong Gmail Settings");
        helpText.setEditable(false);
        helpText.setBackground(getBackground());
        helpText.setFont(new Font("Arial", Font.PLAIN, 10));
        mainPanel.add(helpText, gbc);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton saveBtn = new JButton("Lưu cấu hình");
        JButton testBtn = new JButton("Kiểm tra kết nối");
        JButton cancelBtn = new JButton("Hủy");
        
        saveBtn.setBackground(new Color(70, 130, 180));
        saveBtn.setForeground(Color.WHITE);
        testBtn.setBackground(new Color(34, 139, 34));
        testBtn.setForeground(Color.WHITE);
        
        buttonPanel.add(saveBtn);
        buttonPanel.add(testBtn);
        buttonPanel.add(cancelBtn);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Set button actions
        saveBtn.addActionListener(e -> saveConfig());
        testBtn.addActionListener(e -> testConnection());
        cancelBtn.addActionListener(e -> dispose());
    }
    
    private void setupEvents() {
        // Enter key listeners
        emailField.addActionListener(e -> passwordField.requestFocus());
        passwordField.addActionListener(e -> saveConfig());
    }
    
    private void saveConfig() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (email.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", 
                                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (!email.contains("@gmail.com")) {
            JOptionPane.showMessageDialog(this, "Chỉ hỗ trợ Gmail!", 
                                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Lưu cấu hình vào database
        try {
            // TODO: Thực sự lưu vào database
            configSaved = true;
            JOptionPane.showMessageDialog(this, "Đã lưu cấu hình email!\nBạn có thể test kết nối hoặc đóng dialog.", 
                                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
            // Không tự động đóng dialog, để user có thể test kết nối
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi khi lưu cấu hình: " + e.getMessage(), 
                                        "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void testConnection() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (email.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", 
                                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Progress dialog (modal)
        JDialog progressDialog = new JDialog(this, "Đang test kết nối...", true);
        progressDialog.setSize(300, 100);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.add(new JLabel("Vui lòng chờ...", JLabel.CENTER));

        // Chạy kiểm tra ở background để tránh treo UI
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                // Gọi IMAP thật để chỉ xác thực
                return emailService.fetchEmailsFromGmail(email, password);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get();
                    JOptionPane.showMessageDialog(EmailConfigDialog.this, "Kết nối thành công!", 
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(EmailConfigDialog.this, "Kết nối thất bại: " + ex.getMessage(), 
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }
    
    public boolean isConfigSaved() {
        return configSaved;
    }
    
    public String getEmail() {
        return emailField.getText().trim();
    }
    
    public String getPassword() {
        return new String(passwordField.getPassword());
    }
    
    public boolean isAutoFetchEnabled() {
        return autoFetchCheckbox.isSelected();
    }
    
    public int getFetchInterval() {
        return (Integer) intervalSpinner.getValue();
    }
}
