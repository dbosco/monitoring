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
        updateApiMetrics(methodName, durationMs, exception, false);
    }
    
    /**
     * Helper method to update Prometheus metrics for API calls.
     * 
     * @param methodName Name of the method
     * @param durationMs Duration in milliseconds
     * @param exception Exception if any was thrown
     * @param potentialFailure true if null result was detected (indicates suppressed failure)
     */
    private void updateApiMetrics(String methodName, long durationMs, Exception exception, boolean potentialFailure) {
        // Consider it an error if there's an exception OR if there's a potential failure (null result)
        String status = (exception != null || potentialFailure) ? "error" : "success";
        metrics.getRangerApiCallsTotal().labels(methodName, status).inc();
        metrics.getRangerApiDurationMs().labels(methodName).observe(durationMs);
        if (exception != null || potentialFailure) {
            metrics.getRangerApiErrorsTotal().labels(methodName).inc();
        }
    }
    
    /**
     * Generic helper to detect and handle null results that may indicate suppressed failures.
     * The parent class methods can return null without throwing exceptions in failure scenarios.
     * 
     * @param result The result object that may be null
     * @param methodName Name of the method being monitored
     * @param contextParam Context information for logging (e.g., username, emailAddress)
     * @return true if result is null (potential failure), false otherwise
     */
    private boolean detectNullResult(Object result, String methodName, String contextParam) {
        if (result == null) {
            String context = contextParam != null ? " for " + contextParam : "";
            LOG.warn("[RANGER_API_WARNING] " + methodName + " returned null" + context + 
                    " - This could indicate a suppressed failure or valid 'not found' scenario");
            return true;
        }
        return false;
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
            boolean potentialFailure = detectNullResult(result, methodName, "version=" + lastKnownVersion);
            boolean success = exception == null && !potentialFailure;
            logApiResponse(methodName, duration, success, 
                "hasPolicies=" + (result != null) + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "version=" + lastKnownVersion);
            boolean success = exception == null && !potentialFailure;
            logApiResponse(methodName, duration, success,
                "hasTags=" + (result != null) + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "version=" + lastKnownRoleVersion);
            boolean success = exception == null && !potentialFailure;
            logApiResponse(methodName, duration, success,
                "hasRoles=" + (result != null) + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "version=" + lastKnownUserStoreVersion);
            boolean success = exception == null && !potentialFailure;
            logApiResponse(methodName, duration, success,
                "hasUserStore=" + (result != null) + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "version=" + lastKnownVersion);
            boolean success = exception == null && !potentialFailure;
            logApiResponse(methodName, duration, success,
                "hasGdsInfo=" + (result != null) + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "roleName=" + (request != null ? request.getName() : "null"));
            boolean success = exception == null && !potentialFailure;
            logApiResponse(methodName, duration, success, 
                (potentialFailure ? "nullResult=true" : null));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "execUser=" + execUser);
            boolean success = exception == null && !potentialFailure;
            String sizeInfo = result != null ? String.valueOf(result.size()) : "0";
            logApiResponse(methodName, duration, success,
                "rolesCount=" + sizeInfo + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "execUser=" + execUser);
            boolean success = exception == null && !potentialFailure;
            String sizeInfo = result != null ? String.valueOf(result.size()) : "0";
            logApiResponse(methodName, duration, success,
                "rolesCount=" + sizeInfo + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "roleName=" + roleName + ", execUser=" + execUser);
            boolean success = exception == null && !potentialFailure;
            logApiResponse(methodName, duration, success, 
                "hasRole=" + (result != null) + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "pattern=" + pattern);
            boolean success = exception == null && !potentialFailure;
            String sizeInfo = result != null ? String.valueOf(result.size()) : "0";
            logApiResponse(methodName, duration, success,
                "tagTypesCount=" + sizeInfo + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
        }
    }
    
    /**
     * Override getGroups to intercept group retrieval calls.
     * Note: The parent method can return null without throwing an exception when
     * max retries are exceeded or other failures occur.
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
            boolean potentialFailure = detectNullResult(result, methodName, null);
            boolean success = exception == null && !potentialFailure;
            String sizeInfo = result != null ? String.valueOf(result.size()) : "0";
            logApiResponse(methodName, duration, success,
                "groupsCount=" + sizeInfo + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
        }
    }
    
    /**
     * Override getUsersByGroup to intercept user-by-group retrieval calls.
     * Note: The parent method can return null without throwing an exception when
     * max retries are exceeded or other failures occur.
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
            boolean potentialFailure = detectNullResult(result, methodName, "groupName: " + groupName);
            boolean success = exception == null && !potentialFailure;
            String sizeInfo = result != null ? String.valueOf(result.size()) : "0";
            logApiResponse(methodName, duration, success,
                "usersCount=" + sizeInfo + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
        }
    }
    
    /**
     * Override getGroupsForUser to intercept groups-for-user retrieval calls.
     * 
     * CRITICAL: The parent method suppresses exceptions and returns null in several failure scenarios:
     * 
     * 1. Exception swallowed after max retries (catch block): When max retries are exceeded during
     *    exception handling, the exception is only logged but NOT thrown, returning null instead.
     * 
     * 2. Silent failure after retry loop: When retries == maxSearchRetries but retry flag is false
     *    (or response is null), only logs warning and returns null without throwing.
     * 
     * 3. HTTP 400 (Bad Request): All 400 responses return null without exception. While "user not found"
     *    is acceptable, other 400 errors (malformed requests, validation failures) are also silently ignored.
     * 
     * 4. Connection errors: After all retries exhausted, connection exceptions are caught and swallowed,
     *    returning null instead of propagating the failure.
     * 
     * This wrapper detects all null results as potential failures and:
     * - Logs warnings for visibility
     * - Increments error metrics for monitoring
     * - Marks the call as failed in success tracking
     * Note: We cannot distinguish between "user not found" (valid null) vs "request failure" (invalid null),
     * so all nulls are treated as potential failures for monitoring purposes.
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
            boolean potentialFailure = detectNullResult(result, methodName, "username: " + username);
            boolean success = exception == null && !potentialFailure;
            String sizeInfo = result != null ? String.valueOf(result.size()) : "0";
            logApiResponse(methodName, duration, success,
                "groupsCount=" + sizeInfo + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
        }
    }
    
    /**
     * Override getUserInfoByEmailAddress to intercept user info retrieval calls.
     * Note: The parent method can return null without throwing an exception when
     * max retries are exceeded or other failures occur.
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
            boolean potentialFailure = detectNullResult(result, methodName, "emailAddress: " + emailAddress);
            boolean success = exception == null && !potentialFailure;
            logApiResponse(methodName, duration, success,
                "hasUserInfo=" + (result != null) + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, "userName=" + userName);
            boolean success = exception == null && !potentialFailure;
            String sizeInfo = result != null ? String.valueOf(result.size()) : "0";
            logApiResponse(methodName, duration, success,
                "attributesCount=" + sizeInfo + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
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
            boolean potentialFailure = detectNullResult(result, methodName, null);
            boolean success = exception == null && !potentialFailure;
            String sizeInfo = result != null ? String.valueOf(result.size()) : "0";
            logApiResponse(methodName, duration, success,
                "usersCount=" + sizeInfo + (potentialFailure ? " | nullResult=true" : ""));
            updateApiMetrics(methodName, duration, exception, potentialFailure);
        }
    }
}

