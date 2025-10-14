package com.example.docmgmt.gui;

import com.example.docmgmt.domain.Models.Role;
import com.example.docmgmt.service.AuthenticationService;

import javax.swing.*;
import java.awt.*;

public class RegisterDialog extends JDialog {
    private final AuthenticationService authService;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JComboBox<Role> roleCombo;
    private boolean success = false;

    public RegisterDialog(Dialog owner, AuthenticationService authService) {
        super(owner, "Đăng ký tài khoản", true);
        this.authService = authService;
        init();
    }

    private void init() {
        setSize(380, 230);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.anchor = GridBagConstraints.EAST;

        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);
        roleCombo = new JComboBox<>(Role.values());
        roleCombo.setSelectedItem(Role.VAN_THU);

        gbc.gridx=0; gbc.gridy=0; panel.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx=1; gbc.anchor = GridBagConstraints.WEST; panel.add(usernameField, gbc);
        gbc.gridx=0; gbc.gridy=1; gbc.anchor = GridBagConstraints.EAST; panel.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx=1; gbc.anchor = GridBagConstraints.WEST; panel.add(passwordField, gbc);
        gbc.gridx=0; gbc.gridy=2; gbc.anchor = GridBagConstraints.EAST; panel.add(new JLabel("Vai trò:"), gbc);
        gbc.gridx=1; gbc.anchor = GridBagConstraints.WEST; panel.add(roleCombo, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOk = new JButton("Đăng ký");
        JButton btnCancel = new JButton("Hủy");
        buttons.add(btnOk); buttons.add(btnCancel);

        add(panel, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        btnOk.addActionListener(e -> doRegister());
        btnCancel.addActionListener(e -> dispose());
    }

    private void doRegister() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        Role role = (Role) roleCombo.getSelectedItem();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nhập đầy đủ thông tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        boolean ok = authService.registerUser(username, password, role);
        if (ok) {
            success = true;
            JOptionPane.showMessageDialog(this, "Đăng ký thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Đăng ký thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSuccess() { return success; }
}


