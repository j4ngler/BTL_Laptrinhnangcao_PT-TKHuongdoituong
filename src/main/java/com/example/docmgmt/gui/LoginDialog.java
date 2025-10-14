package com.example.docmgmt.gui;

import com.example.docmgmt.domain.Models.Role;
import com.example.docmgmt.service.AuthenticationService;

import javax.swing.*;
import java.awt.*;

public class LoginDialog extends JDialog {
    private AuthenticationService authService;
    private boolean loginSuccess = false;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JComboBox<Role> roleCombo;

    public LoginDialog(Frame parent, AuthenticationService authService) {
        super(parent, "Đăng nhập hệ thống", true);
        this.authService = authService;
        initComponents();
        setupLayout();
        setupEvents();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);
        roleCombo = new JComboBox<>(Role.values());
        
        // Set default role
        roleCombo.setSelectedItem(Role.VAN_THU);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(70, 130, 180));
        JLabel titleLabel = new JLabel("HỆ THỐNG QUẢN LÝ VĂN BẢN ĐẾN");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        // Main content
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Username
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(passwordField, gbc);

        // Role
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Vai trò:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(roleCombo, gbc);

        add(mainPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton loginBtn = new JButton("Đăng nhập");
        JButton registerBtn = new JButton("Đăng ký");
        JButton cancelBtn = new JButton("Hủy");
        
        loginBtn.setBackground(new Color(70, 130, 180));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setPreferredSize(new Dimension(100, 30));
        
        cancelBtn.setPreferredSize(new Dimension(100, 30));
        
        buttonPanel.add(loginBtn);
        buttonPanel.add(registerBtn);
        buttonPanel.add(cancelBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // Set button actions
        loginBtn.addActionListener(e -> performLogin());
        cancelBtn.addActionListener(e -> dispose());
        registerBtn.addActionListener(e -> openRegister());
        
        // Enter key to login
        getRootPane().setDefaultButton(loginBtn);
    }

    private void setupEvents() {
        // Enter key listeners
        usernameField.addActionListener(e -> passwordField.requestFocus());
        passwordField.addActionListener(e -> performLogin());
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        Role selectedRole = (Role) roleCombo.getSelectedItem();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", 
                                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (authService.login(username, password)) {
            // Kiểm tra vai trò
            if (authService.hasRole(selectedRole)) {
                loginSuccess = true;
                JOptionPane.showMessageDialog(this, 
                    "Đăng nhập thành công!\nVai trò: " + authService.getCurrentUserRoleName(), 
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Người dùng không có quyền truy cập với vai trò này!", 
                    "Lỗi quyền truy cập", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, 
                "Tên đăng nhập hoặc mật khẩu không đúng!", 
                "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openRegister() {
        RegisterDialog rd = new RegisterDialog(this, authService);
        rd.setVisible(true);
        if (rd.isSuccess()) {
            JOptionPane.showMessageDialog(this, "Bạn có thể đăng nhập bằng tài khoản vừa đăng ký.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public boolean isLoginSuccessful() {
        return loginSuccess;
    }

    public static void main(String[] args) {
        // Test dialog
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Mock authentication service for testing
            AuthenticationService mockAuth = new AuthenticationService(null) {
                @Override
                public boolean login(String username, String password) {
                    return "admin".equals(username) && "123".equals(password);
                }
                
                @Override
                public boolean hasRole(Role role) {
                    return true;
                }
                
                @Override
                public String getCurrentUserRoleName() {
                    return "Văn thư";
                }
            };
            
            LoginDialog dialog = new LoginDialog(null, mockAuth);
            dialog.setVisible(true);
        });
    }
}
