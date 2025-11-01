package com.privacera.ranger.monitoring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.admin.client.RangerAdminRESTClient;
import org.apache.ranger.plugin.util.ServicePolicies;
import org.apache.ranger.plugin.util.ServiceTags;
import org.apache.ranger.plugin.util.RangerRoles;
import org.apache.ranger.plugin.util.RangerUserStore;
import org.apache.ranger.plugin.util.ServiceGdsInfo;
import org.apache.ranger.plugin.model.RangerRole;
import org.apache.ranger.plugin.util.GrantRevokeRequest;
import org.apache.ranger.plugin.util.GrantRevokeRoleRequest;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Extended RangerAdminRESTClient that intercepts all API calls at the admin client level.
 * This extends RangerAdminRESTClient and overrides all methods that make REST calls,
 * allowing us to intercept at a higher level than RangerRESTClient.
 */
public class MonitoringRangerAdminRESTClient extends RangerAdminRESTClient {
    
    private static final Log LOG = LogFactory.getLog(MonitoringRangerAdminRESTClient.class);
    
    // Prometheus metrics
    private final PrometheusMetricsRegistry metrics = PrometheusMetricsRegistry.getInstance();
    
    /**
     * Constructor matching the parent class.
     */
    public MonitoringRangerAdminRESTClient() {
        super();
        LOG.info("MonitoringRangerAdminRESTClient created");
    }
    
    /**
     * Helper method to log API call start.
     */
    private void logApiCall(String methodName, String params) {
        String logMsg = "[RANGER_API_CALL] " + methodName + (params != null ? " | " + params : "");
        LOG.info(logMsg);
    }
    
    /**
     * Helper method to log API call completion.
     */
    private void logApiResponse(String methodName, long durationMs, boolean success, String additionalInfo) {
        String logMsg = "[RANGER_API_RESPONSE] " + methodName + " | DURATION_MS: " + durationMs + 
                       " | SUCCESS: " + success + (additionalInfo != null ? " | " + additionalInfo : "");
        LOG.info(logMsg);
    }
    
    /**
     * Helper method to update Prometheus metrics for API calls.
     */
    private void updateApiMetrics(String methodName, long durationMs, Exception exception) {
        String status = exception == null ? "success" : "error";
        metrics.getRangerApiCallsTotal().labels(methodName, status).inc();
        metrics.getRangerApiDurationMs().labels(methodName).observe(durationMs);
        if (exception != null) {
            metrics.getRangerApiErrorsTotal().labels(methodName).inc();
        }
    }
    
    /**
     * Override getServicePoliciesIfUpdated to intercept policy download calls.
     * This is the main method called by PolicyRefresher.
     */
    @Override
    public ServicePolicies getServicePoliciesIfUpdated(final long lastKnownVersion, final long lastActivationTimeInMillis) throws Exception {
        String methodName = "getServicePoliciesIfUpdated";
        logApiCall(methodName, "version=" + lastKnownVersion);
        long startTime = System.currentTimeMillis();
        ServicePolicies result = null;
        Exception exception = null;
        try {
            result = super.getServicePoliciesIfUpdated(lastKnownVersion, lastActivationTimeInMillis);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null, 
                "hasPolicies=" + (result != null));
            
            // Update Prometheus metrics
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getServiceTagsIfUpdated to intercept tag download calls.
     */
    @Override
    public ServiceTags getServiceTagsIfUpdated(final long lastKnownVersion, final long lastActivationTimeInMillis) throws Exception {
        String methodName = "getServiceTagsIfUpdated";
        logApiCall(methodName, "version=" + lastKnownVersion);
        long startTime = System.currentTimeMillis();
        ServiceTags result = null;
        Exception exception = null;
        try {
            result = super.getServiceTagsIfUpdated(lastKnownVersion, lastActivationTimeInMillis);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "hasTags=" + (result != null));
            
            // Update Prometheus metrics
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getRolesIfUpdated to intercept role download calls.
     */
    @Override
    public RangerRoles getRolesIfUpdated(final long lastKnownRoleVersion, final long lastActivationTimeInMillis) throws Exception {
        String methodName = "getRolesIfUpdated";
        logApiCall(methodName, "version=" + lastKnownRoleVersion);
        long startTime = System.currentTimeMillis();
        RangerRoles result = null;
        Exception exception = null;
        try {
            result = super.getRolesIfUpdated(lastKnownRoleVersion, lastActivationTimeInMillis);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "hasRoles=" + (result != null));
            
            // Update Prometheus metrics
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getUserStoreIfUpdated to intercept user store download calls.
     */
    @Override
    public RangerUserStore getUserStoreIfUpdated(final long lastKnownUserStoreVersion, final long lastActivationTimeInMillis) throws Exception {
        String methodName = "getUserStoreIfUpdated";
        logApiCall(methodName, "version=" + lastKnownUserStoreVersion);
        long startTime = System.currentTimeMillis();
        RangerUserStore result = null;
        Exception exception = null;
        try {
            result = super.getUserStoreIfUpdated(lastKnownUserStoreVersion, lastActivationTimeInMillis);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "hasUserStore=" + (result != null));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getGdsInfoIfUpdated to intercept GDS info download calls.
     */
    @Override
    public ServiceGdsInfo getGdsInfoIfUpdated(long lastKnownVersion, long lastActivationTimeInMillis) throws Exception {
        String methodName = "getGdsInfoIfUpdated";
        logApiCall(methodName, "version=" + lastKnownVersion);
        long startTime = System.currentTimeMillis();
        ServiceGdsInfo result = null;
        Exception exception = null;
        try {
            result = super.getGdsInfoIfUpdated(lastKnownVersion, lastActivationTimeInMillis);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "hasGdsInfo=" + (result != null));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override createRole to intercept role creation calls.
     */
    @Override
    public RangerRole createRole(final RangerRole request) throws Exception {
        String methodName = "createRole";
        logApiCall(methodName, "roleName=" + (request != null ? request.getName() : "null"));
        long startTime = System.currentTimeMillis();
        RangerRole result = null;
        Exception exception = null;
        try {
            result = super.createRole(request);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null, null);
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override dropRole to intercept role deletion calls.
     */
    @Override
    public void dropRole(final String execUser, final String roleName) throws Exception {
        String methodName = "dropRole";
        logApiCall(methodName, "roleName=" + roleName + ", execUser=" + execUser);
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.dropRole(execUser, roleName);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null, null);
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getUserRoles to intercept user role retrieval calls.
     */
    @Override
    public List<String> getUserRoles(final String execUser) throws Exception {
        String methodName = "getUserRoles";
        logApiCall(methodName, "execUser=" + execUser);
        long startTime = System.currentTimeMillis();
        List<String> result = null;
        Exception exception = null;
        try {
            result = super.getUserRoles(execUser);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "rolesCount=" + (result != null ? result.size() : 0));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getAllRoles to intercept all roles retrieval calls.
     */
    @Override
    public List<String> getAllRoles(final String execUser) throws Exception {
        String methodName = "getAllRoles";
        logApiCall(methodName, "execUser=" + execUser);
        long startTime = System.currentTimeMillis();
        List<String> result = null;
        Exception exception = null;
        try {
            result = super.getAllRoles(execUser);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "rolesCount=" + (result != null ? result.size() : 0));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getRole to intercept role retrieval calls.
     */
    @Override
    public RangerRole getRole(final String execUser, final String roleName) throws Exception {
        String methodName = "getRole";
        logApiCall(methodName, "roleName=" + roleName + ", execUser=" + execUser);
        long startTime = System.currentTimeMillis();
        RangerRole result = null;
        Exception exception = null;
        try {
            result = super.getRole(execUser, roleName);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null, "hasRole=" + (result != null));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override grantRole to intercept role grant calls.
     */
    @Override
    public void grantRole(final GrantRevokeRoleRequest request) throws Exception {
        String methodName = "grantRole";
        logApiCall(methodName, "request=" + (request != null ? request.getClass().getSimpleName() : "null"));
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.grantRole(request);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null, null);
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override revokeRole to intercept role revoke calls.
     */
    @Override
    public void revokeRole(final GrantRevokeRoleRequest request) throws Exception {
        String methodName = "revokeRole";
        logApiCall(methodName, "request=" + (request != null ? request.getClass().getSimpleName() : "null"));
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.revokeRole(request);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null, null);
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override grantAccess to intercept access grant calls.
     */
    @Override
    public void grantAccess(final GrantRevokeRequest request) throws Exception {
        String methodName = "grantAccess";
        logApiCall(methodName, "request=" + (request != null ? request.getClass().getSimpleName() : "null"));
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.grantAccess(request);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null, null);
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override revokeAccess to intercept access revoke calls.
     */
    @Override
    public void revokeAccess(final GrantRevokeRequest request) throws Exception {
        String methodName = "revokeAccess";
        logApiCall(methodName, "request=" + (request != null ? request.getClass().getSimpleName() : "null"));
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.revokeAccess(request);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null, null);
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getTagTypes to intercept tag type retrieval calls.
     */
    @Override
    public List<String> getTagTypes(String pattern) throws Exception {
        String methodName = "getTagTypes";
        logApiCall(methodName, "pattern=" + pattern);
        long startTime = System.currentTimeMillis();
        List<String> result = null;
        Exception exception = null;
        try {
            result = super.getTagTypes(pattern);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "tagTypesCount=" + (result != null ? result.size() : 0));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getGroups to intercept group retrieval calls.
     */
    @Override
    public List<Map<String, Object>> getGroups() throws Exception {
        String methodName = "getGroups";
        logApiCall(methodName, null);
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> result = null;
        Exception exception = null;
        try {
            result = super.getGroups();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "groupsCount=" + (result != null ? result.size() : 0));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getUsersByGroup to intercept user-by-group retrieval calls.
     */
    @Override
    public List<Map<String, Object>> getUsersByGroup(String groupName) throws Exception {
        String methodName = "getUsersByGroup";
        logApiCall(methodName, "groupName=" + groupName);
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> result = null;
        Exception exception = null;
        try {
            result = super.getUsersByGroup(groupName);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "usersCount=" + (result != null ? result.size() : 0));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getGroupsForUser to intercept groups-for-user retrieval calls.
     */
    @Override
    public List<Map<String, Object>> getGroupsForUser(String username) throws Exception {
        String methodName = "getGroupsForUser";
        logApiCall(methodName, "username=" + username);
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> result = null;
        Exception exception = null;
        try {
            result = super.getGroupsForUser(username);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "groupsCount=" + (result != null ? result.size() : 0));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getUserInfoByEmailAddress to intercept user info retrieval calls.
     */
    @Override
    public HashMap<String, Object> getUserInfoByEmailAddress(String emailAddress) throws Exception {
        String methodName = "getUserInfoByEmailAddress";
        logApiCall(methodName, "emailAddress=" + emailAddress);
        long startTime = System.currentTimeMillis();
        HashMap<String, Object> result = null;
        Exception exception = null;
        try {
            result = super.getUserInfoByEmailAddress(emailAddress);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "hasUserInfo=" + (result != null));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getUserAttributesForUserName to intercept user attribute retrieval calls.
     */
    @Override
    public Map<String, String> getUserAttributesForUserName(String userName) throws Exception {
        String methodName = "getUserAttributesForUserName";
        logApiCall(methodName, "userName=" + userName);
        long startTime = System.currentTimeMillis();
        Map<String, String> result = null;
        Exception exception = null;
        try {
            result = super.getUserAttributesForUserName(userName);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "attributesCount=" + (result != null ? result.size() : 0));
            updateApiMetrics(methodName, duration, exception);
        }
    }
    
    /**
     * Override getUsersWithoutGroups to intercept users without groups retrieval calls.
     */
    @Override
    public List<Map<String, Object>> getUsersWithoutGroups() throws Exception {
        String methodName = "getUsersWithoutGroups";
        logApiCall(methodName, null);
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> result = null;
        Exception exception = null;
        try {
            result = super.getUsersWithoutGroups();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse(methodName, duration, exception == null,
                "usersCount=" + (result != null ? result.size() : 0));
            updateApiMetrics(methodName, duration, exception);
        }
    }
}

