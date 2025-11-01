package com.privacera.ranger.monitoring;

import io.prometheus.client.exporter.HTTPServer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

/**
 * HTTP server that exposes Prometheus metrics endpoint.
 * Runs on port 6085 by default, configurable via METRICS_PORT environment variable.
 */
public class MetricsServer {
    
    private static final Log LOG = LogFactory.getLog(MetricsServer.class);
    
    private static final int DEFAULT_PORT = 6085;
    private static MetricsServer instance;
    private HTTPServer server;
    private final int port;
    
    private MetricsServer(int port) {
        this.port = port;
    }
    
    /**
     * Get or create the singleton MetricsServer instance.
     * 
     * @param port the port to run the metrics server on
     * @return the MetricsServer instance
     */
    public static synchronized MetricsServer getInstance(int port) {
        if (instance == null) {
            instance = new MetricsServer(port);
        }
        return instance;
    }
    
    /**
     * Get or create the MetricsServer instance using default port or environment variable.
     * 
     * @return the MetricsServer instance
     */
    public static synchronized MetricsServer getInstance() {
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("METRICS_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid METRICS_PORT environment variable: " + portEnv + ". Using default: " + DEFAULT_PORT);
            }
        }
        return getInstance(port);
    }
    
    /**
     * Start the metrics HTTP server.
     */
    public void start() {
        if (server != null) {
            LOG.warn("Metrics server is already running on port " + port);
            return;
        }
        
        try {
            server = new HTTPServer(port);
            LOG.info("Prometheus metrics server started on port " + port);
            LOG.info("Metrics available at: http://localhost:" + port + "/metrics");
        } catch (IOException e) {
            LOG.error("Failed to start metrics server on port " + port + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to start metrics server", e);
        }
    }
    
    /**
     * Stop the metrics HTTP server.
     */
    public void stop() {
        if (server != null) {
            try {
                server.stop();
                server = null;
                LOG.info("Prometheus metrics server stopped");
            } catch (Exception e) {
                LOG.error("Error stopping metrics server: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Check if the metrics server is running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return server != null;
    }
    
    /**
     * Get the port the metrics server is running on.
     * 
     * @return the port number
     */
    public int getPort() {
        return port;
    }
}

