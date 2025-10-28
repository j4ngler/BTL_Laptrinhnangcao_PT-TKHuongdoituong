package com.example.docmgmt.gui;

import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.UserRepository;
import com.example.docmgmt.domain.Models.DocState;
import com.example.docmgmt.domain.Models.UserStatus;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class AdminDashboard extends JPanel {
    private final JFrame parentFrame;
    private final DocumentRepository docRepo;
    private final UserRepository userRepo;

    public AdminDashboard(JFrame parent, DocumentRepository docRepo, UserRepository userRepo) {
        this.parentFrame = parent;
        this.docRepo = docRepo;
        this.userRepo = userRepo;
        init();
    }

    private void init() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("DASHBOARD QUẢN TRỊ", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        
        // Top panel with title and navigation
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(titleLabel, BorderLayout.CENTER);
        
        JButton viewDocsBtn = new JButton("Xem tất cả văn bản");
        viewDocsBtn.setPreferredSize(new Dimension(150, 35));
        topPanel.add(viewDocsBtn, BorderLayout.EAST);

        // Statistics cards
        JPanel statsPanel = new JPanel(new GridLayout(2, 3, 15, 15));
        
        StatsCard docTotalCard = createStatsCard("Tổng văn bản", "0", Color.decode("#4CAF50"));
        StatsCard docPendingCard = createStatsCard("Chờ xử lý", "0", Color.decode("#FF9800"));
        StatsCard docCompleteCard = createStatsCard("Hoàn thành", "0", Color.decode("#2196F3"));
        StatsCard userTotalCard = createStatsCard("Tổng người dùng", "0", Color.decode("#9C27B0"));
        StatsCard userPendingCard = createStatsCard("Chờ duyệt", "0", Color.decode("#F44336"));
        StatsCard userActiveCard = createStatsCard("Đang hoạt động", "0", Color.decode("#00BCD4"));
        
        statsPanel.add(docTotalCard);
        statsPanel.add(docPendingCard);
        statsPanel.add(docCompleteCard);
        statsPanel.add(userTotalCard);
        statsPanel.add(userPendingCard);
        statsPanel.add(userActiveCard);

        // Store cards for updating
        Map<String, StatsCard> cards = new HashMap<>();
        cards.put("docTotal", docTotalCard);
        cards.put("docPending", docPendingCard);
        cards.put("docComplete", docCompleteCard);
        cards.put("userTotal", userTotalCard);
        cards.put("userPending", userPendingCard);
        cards.put("userActive", userActiveCard);

        // Bottom panel with quick actions
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JButton manageUsersBtn = new JButton("Quản lý người dùng");
        JButton manageDocsBtn = new JButton("Quản lý văn bản");
        JButton reportsBtn = new JButton("Báo cáo thống kê");
        
        manageUsersBtn.setPreferredSize(new Dimension(180, 40));
        manageDocsBtn.setPreferredSize(new Dimension(180, 40));
        reportsBtn.setPreferredSize(new Dimension(180, 40));
        
        actionPanel.add(manageUsersBtn);
        actionPanel.add(manageDocsBtn);
        actionPanel.add(reportsBtn);

        // Layout
        add(topPanel, BorderLayout.NORTH);
        add(statsPanel, BorderLayout.CENTER);
        add(actionPanel, BorderLayout.SOUTH);

        // Load statistics
        loadStatistics(cards);

        // Button actions
        viewDocsBtn.addActionListener(e -> parentFrame.setVisible(false));
        manageUsersBtn.addActionListener(e -> {
            try {
                new UserManagementDialog(parentFrame, userRepo).setVisible(true);
                loadStatistics(cards);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parentFrame, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        manageDocsBtn.addActionListener(e -> parentFrame.setVisible(false));
        reportsBtn.addActionListener(e -> showReports());
    }

    private StatsCard createStatsCard(String title, String value, Color color) {
        StatsCard card = new StatsCard(title, value, color);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createRaisedBevelBorder(),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        return card;
    }

    private void loadStatistics(Map<String, StatsCard> cards) {
        try {
            // Document statistics
            var allDocs = docRepo.list();
            int docTotal = allDocs.size();
            long docPending = allDocs.stream().filter(d -> d.state() == DocState.CHO_XEM_XET || d.state() == DocState.CHO_DUYET).count();
            long docComplete = allDocs.stream().filter(d -> d.state() == DocState.HOAN_THANH).count();
            
            // User statistics
            var allUsers = userRepo.list();
            int userTotal = allUsers.size();
            long userPending = allUsers.stream().filter(u -> u.status() == UserStatus.PENDING).count();
            long userActive = allUsers.stream().filter(u -> u.status() == UserStatus.APPROVED).count();

            // Update cards
            cards.get("docTotal").updateValue(String.valueOf(docTotal));
            cards.get("docPending").updateValue(String.valueOf(docPending));
            cards.get("docComplete").updateValue(String.valueOf(docComplete));
            cards.get("userTotal").updateValue(String.valueOf(userTotal));
            cards.get("userPending").updateValue(String.valueOf(userPending));
            cards.get("userActive").updateValue(String.valueOf(userActive));
            
        } catch (SQLException e) {
            System.err.println("Lỗi tải thống kê: " + e.getMessage());
        }
    }

    private void showReports() {
        JDialog dialog = new JDialog(parentFrame, "Báo cáo thống kê", true);
        dialog.setSize(500, 300);
        dialog.setLocationRelativeTo(parentFrame);
        
        JTextArea reportArea = new JTextArea();
        reportArea.setEditable(false);
        
        try {
            var allDocs = docRepo.list();
            var allUsers = userRepo.list();
            
            StringBuilder report = new StringBuilder();
            report.append("=== BÁO CÁO TỔNG QUAN ===\n\n");
            report.append(String.format("Tổng văn bản: %d\n", allDocs.size()));
            
            Map<DocState, Long> docByState = new HashMap<>();
            for (var doc : allDocs) {
                docByState.merge(doc.state(), 1L, Long::sum);
            }
            
            report.append("\nVăn bản theo trạng thái:\n");
            for (DocState state : DocState.values()) {
                long count = docByState.getOrDefault(state, 0L);
                if (count > 0) {
                    report.append(String.format("- %s: %d\n", state, count));
                }
            }
            
            report.append(String.format("\nTổng người dùng: %d\n", allUsers.size()));
            report.append(String.format("Đang hoạt động: %d\n", 
                allUsers.stream().filter(u -> u.status() == UserStatus.APPROVED).count()));
            report.append(String.format("Chờ duyệt: %d\n", 
                allUsers.stream().filter(u -> u.status() == UserStatus.PENDING).count()));
            
            reportArea.setText(report.toString());
        } catch (Exception e) {
            reportArea.setText("Lỗi tạo báo cáo: " + e.getMessage());
        }
        
        dialog.add(new JScrollPane(reportArea));
        dialog.setVisible(true);
    }

    // Inner class for statistics card
    @SuppressWarnings("unused")
    private static class StatsCard extends JPanel {
        private final String title;
        private JLabel valueLabel;
        @SuppressWarnings("unused")
        private final Color bgColor;

        public StatsCard(String title, String value, Color bgColor) {
            this.title = title;
            this.bgColor = bgColor;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(bgColor);
            
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            titleLabel.setForeground(Color.WHITE);
            
            valueLabel = new JLabel(value);
            valueLabel.setFont(new Font("Arial", Font.BOLD, 32));
            valueLabel.setForeground(Color.WHITE);
            
            add(Box.createVerticalGlue());
            add(titleLabel);
            add(valueLabel);
            add(Box.createVerticalGlue());
        }

        public void updateValue(String newValue) {
            valueLabel.setText(newValue);
        }
    }
}

