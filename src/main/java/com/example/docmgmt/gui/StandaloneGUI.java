package com.example.docmgmt.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class StandaloneGUI {
    public static void main(String[] args) {
        System.out.println("Starting GUI...");
        
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        JFrame frame = new JFrame("Quản lý văn bản - Standalone");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        
        // Top panel with buttons
        JPanel topPanel = new JPanel(new FlowLayout());
        
        JButton btnAdd = new JButton("Thêm");
        JButton btnExport = new JButton("Xuất");
        JButton btnRefresh = new JButton("Làm mới");
        JButton btnSubmit = new JButton("Submit");
        JButton btnClassify = new JButton("Classify");
        JButton btnApprove = new JButton("Approve");
        JButton btnIssue = new JButton("Issue");
        JButton btnArchive = new JButton("Archive");
        JButton btnDetails = new JButton("Chi tiết");
        
        topPanel.add(btnAdd);
        topPanel.add(btnExport);
        topPanel.add(btnDetails);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(btnRefresh);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(btnSubmit);
        topPanel.add(btnClassify);
        topPanel.add(btnApprove);
        topPanel.add(btnIssue);
        topPanel.add(btnArchive);
        
        // Table
        String[] columns = {"ID", "Tiêu đề", "Trạng thái", "Tạo lúc", "Số/VB", "Thời hạn", "Độ ưu tiên", "Phân công"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable table = new JTable(model);
        
        // Add sample data
        model.addRow(new Object[]{1, "Văn bản mẫu 1", "DRAFT", "2024-01-01 10:00", "001/2024", "", "Thường", "Chưa phân công"});
        model.addRow(new Object[]{2, "Văn bản mẫu 2", "SUBMITTED", "2024-01-02 11:00", "002/2024", "", "Khẩn", "Người A"});
        model.addRow(new Object[]{3, "Văn bản mẫu 3", "APPROVED", "2024-01-03 12:00", "003/2024", "2024-01-10", "Thượng khẩn", "Người B"});
        
        JScrollPane scrollPane = new JScrollPane(table);
        
        // Layout
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        
        // Event listeners
        btnAdd.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame, "Tính năng thêm văn bản", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });
        
        btnExport.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame, "Tính năng xuất văn bản", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });
        
        btnRefresh.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame, "Làm mới danh sách", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });
        
        btnSubmit.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                JOptionPane.showMessageDialog(frame, "Submit văn bản ID: " + table.getValueAt(selectedRow, 0), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Vui lòng chọn một văn bản", "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        btnClassify.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                JOptionPane.showMessageDialog(frame, "Phân loại văn bản ID: " + table.getValueAt(selectedRow, 0), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Vui lòng chọn một văn bản", "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        btnApprove.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                JOptionPane.showMessageDialog(frame, "Phê duyệt văn bản ID: " + table.getValueAt(selectedRow, 0), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Vui lòng chọn một văn bản", "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        btnIssue.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                JOptionPane.showMessageDialog(frame, "Ban hành văn bản ID: " + table.getValueAt(selectedRow, 0), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Vui lòng chọn một văn bản", "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        btnArchive.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                JOptionPane.showMessageDialog(frame, "Lưu trữ văn bản ID: " + table.getValueAt(selectedRow, 0), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Vui lòng chọn một văn bản", "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        btnDetails.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                StringBuilder details = new StringBuilder();
                details.append("Chi tiết văn bản:\n");
                for (int i = 0; i < columns.length; i++) {
                    details.append(columns[i]).append(": ").append(table.getValueAt(selectedRow, i)).append("\n");
                }
                JOptionPane.showMessageDialog(frame, details.toString(), "Chi tiết văn bản", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Vui lòng chọn một văn bản", "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // Show frame
        frame.setVisible(true);
        System.out.println("GUI window opened successfully!");
        
        // Keep the application running
        System.out.println("Application is running. Close the window to exit.");
        
        // Add window listener to handle closing
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                System.out.println("Window is closing. Exiting application.");
                System.exit(0);
            }
        });
    }
}
