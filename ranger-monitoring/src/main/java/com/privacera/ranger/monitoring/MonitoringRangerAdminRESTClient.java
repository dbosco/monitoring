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
     * Override getServicePoliciesIfUpdated to intercept policy download calls.
     * This is the main method called by PolicyRefresher.
     */
    @Override
    public ServicePolicies getServicePoliciesIfUpdated(final long lastKnownVersion, final long lastActivationTimeInMillis) throws Exception {
        logApiCall("getServicePoliciesIfUpdated", "version=" + lastKnownVersion);
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
            logApiResponse("getServicePoliciesIfUpdated", duration, exception == null, 
                "hasPolicies=" + (result != null));
        }
    }
    
    /**
     * Override getServiceTagsIfUpdated to intercept tag download calls.
     */
    @Override
    public ServiceTags getServiceTagsIfUpdated(final long lastKnownVersion, final long lastActivationTimeInMillis) throws Exception {
        logApiCall("getServiceTagsIfUpdated", "version=" + lastKnownVersion);
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
            logApiResponse("getServiceTagsIfUpdated", duration, exception == null,
                "hasTags=" + (result != null));
        }
    }
    
    /**
     * Override getRolesIfUpdated to intercept role download calls.
     */
    @Override
    public RangerRoles getRolesIfUpdated(final long lastKnownRoleVersion, final long lastActivationTimeInMillis) throws Exception {
        logApiCall("getRolesIfUpdated", "version=" + lastKnownRoleVersion);
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
            logApiResponse("getRolesIfUpdated", duration, exception == null,
                "hasRoles=" + (result != null));
        }
    }
    
    /**
     * Override getUserStoreIfUpdated to intercept user store download calls.
     */
    @Override
    public RangerUserStore getUserStoreIfUpdated(final long lastKnownUserStoreVersion, final long lastActivationTimeInMillis) throws Exception {
        logApiCall("getUserStoreIfUpdated", "version=" + lastKnownUserStoreVersion);
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
            logApiResponse("getUserStoreIfUpdated", duration, exception == null,
                "hasUserStore=" + (result != null));
        }
    }
    
    /**
     * Override getGdsInfoIfUpdated to intercept GDS info download calls.
     */
    @Override
    public ServiceGdsInfo getGdsInfoIfUpdated(long lastKnownVersion, long lastActivationTimeInMillis) throws Exception {
        logApiCall("getGdsInfoIfUpdated", "version=" + lastKnownVersion);
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
            logApiResponse("getGdsInfoIfUpdated", duration, exception == null,
                "hasGdsInfo=" + (result != null));
        }
    }
    
    /**
     * Override createRole to intercept role creation calls.
     */
    @Override
    public RangerRole createRole(final RangerRole request) throws Exception {
        logApiCall("createRole", "roleName=" + (request != null ? request.getName() : "null"));
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
            logApiResponse("createRole", duration, exception == null, null);
        }
    }
    
    /**
     * Override dropRole to intercept role deletion calls.
     */
    @Override
    public void dropRole(final String execUser, final String roleName) throws Exception {
        logApiCall("dropRole", "roleName=" + roleName + ", execUser=" + execUser);
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.dropRole(execUser, roleName);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse("dropRole", duration, exception == null, null);
        }
    }
    
    /**
     * Override getUserRoles to intercept user role retrieval calls.
     */
    @Override
    public List<String> getUserRoles(final String execUser) throws Exception {
        logApiCall("getUserRoles", "execUser=" + execUser);
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
            logApiResponse("getUserRoles", duration, exception == null,
                "rolesCount=" + (result != null ? result.size() : 0));
        }
    }
    
    /**
     * Override getAllRoles to intercept all roles retrieval calls.
     */
    @Override
    public List<String> getAllRoles(final String execUser) throws Exception {
        logApiCall("getAllRoles", "execUser=" + execUser);
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
            logApiResponse("getAllRoles", duration, exception == null,
                "rolesCount=" + (result != null ? result.size() : 0));
        }
    }
    
    /**
     * Override getRole to intercept role retrieval calls.
     */
    @Override
    public RangerRole getRole(final String execUser, final String roleName) throws Exception {
        logApiCall("getRole", "roleName=" + roleName + ", execUser=" + execUser);
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
            logApiResponse("getRole", duration, exception == null, "hasRole=" + (result != null));
        }
    }
    
    /**
     * Override grantRole to intercept role grant calls.
     */
    @Override
    public void grantRole(final GrantRevokeRoleRequest request) throws Exception {
        logApiCall("grantRole", "request=" + (request != null ? request.getClass().getSimpleName() : "null"));
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.grantRole(request);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse("grantRole", duration, exception == null, null);
        }
    }
    
    /**
     * Override revokeRole to intercept role revoke calls.
     */
    @Override
    public void revokeRole(final GrantRevokeRoleRequest request) throws Exception {
        logApiCall("revokeRole", "request=" + (request != null ? request.getClass().getSimpleName() : "null"));
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.revokeRole(request);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse("revokeRole", duration, exception == null, null);
        }
    }
    
    /**
     * Override grantAccess to intercept access grant calls.
     */
    @Override
    public void grantAccess(final GrantRevokeRequest request) throws Exception {
        logApiCall("grantAccess", "request=" + (request != null ? request.getClass().getSimpleName() : "null"));
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.grantAccess(request);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse("grantAccess", duration, exception == null, null);
        }
    }
    
    /**
     * Override revokeAccess to intercept access revoke calls.
     */
    @Override
    public void revokeAccess(final GrantRevokeRequest request) throws Exception {
        logApiCall("revokeAccess", "request=" + (request != null ? request.getClass().getSimpleName() : "null"));
        long startTime = System.currentTimeMillis();
        Exception exception = null;
        try {
            super.revokeAccess(request);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logApiResponse("revokeAccess", duration, exception == null, null);
        }
    }
    
    /**
     * Override getTagTypes to intercept tag type retrieval calls.
     */
    @Override
    public List<String> getTagTypes(String pattern) throws Exception {
        logApiCall("getTagTypes", "pattern=" + pattern);
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
            logApiResponse("getTagTypes", duration, exception == null,
                "tagTypesCount=" + (result != null ? result.size() : 0));
        }
    }
    
    /**
     * Override getGroups to intercept group retrieval calls.
     */
    @Override
    public List<Map<String, Object>> getGroups() throws Exception {
        logApiCall("getGroups", null);
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
            logApiResponse("getGroups", duration, exception == null,
                "groupsCount=" + (result != null ? result.size() : 0));
        }
    }
    
    /**
     * Override getUsersByGroup to intercept user-by-group retrieval calls.
     */
    @Override
    public List<Map<String, Object>> getUsersByGroup(String groupName) throws Exception {
        logApiCall("getUsersByGroup", "groupName=" + groupName);
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
            logApiResponse("getUsersByGroup", duration, exception == null,
                "usersCount=" + (result != null ? result.size() : 0));
        }
    }
    
    /**
     * Override getGroupsForUser to intercept groups-for-user retrieval calls.
     */
    @Override
    public List<Map<String, Object>> getGroupsForUser(String username) throws Exception {
        logApiCall("getGroupsForUser", "username=" + username);
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
            logApiResponse("getGroupsForUser", duration, exception == null,
                "groupsCount=" + (result != null ? result.size() : 0));
        }
    }
    
    /**
     * Override getUserInfoByEmailAddress to intercept user info retrieval calls.
     */
    @Override
    public HashMap<String, Object> getUserInfoByEmailAddress(String emailAddress) throws Exception {
        logApiCall("getUserInfoByEmailAddress", "emailAddress=" + emailAddress);
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
            logApiResponse("getUserInfoByEmailAddress", duration, exception == null,
                "hasUserInfo=" + (result != null));
        }
    }
    
    /**
     * Override getUserAttributesForUserName to intercept user attribute retrieval calls.
     */
    @Override
    public Map<String, String> getUserAttributesForUserName(String userName) throws Exception {
        logApiCall("getUserAttributesForUserName", "userName=" + userName);
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
            logApiResponse("getUserAttributesForUserName", duration, exception == null,
                "attributesCount=" + (result != null ? result.size() : 0));
        }
    }
    
    /**
     * Override getUsersWithoutGroups to intercept users without groups retrieval calls.
     */
    @Override
    public List<Map<String, Object>> getUsersWithoutGroups() throws Exception {
        logApiCall("getUsersWithoutGroups", null);
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
            logApiResponse("getUsersWithoutGroups", duration, exception == null,
                "usersCount=" + (result != null ? result.size() : 0));
        }
    }
}

