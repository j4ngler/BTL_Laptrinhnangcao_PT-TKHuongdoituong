package com.example.docmgmt.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class SimpleGUI {
    public static void main(String[] args) {
        System.out.println("Starting Simple GUI...");
        
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Quản lý văn bản - Simple");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 500);
            frame.setLocationRelativeTo(null);
            
            // Top panel
            JPanel topPanel = new JPanel(new FlowLayout());
            JButton btnAdd = new JButton("Thêm");
            JButton btnExport = new JButton("Xuất");
            JButton btnRefresh = new JButton("Làm mới");
            JButton btnSubmit = new JButton("Submit");
            JButton btnClassify = new JButton("Classify");
            JButton btnApprove = new JButton("Approve");
            JButton btnIssue = new JButton("Issue");
            JButton btnArchive = new JButton("Archive");
            
            topPanel.add(btnAdd);
            topPanel.add(btnExport);
            topPanel.add(btnRefresh);
            topPanel.add(new JSeparator(SwingConstants.VERTICAL));
            topPanel.add(btnSubmit);
            topPanel.add(btnClassify);
            topPanel.add(btnApprove);
            topPanel.add(btnIssue);
            topPanel.add(btnArchive);
            
            // Table
            String[] columns = {"ID", "Tiêu đề", "Trạng thái", "Tạo lúc", "Số/VB"};
            DefaultTableModel model = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            
            JTable table = new JTable(model);
            
            // Add sample data
            model.addRow(new Object[]{1, "Văn bản mẫu 1", "DRAFT", "2024-01-01 10:00", "001/2024"});
            model.addRow(new Object[]{2, "Văn bản mẫu 2", "SUBMITTED", "2024-01-02 11:00", "002/2024"});
            model.addRow(new Object[]{3, "Văn bản mẫu 3", "APPROVED", "2024-01-03 12:00", "003/2024"});
            
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
            
            // Show frame
            frame.setVisible(true);
            System.out.println("Simple GUI window opened successfully!");
        });
    }
}
