package com.privacera.ranger.monitoring;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Centralized Prometheus metrics registry for Ranger monitoring.
 * Provides singleton access to all metrics instruments.
 */
public class PrometheusMetricsRegistry {
    
    private static final Log LOG = LogFactory.getLog(PrometheusMetricsRegistry.class);
    
    private static PrometheusMetricsRegistry instance;
    
    // Access check metrics
    private final Counter accessChecksTotal;
    private final Counter accessAllowedTotal;
    private final Counter accessDeniedTotal;
    private final Counter accessCheckErrorsTotal;
    private final Histogram accessCheckDurationMs;
    private final Gauge activeAccessChecks;
    
    // API call metrics (Ranger Admin REST Client)
    private final Counter rangerApiCallsTotal;
    private final Counter rangerApiErrorsTotal;
    private final Histogram rangerApiDurationMs;
    
    // Plugin status metrics
    private final Gauge monitoringPluginRunning;
    private final Gauge authorizerInitialized;
    
    private PrometheusMetricsRegistry() {
        // Access check metrics (no labels to avoid high cardinality)
        accessChecksTotal = Counter.build()
            .name("ranger_access_checks_total")
            .help("Total number of access checks performed")
            .register();
        
        accessAllowedTotal = Counter.build()
            .name("ranger_access_allowed_total")
            .help("Total number of access checks that were allowed")
            .register();
        
        accessDeniedTotal = Counter.build()
            .name("ranger_access_denied_total")
            .help("Total number of access checks that were denied")
            .register();
        
        accessCheckErrorsTotal = Counter.build()
            .name("ranger_access_check_errors_total")
            .help("Total number of errors during access checks")
            .register();
        
        accessCheckDurationMs = Histogram.build()
            .name("ranger_access_check_duration_ms")
            .help("Duration of access checks in milliseconds")
            .buckets(10.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0)
            .register();
        
        activeAccessChecks = Gauge.build()
            .name("ranger_active_access_checks")
            .help("Number of currently active access checks")
            .register();
        
        // Ranger Admin API metrics
        rangerApiCallsTotal = Counter.build()
            .name("ranger_api_calls_total")
            .help("Total number of Ranger Admin API calls")
            .labelNames("api_method", "status")
            .register();
        
        rangerApiErrorsTotal = Counter.build()
            .name("ranger_api_errors_total")
            .help("Total number of Ranger Admin API errors")
            .labelNames("api_method")
            .register();
        
        rangerApiDurationMs = Histogram.build()
            .name("ranger_api_duration_ms")
            .help("Duration of Ranger Admin API calls in milliseconds")
            .labelNames("api_method")
            .buckets(10.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
            .register();
        
        // Plugin status metrics
        monitoringPluginRunning = Gauge.build()
            .name("ranger_monitoring_plugin_running")
            .help("Whether the monitoring plugin is running (1 = running, 0 = stopped)")
            .register();
        
        authorizerInitialized = Gauge.build()
            .name("ranger_authorizer_initialized")
            .help("Whether the authorizer is initialized (1 = initialized, 0 = not initialized)")
            .register();
        
        LOG.info("PrometheusMetricsRegistry initialized successfully");
    }
    
    /**
     * Get the singleton instance of PrometheusMetricsRegistry.
     */
    public static synchronized PrometheusMetricsRegistry getInstance() {
        if (instance == null) {
            instance = new PrometheusMetricsRegistry();
        }
        return instance;
    }
    
    // Access check metrics getters
    
    public Counter getAccessChecksTotal() {
        return accessChecksTotal;
    }
    
    public Counter getAccessAllowedTotal() {
        return accessAllowedTotal;
    }
    
    public Counter getAccessDeniedTotal() {
        return accessDeniedTotal;
    }
    
    public Counter getAccessCheckErrorsTotal() {
        return accessCheckErrorsTotal;
    }
    
    public Histogram getAccessCheckDurationMs() {
        return accessCheckDurationMs;
    }
    
    public Gauge getActiveAccessChecks() {
        return activeAccessChecks;
    }
    
    // Ranger Admin API metrics getters
    
    public Counter getRangerApiCallsTotal() {
        return rangerApiCallsTotal;
    }
    
    public Counter getRangerApiErrorsTotal() {
        return rangerApiErrorsTotal;
    }
    
    public Histogram getRangerApiDurationMs() {
        return rangerApiDurationMs;
    }
    
    // Plugin status metrics getters
    
    public Gauge getMonitoringPluginRunning() {
        return monitoringPluginRunning;
    }
    
    public Gauge getAuthorizerInitialized() {
        return authorizerInitialized;
    }
}

