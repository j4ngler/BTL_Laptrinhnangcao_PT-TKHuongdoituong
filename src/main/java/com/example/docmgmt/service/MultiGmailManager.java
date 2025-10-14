package com.example.docmgmt.service;

import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.GridFsRepository;

import java.util.*;
import java.util.concurrent.*;

public class MultiGmailManager {
    private final Map<String, GmailAPIService> gmailServices;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final DocumentRepository docRepo;
    private final GridFsRepository gridFsRepo;
    
    // Configuration
    private final int maxConcurrentAccounts;
    private final int syncIntervalMinutes;
    private final String queryFilter;
    
    public MultiGmailManager(DocumentRepository docRepo, GridFsRepository gridFsRepo, 
                           int maxConcurrentAccounts, int syncIntervalMinutes, String queryFilter) {
        this.docRepo = docRepo;
        this.gridFsRepo = gridFsRepo;
        this.maxConcurrentAccounts = maxConcurrentAccounts;
        this.syncIntervalMinutes = syncIntervalMinutes;
        this.queryFilter = queryFilter;
        
        this.gmailServices = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(maxConcurrentAccounts);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Thêm Gmail account
     */
    public boolean addGmailAccount(String email, String credentialsPath) {
        try {
            GmailAPIService service = new GmailAPIService(email, docRepo, gridFsRepo);
            gmailServices.put(email, service);
            System.out.println("Added Gmail account: " + email);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to add Gmail account " + email + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Xóa Gmail account
     */
    public boolean removeGmailAccount(String email) {
        GmailAPIService service = gmailServices.remove(email);
        if (service != null) {
            service.shutdown();
            System.out.println("Removed Gmail account: " + email);
            return true;
        }
        return false;
    }
    
    /**
     * Lấy danh sách Gmail accounts
     */
    public Set<String> getGmailAccounts() {
        return new HashSet<>(gmailServices.keySet());
    }
    
    /**
     * Fetch emails từ tất cả accounts
     */
    public CompletableFuture<Map<String, Integer>> fetchAllEmailsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, CompletableFuture<Integer>> futures = new HashMap<>();
            
            // Tạo futures cho từng account
            for (Map.Entry<String, GmailAPIService> entry : gmailServices.entrySet()) {
                String email = entry.getKey();
                GmailAPIService service = entry.getValue();
                
                CompletableFuture<Integer> future = service.fetchEmailsAsync(queryFilter)
                    .exceptionally(throwable -> {
                        System.err.println("Error fetching emails from " + email + ": " + throwable.getMessage());
                        return 0;
                    });
                
                futures.put(email, future);
            }
            
            // Chờ tất cả futures hoàn thành
            Map<String, Integer> results = new HashMap<>();
            for (Map.Entry<String, CompletableFuture<Integer>> entry : futures.entrySet()) {
                try {
                    results.put(entry.getKey(), entry.getValue().get(30, TimeUnit.SECONDS));
                } catch (Exception e) {
                    System.err.println("Timeout fetching emails from " + entry.getKey());
                    results.put(entry.getKey(), 0);
                }
            }
            
            return results;
        }, executor);
    }
    
    /**
     * Fetch emails từ account cụ thể
     */
    public CompletableFuture<Integer> fetchEmailsFromAccount(String email) {
        GmailAPIService service = gmailServices.get(email);
        if (service == null) {
            return CompletableFuture.completedFuture(0);
        }
        
        return service.fetchEmailsAsync(queryFilter);
    }
    
    /**
     * Bắt đầu auto-sync
     */
    public void startAutoSync() {
        System.out.println("Starting auto-sync every " + syncIntervalMinutes + " minutes");
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Starting scheduled email fetch...");
                Map<String, Integer> results = fetchAllEmailsAsync().get();
                
                int totalProcessed = results.values().stream().mapToInt(Integer::intValue).sum();
                System.out.println("Scheduled fetch completed. Total emails processed: " + totalProcessed);
                
                // Log results
                for (Map.Entry<String, Integer> entry : results.entrySet()) {
                    System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " emails");
                }
                
            } catch (Exception e) {
                System.err.println("Error in scheduled email fetch: " + e.getMessage());
            }
        }, 0, syncIntervalMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Dừng auto-sync
     */
    public void stopAutoSync() {
        System.out.println("Stopping auto-sync");
        scheduler.shutdown();
    }
    
    /**
     * Setup webhooks cho tất cả accounts
     */
    public void setupWebhooks(String topicName) {
        for (Map.Entry<String, GmailAPIService> entry : gmailServices.entrySet()) {
            try {
                entry.getValue().setupWebhook(topicName);
                System.out.println("Webhook setup for " + entry.getKey());
            } catch (Exception e) {
                System.err.println("Failed to setup webhook for " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Lấy thống kê
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_accounts", gmailServices.size());
        stats.put("accounts", gmailServices.keySet());
        stats.put("max_concurrent", maxConcurrentAccounts);
        stats.put("sync_interval_minutes", syncIntervalMinutes);
        stats.put("query_filter", queryFilter);
        return stats;
    }
    
    /**
     * Health check cho tất cả accounts
     */
    public Map<String, Boolean> healthCheck() {
        Map<String, Boolean> health = new HashMap<>();
        
        for (Map.Entry<String, GmailAPIService> entry : gmailServices.entrySet()) {
            try {
                // Test fetch 1 email để kiểm tra kết nối
                CompletableFuture<Integer> future = entry.getValue().fetchEmailsAsync("is:unread limit:1");
                future.get(10, TimeUnit.SECONDS);
                health.put(entry.getKey(), true);
            } catch (Exception e) {
                health.put(entry.getKey(), false);
                System.err.println("Health check failed for " + entry.getKey() + ": " + e.getMessage());
            }
        }
        
        return health;
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        System.out.println("Shutting down MultiGmailManager...");
        
        // Shutdown all Gmail services
        for (GmailAPIService service : gmailServices.values()) {
            service.shutdown();
        }
        
        // Shutdown executors
        executor.shutdown();
        scheduler.shutdown();
        
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("MultiGmailManager shutdown completed");
    }
}

