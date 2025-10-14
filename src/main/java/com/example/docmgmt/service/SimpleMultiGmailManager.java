package com.example.docmgmt.service;

import com.example.docmgmt.repo.DocumentRepository;
import com.example.docmgmt.repo.GridFsRepository;

import java.util.*;
import java.util.concurrent.*;
import com.example.docmgmt.repo.GmailAccountRepository;
import com.example.docmgmt.repo.EmailFetchLogRepository;

public class SimpleMultiGmailManager {
    private final Map<String, SimpleGmailAPIService> gmailServices;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final DocumentRepository docRepo;
    private final GridFsRepository gridFsRepo;
    private final GmailAccountRepository accountRepo;
    private EmailFetchLogRepository fetchLogRepo;
    
    // Configuration
    private final int maxConcurrentAccounts;
    private final int syncIntervalMinutes;
    private final String queryFilter;
    
    public SimpleMultiGmailManager(DocumentRepository docRepo, GridFsRepository gridFsRepo, 
                                 int maxConcurrentAccounts, int syncIntervalMinutes, String queryFilter) {
        this(docRepo, gridFsRepo, null, maxConcurrentAccounts, syncIntervalMinutes, queryFilter);
    }

    public SimpleMultiGmailManager(DocumentRepository docRepo, GridFsRepository gridFsRepo,
                                   GmailAccountRepository accountRepo,
                                   int maxConcurrentAccounts, int syncIntervalMinutes, String queryFilter) {
        this.docRepo = docRepo;
        this.gridFsRepo = gridFsRepo;
        this.accountRepo = accountRepo;
        this.maxConcurrentAccounts = maxConcurrentAccounts;
        this.syncIntervalMinutes = syncIntervalMinutes;
        this.queryFilter = queryFilter;
        
        this.gmailServices = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(maxConcurrentAccounts);
        this.scheduler = Executors.newScheduledThreadPool(2);
        try { this.fetchLogRepo = new EmailFetchLogRepository(docRepo.getDataSource()); this.fetchLogRepo.migrate(); } catch (Exception ignore) {}

        // Auto-load accounts from DB
        if (this.accountRepo != null) {
            try {
                for (var acc : this.accountRepo.listActive()) {
                    addGmailAccount(acc.email(), acc.credentialsPath());
                }
            } catch (Exception e) {
                System.err.println("Failed to autoload Gmail accounts: " + e.getMessage());
            }
        }
    }
    
    /**
     * Thêm Gmail account
     */
    public boolean addGmailAccount(String email, String credentialsPath) {
        try {
            SimpleGmailAPIService service = new SimpleGmailAPIService(email, credentialsPath, docRepo, gridFsRepo);
            gmailServices.put(email, service);
            if (accountRepo != null) {
                accountRepo.add(email, credentialsPath);
            }
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
        SimpleGmailAPIService service = gmailServices.remove(email);
        if (service != null) {
            service.shutdown();
            if (accountRepo != null) {
                try { accountRepo.remove(email); } catch (Exception ignore) {}
            }
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
            for (Map.Entry<String, SimpleGmailAPIService> entry : gmailServices.entrySet()) {
                String email = entry.getKey();
                SimpleGmailAPIService service = entry.getValue();
                
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
                    int cnt = entry.getValue().get(30, TimeUnit.SECONDS);
                    results.put(entry.getKey(), cnt);
                    if (fetchLogRepo != null) fetchLogRepo.log(entry.getKey(), cnt, "OK", null);
                } catch (Exception e) {
                    System.err.println("Timeout fetching emails from " + entry.getKey());
                    results.put(entry.getKey(), 0);
                    if (fetchLogRepo != null) fetchLogRepo.log(entry.getKey(), 0, "ERROR", e.getMessage());
                }
            }
            
            return results;
        }, executor);
    }
    
    /**
     * Fetch emails từ account cụ thể
     */
    public CompletableFuture<Integer> fetchEmailsFromAccount(String email) {
        SimpleGmailAPIService service = gmailServices.get(email);
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
        
        for (Map.Entry<String, SimpleGmailAPIService> entry : gmailServices.entrySet()) {
            try {
                boolean isHealthy = entry.getValue().testConnection();
                health.put(entry.getKey(), isHealthy);
            } catch (Exception e) {
                health.put(entry.getKey(), false);
                System.err.println("Health check failed for " + entry.getKey() + ": " + e.getMessage());
            }
        }
        
        return health;
    }
    
    /**
     * Test tất cả connections
     */
    public void testAllConnections() {
        System.out.println("Testing all Gmail connections...");
        
        for (Map.Entry<String, SimpleGmailAPIService> entry : gmailServices.entrySet()) {
            String email = entry.getKey();
            SimpleGmailAPIService service = entry.getValue();
            
            System.out.println("Testing " + email + "...");
            boolean success = service.testConnection();
            System.out.println("  " + email + ": " + (success ? "SUCCESS" : "FAILED"));
        }
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        System.out.println("Shutting down SimpleMultiGmailManager...");
        
        // Shutdown all Gmail services
        for (SimpleGmailAPIService service : gmailServices.values()) {
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
        
        System.out.println("SimpleMultiGmailManager shutdown completed");
    }
}
