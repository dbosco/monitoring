package com.privacera.ranger.monitoring;

import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.plugin.policyengine.RangerAccessRequestImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResourceImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResult;
import org.apache.ranger.plugin.service.RangerBasePlugin;
import org.apache.ranger.plugin.audit.RangerDefaultAuditHandler;
import org.apache.ranger.plugin.util.PolicyRefresher;

/**
 * DummyAuthorizer class for testing Ranger authorization functionality.
 * This is a simplified version of APIHiveAuthorizer for monitoring and testing purposes.
 */
public class DummyAuthorizer {
    
    private static final Log LOG = LogFactory.getLog(DummyAuthorizer.class.getName());
    
    private String RANGER_APP_ID = "privacera_hive";
    private RangerBasePlugin rangerPlugin = null;
    
    // Prometheus metrics
    private final PrometheusMetricsRegistry metrics = PrometheusMetricsRegistry.getInstance();
    
    /**
     * Constructor for DummyAuthorizer.
     * Initializes the Ranger plugin for authorization testing.
     */
    public DummyAuthorizer() {
        if (rangerPlugin == null) {
            LOG.info("Initializing DummyAuthorizer with RangerBasePlugin hive, appId=" + RANGER_APP_ID);
            
            try {
                // Add this before creating RangerBasePlugin
                // Check the actual classpath location
                URL configUrl = getClass().getClassLoader().getResource("ranger-hive-privacera_hive-security.xml");
                if (configUrl != null) {
                    File configFile = new File(configUrl.getPath());
                    System.out.println("Config file exists at classpath location: " + configFile.exists());
                    System.out.println("Config file classpath path: " + configFile.getAbsolutePath());
                } else {
                    System.out.println("Config file not found in classpath");
                }
                System.out.println("Config file URL: " + configUrl);

                // Also check if file exists in filesystem
                File configFile = new File("ranger-hive-privacera_hive-security.xml");
                System.out.println("Config file exists: " + configFile.exists());
                System.out.println("Config file path: " + configFile.getAbsolutePath());
                System.out.println("Config file value: " + configFile);
                //System.exit(-1);
                LOG.info("Creating RangerBasePlugin instance...");
                // Construct the plugin and initialize it
                rangerPlugin = new RangerBasePlugin("hive", RANGER_APP_ID, RANGER_APP_ID);
                
                LOG.info("RangerBasePlugin created successfully, calling init()...");
                rangerPlugin.init();
                
                LOG.info("RangerBasePlugin initialized, setting audit handler...");
                rangerPlugin.setResultProcessor(new RangerDefaultAuditHandler());
                
                // Verify that MonitoringRangerAdminRESTClient is being used
                verifyMonitoringClient();
                
                // Update metrics
                metrics.getAuthorizerInitialized().set(1.0);
                
                LOG.info("DummyAuthorizer initialized successfully");
            } catch (Exception e) {
                LOG.error("Failed to initialize DummyAuthorizer: " + e.getMessage(), e);
                e.printStackTrace();
                rangerPlugin = null;
                metrics.getAuthorizerInitialized().set(0.0);
            }
        }
    }
    
    /**
     * Checks access permissions for a given user, groups, database, table, and column.
     * 
     * @param user the username requesting access
     * @param groups set of groups the user belongs to
     * @param database the database name
     * @param table the table name
     * @param column the column name
     * @return true if access is allowed, false otherwise
     */
    public boolean checkAccess(String user, Set<String> groups, String database, String table, String column) {
        LOG.info("Checking access for user: " + user + ", database: " + database + ", table: " + table + ", column: " + column);
        
        if (rangerPlugin == null) {
            LOG.error("Ranger plugin is not initialized. Cannot check access.");
            return false;
        }
        
        try {
            RangerAccessRequestImpl rangerHiveAccessRequest = createRangerHiveAccessRequest(user, groups, database, table, column);
            RangerAccessResult rangerAccessResult = rangerPlugin.isAccessAllowed(rangerHiveAccessRequest);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("RangerAccessResult: " + rangerAccessResult);
                LOG.debug("Returning AccessAllowed = " + rangerAccessResult.getIsAllowed());
            }
            
            boolean isAllowed = rangerAccessResult.getIsAllowed();
            LOG.info("Access check result: " + (isAllowed ? "ALLOWED" : "DENIED"));
            
            return isAllowed;
            
        } catch (Exception e) {
            LOG.error("Error during access check: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Creates a Ranger access request for Hive resources.
     * 
     * @param user the username
     * @param groups set of user groups
     * @param database the database name
     * @param table the table name
     * @param column the column name
     * @return RangerAccessRequestImpl object
     */
    protected RangerAccessRequestImpl createRangerHiveAccessRequest(String user, Set<String> groups, String database,
            String table, String column) {
        
        final String ACCESS_TYPE_SELECT = "select";
        final String OPERATION_TYPE_QUERY = "query";
        final String CLUSTER_NAME = "api-service";
        final String KEY_DATABASE = "database";
        final String KEY_TABLE = "table";
        final String KEY_COLUMN = "column";
        
        String log_str = "user=" + user + ", groups=" + groups + ", database=" + database + ", table=" + table + ", column=" + column;
        LOG.debug("Creating Ranger access request: " + log_str);
        
        RangerAccessResourceImpl resource = new RangerAccessResourceImpl();
        
        // Set database
        if (database != null && database.trim().length() > 0) {
            resource.setValue(KEY_DATABASE, database.trim().toLowerCase());
        } else {
            LOG.error("Database can't be null. " + log_str);
            throw new IllegalArgumentException("Database cannot be null or empty");
        }
        
        // Set table
        if (table != null && table.trim().length() > 0) {
            resource.setValue(KEY_TABLE, table.trim().toLowerCase());
        }
        
        // Set column
        if (column != null && column.trim().length() > 0) {
            resource.setValue(KEY_COLUMN, column.trim().toLowerCase());
        }
        
        // Create the access request
        RangerAccessRequestImpl rangerRequest = new RangerAccessRequestImpl();
        rangerRequest.setAccessType(ACCESS_TYPE_SELECT);
        rangerRequest.setResource(resource);
        rangerRequest.setUser(user);
        // rangerRequest.setUserGroups(groups != null ? groups : new HashSet<String>());
        rangerRequest.setAccessTime(new Date());
        rangerRequest.setAction(OPERATION_TYPE_QUERY);
        rangerRequest.setClusterName(CLUSTER_NAME);
        
        return rangerRequest;
    }
    
    /**
     * Checks if the Ranger plugin is properly initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return rangerPlugin != null;
    }
    
    /**
     * Gets the application ID being used.
     * 
     * @return the Ranger application ID
     */
    public String getAppId() {
        return RANGER_APP_ID;
    }
    
    /**
     * Verifies that MonitoringRangerAdminRESTClient is being used.
     * Since we're using configuration-based approach, this just verifies the setup.
     */
    private void verifyMonitoringClient() {
        try {
            PolicyRefresher refresher = rangerPlugin.getPolicyRefresher();
            if (refresher == null) {
                LOG.warn("PolicyRefresher is null, cannot verify monitoring client");
                return;
            }
            
            Object adminClient = refresher.getRangerAdminClient();
            if (adminClient == null) {
                LOG.warn("RangerAdminClient is null, cannot verify monitoring client");
                return;
            }
            
            String clientType = adminClient.getClass().getName();
            LOG.info("RangerAdminClient type: " + clientType);
            
            if (adminClient instanceof com.privacera.ranger.monitoring.MonitoringRangerAdminRESTClient) {
                LOG.info("✓ VERIFIED: MonitoringRangerAdminRESTClient is being used");
                System.out.println("✓ VERIFIED: MonitoringRangerAdminRESTClient is being used");
            } else {
                LOG.warn("⚠ WARNING: Expected MonitoringRangerAdminRESTClient but got: " + clientType);
                LOG.warn("⚠ Make sure ranger.plugin.hive.policy.source.impl is set to com.privacera.ranger.monitoring.MonitoringRangerAdminRESTClient");
            }
        } catch (Exception e) {
            LOG.error("Failed to verify monitoring client: " + e.getMessage(), e);
        }
    }
    
    /**
     * Main method for testing the DummyAuthorizer functionality.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        LOG.info("Starting DummyAuthorizer test...");
        
        DummyAuthorizer authorizer = new DummyAuthorizer();
        
        if (!authorizer.isInitialized()) {
            LOG.error("Failed to initialize DummyAuthorizer");
            System.exit(1);
        }
        
        // Test data
        String user = "test_user1";
        Set<String> groups = new HashSet<>();
        // groups.add("testgroup");
        // groups.add("admin");
        
        String database = "test_db1";
        String table = "test_table1";
        String column = "test_col1";
        
        // Use command line arguments if provided
        if (args.length >= 5) {
            user = args[0];
            groups = new HashSet<>();
            groups.add(args[1]);
            database = args[2];
            table = args[3];
            column = args[4];
        }
        
        LOG.info("Testing access check...");
        boolean result = authorizer.checkAccess(user, groups, database, table, column);
        
        LOG.info("Test completed. Access result: " + (result ? "ALLOWED" : "DENIED"));
        System.out.println("DummyAuthorizer test completed successfully!");
        System.out.println("Access result: " + (result ? "ALLOWED" : "DENIED"));
    }
}