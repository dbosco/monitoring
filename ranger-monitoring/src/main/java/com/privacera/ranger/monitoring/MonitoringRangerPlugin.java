package com.privacera.ranger.monitoring;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * MonitoringRangerPlugin class that continuously monitors Ranger authorization
 * by calling checkAccess at regular intervals.
 * 
 * This class uses DummyAuthorizer to perform periodic access checks for monitoring
 * and health check purposes.
 */
public class MonitoringRangerPlugin {
    
    private static final Log LOG = LogFactory.getLog(MonitoringRangerPlugin.class.getName());
    
    private static final long DEFAULT_INTERVAL_SECONDS = 60;
    
    private final DummyAuthorizer authorizer;
    private final long intervalSeconds;
    private final String user;
    private final Set<String> groups;
    private final String database;
    private final String table;
    private final String column;
    
    private volatile boolean running = false;
    private Thread monitoringThread = null;
    
    // Statistics counters
    private final AtomicLong allowedCount = new AtomicLong(0);
    private final AtomicLong deniedCount = new AtomicLong(0);
    private final AtomicLong totalCount = new AtomicLong(0);
    
    // Timing statistics (all times in milliseconds)
    private final AtomicLong totalTimeMs = new AtomicLong(0);
    private volatile long minTimeMs = Long.MAX_VALUE;
    private volatile long maxTimeMs = 0;
    
    // Prometheus metrics
    private final PrometheusMetricsRegistry metrics = PrometheusMetricsRegistry.getInstance();
    
    /**
     * Constructor with default interval (60 seconds).
     * 
     * @param user the username for access checks
     * @param groups set of groups the user belongs to
     * @param database the database name
     * @param table the table name
     * @param column the column name
     */
    public MonitoringRangerPlugin(String user, Set<String> groups, String database, String table, String column) {
        this(user, groups, database, table, column, DEFAULT_INTERVAL_SECONDS);
    }
    
    /**
     * Constructor with custom interval.
     * 
     * @param user the username for access checks
     * @param groups set of groups the user belongs to
     * @param database the database name
     * @param table the table name
     * @param column the column name
     * @param intervalSeconds the interval in seconds between access checks
     */
    public MonitoringRangerPlugin(String user, Set<String> groups, String database, String table, String column,
            long intervalSeconds) {
        this.authorizer = new DummyAuthorizer();
        this.user = user;
        this.groups = groups != null ? groups : new HashSet<>();
        this.database = database;
        this.table = table;
        this.column = column;
        this.intervalSeconds = intervalSeconds > 0 ? intervalSeconds : DEFAULT_INTERVAL_SECONDS;
        
        if (!this.authorizer.isInitialized()) {
            throw new IllegalStateException("Failed to initialize DummyAuthorizer");
        }
        
        LOG.info("MonitoringRangerPlugin created with interval: " + this.intervalSeconds + " seconds");
        LOG.info("Monitoring parameters - user: " + user + ", database: " + database + 
                ", table: " + table + ", column: " + column);
    }
    
    /**
     * Starts the monitoring loop in a separate thread.
     */
    public synchronized void start() {
        if (running) {
            LOG.warn("MonitoringRangerPlugin is already running");
            return;
        }
        
        running = true;
        monitoringThread = new Thread(this::monitoringLoop, "MonitoringRangerPlugin-Thread");
        monitoringThread.setDaemon(false);
        monitoringThread.start();
        
        // Update metrics
        metrics.getMonitoringPluginRunning().set(1.0);
        
        LOG.info("MonitoringRangerPlugin started");
    }
    
    /**
     * Stops the monitoring loop gracefully.
     */
    public synchronized void stop() {
        if (!running) {
            LOG.warn("MonitoringRangerPlugin is not running");
            return;
        }
        
        running = false;
        if (monitoringThread != null) {
            monitoringThread.interrupt();
            try {
                monitoringThread.join(5000); // Wait up to 5 seconds for thread to finish
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while waiting for monitoring thread to finish", e);
                Thread.currentThread().interrupt();
            }
        }
        
        // Update metrics
        metrics.getMonitoringPluginRunning().set(0.0);
        
        LOG.info("MonitoringRangerPlugin stopped");
    }
    
    /**
     * The main monitoring loop that calls checkAccess at regular intervals.
     */
    private void monitoringLoop() {
        LOG.info("Monitoring loop started. Will check access every " + intervalSeconds + " seconds");
        
        while (running) {
            try {
                LOG.info("Performing periodic access check...");
                
                // Increment active access checks gauge
                metrics.getActiveAccessChecks().inc();
                
                // Measure time taken for the access check
                long startTime = System.currentTimeMillis();
                boolean result = false;
                Exception accessError = null;
                try {
                    result = authorizer.checkAccess(user, groups, database, table, column);
                } catch (Exception e) {
                    accessError = e;
                    throw e;
                } finally {
                    long endTime = System.currentTimeMillis();
                    long timeTakenMs = endTime - startTime;
                    
                    // Update Prometheus metrics (no labels to avoid high cardinality)
                    metrics.getAccessChecksTotal().inc();
                    metrics.getAccessCheckDurationMs().observe(timeTakenMs);
                    
                    if (accessError != null) {
                        metrics.getAccessCheckErrorsTotal().inc();
                    } else if (result) {
                        metrics.getAccessAllowedTotal().inc();
                    } else {
                        metrics.getAccessDeniedTotal().inc();
                    }
                    
                    // Decrement active access checks gauge
                    metrics.getActiveAccessChecks().dec();
                    
                    // Update internal counters and timing statistics (for backward compatibility)
                    if (accessError == null) {
                        totalCount.incrementAndGet();
                        if (result) {
                            allowedCount.incrementAndGet();
                        } else {
                            deniedCount.incrementAndGet();
                        }
                        
                        totalTimeMs.addAndGet(timeTakenMs);
                        synchronized (this) {
                            if (timeTakenMs < minTimeMs) {
                                minTimeMs = timeTakenMs;
                            }
                            if (timeTakenMs > maxTimeMs) {
                                maxTimeMs = timeTakenMs;
                            }
                        }
                    }
                }
                
                // Calculate average time (for logging)
                long averageTimeMs = totalCount.get() > 0 ? totalTimeMs.get() / totalCount.get() : 0;
                long currentTimeMs = System.currentTimeMillis() - startTime;
                
                LOG.info("Periodic access check completed. Result: " + (result ? "ALLOWED" : "DENIED") + 
                        " | Total: " + totalCount.get() + 
                        ", Allowed: " + allowedCount.get() + 
                        ", Denied: " + deniedCount.get() +
                        " | time_taken_ms: " + currentTimeMs +
                        ", average_time_taken_ms: " + averageTimeMs +
                        ", min_time_taken_ms: " + (minTimeMs == Long.MAX_VALUE ? 0 : minTimeMs) +
                        ", max_time_taken_ms: " + maxTimeMs);
                
                // Sleep for the specified interval, checking if we should stop
                LOG.info("Sleeping for " + intervalSeconds + " seconds until next access check...");
                for (long remaining = intervalSeconds * 1000; remaining > 0 && running; remaining -= 100) {
                    Thread.sleep(Math.min(100, remaining));
                }
                
            } catch (InterruptedException e) {
                LOG.info("Monitoring loop interrupted, shutting down");
                Thread.currentThread().interrupt();
                running = false;
                break;
            } catch (Exception e) {
                LOG.error("Error during access check in monitoring loop: " + e.getMessage(), e);
                // Record error in metrics (no labels to avoid high cardinality)
                metrics.getAccessCheckErrorsTotal().inc();
                
                // Continue monitoring even if one check fails
                try {
                    Thread.sleep(1000); // Brief pause before retrying after an error
                } catch (InterruptedException ie) {
                    LOG.info("Monitoring loop interrupted after error, shutting down");
                    Thread.currentThread().interrupt();
                    running = false;
                    break;
                }
            }
        }
        
        LOG.info("Monitoring loop ended");
    }
    
    /**
     * Checks if the monitoring is currently running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Gets the current interval in seconds.
     * 
     * @return the interval in seconds
     */
    public long getIntervalSeconds() {
        return intervalSeconds;
    }
    
    /**
     * Parses command line arguments and extracts the value for a given flag.
     * 
     * @param args command line arguments
     * @param flag the flag to look for (e.g., "--interval")
     * @return the value after the flag, or null if flag not found or no value provided
     */
    private static String parseArgument(String[] args, String flag) {
        for (int i = 0; i < args.length; i++) {
            if (flag.equals(args[i])) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                } else {
                    LOG.warn("Flag " + flag + " specified but no value provided");
                    return null;
                }
            }
        }
        return null;
    }
    
    /**
     * Main method for running MonitoringRangerPlugin from command line.
     * 
     * Usage: MonitoringRangerPlugin --interval <seconds>
     * 
     * @param args command line arguments - supports --interval flag
     */
    public static void main(String[] args) {
        LOG.info("Starting MonitoringRangerPlugin...");
        
        // Default values
        String user = "test_user1";
        Set<String> groups = new HashSet<>();
        String database = "test_db1";
        String table = "test_table1";
        String column = "test_col1";
        long intervalSeconds = DEFAULT_INTERVAL_SECONDS;
        
        // Parse command line arguments - using flag-based format for extensibility
        String intervalValue = parseArgument(args, "--interval");
        if (intervalValue != null) {
            try {
                intervalSeconds = Long.parseLong(intervalValue);
                if (intervalSeconds <= 0) {
                    LOG.warn("Invalid interval specified: " + intervalValue + ". Using default: " + DEFAULT_INTERVAL_SECONDS);
                    intervalSeconds = DEFAULT_INTERVAL_SECONDS;
                }
            } catch (NumberFormatException e) {
                LOG.warn("Invalid interval format: " + intervalValue + ". Using default: " + DEFAULT_INTERVAL_SECONDS);
            }
        }
        
        // Start metrics server
        MetricsServer metricsServer = MetricsServer.getInstance();
        metricsServer.start();
        
        // Create and start the monitoring plugin
        MonitoringRangerPlugin plugin = new MonitoringRangerPlugin(user, groups, database, table, column, intervalSeconds);
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received, stopping MonitoringRangerPlugin...");
            plugin.stop();
            metricsServer.stop();
        }));
        
        plugin.start();
        
        // Keep main thread alive until interrupted or monitoring stops
        try {
            while (plugin.isRunning()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            LOG.info("Main thread interrupted");
            Thread.currentThread().interrupt();
            plugin.stop();
        }
        
        LOG.info("MonitoringRangerPlugin main method completed");
    }
}

