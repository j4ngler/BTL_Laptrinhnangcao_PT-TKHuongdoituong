package com.example.docmgmt.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class SimpleSwingApp {
    private final JFrame frame;
    private final JTable table;
    private final DefaultTableModel model;

    public SimpleSwingApp() {
        frame = new JFrame("Quản lý văn bản (Swing) - Đơn giản");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(900, 520);

        JPanel top = new JPanel(new FlowLayout());
        
        JButton btnAdd = new JButton("Thêm");
        JButton btnExport = new JButton("Xuất");
        JButton btnRefresh = new JButton("Làm mới");
        JButton btnSubmit = new JButton("Submit");
        JButton btnClassify = new JButton("Classify");
        JButton btnApprove = new JButton("Approve");
        JButton btnIssue = new JButton("Issue");
        JButton btnArchive = new JButton("Archive");
        JButton btnDetails = new JButton("Chi tiết");
        JButton btnDashboard = new JButton("Dashboard");
        JButton btnDistribute = new JButton("Phân phối");
        JButton btnRecall = new JButton("Thu hồi");
        JTextField searchField = new JTextField(20);
        JButton btnSearch = new JButton("Tìm");
        
        top.add(btnAdd); top.add(btnExport); top.add(btnDetails); top.add(new JSeparator(SwingConstants.VERTICAL));
        top.add(btnRefresh); top.add(new JSeparator(SwingConstants.VERTICAL));
        top.add(btnSubmit); top.add(btnClassify); top.add(btnApprove); top.add(btnIssue); top.add(btnArchive);
        top.add(new JSeparator(SwingConstants.VERTICAL)); top.add(searchField); top.add(btnSearch);

        model = new DefaultTableModel(new Object[]{"ID","Tiêu đề","Trạng thái","Tạo lúc","Số/VB","Thời hạn","Độ ưu tiên","Phân công"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        
        // Add some sample data
        model.addRow(new Object[]{1, "Văn bản mẫu 1", "DRAFT", "2024-01-01 10:00", "001/2024", "", "Thường", "Chưa phân công"});
        model.addRow(new Object[]{2, "Văn bản mẫu 2", "SUBMITTED", "2024-01-02 11:00", "002/2024", "", "Khẩn", "Người A"});

        JScrollPane scrollPane = new JScrollPane(table);
        
        frame.add(top, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        
        // Add event listeners
        btnAdd.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Tính năng thêm văn bản"));
        btnExport.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Tính năng xuất văn bản"));
        btnRefresh.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Làm mới danh sách"));
        btnSubmit.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Submit văn bản"));
        btnClassify.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Phân loại văn bản"));
        btnApprove.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Phê duyệt văn bản"));
        btnIssue.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Ban hành văn bản"));
        btnArchive.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Lưu trữ văn bản"));
        btnDetails.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Xem chi tiết văn bản"));
        btnDashboard.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Dashboard thống kê"));
        btnDistribute.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Phân phối văn bản"));
        btnRecall.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Thu hồi văn bản"));
        btnSearch.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Tìm kiếm: " + searchField.getText()));
    }

    public void show() { 
        frame.setVisible(true); 
        System.out.println("SimpleSwingApp window opened");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleSwingApp app = new SimpleSwingApp();
                app.show();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Lỗi khởi động GUI: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
