package com.buildstash;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.model.AbstractProject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to automatically detect version control information from Jenkins builds.
 * Uses reflection to avoid hard dependencies on specific SCM plugins.
 */
public class VersionControlDetector {

    /**
     * Detects and populates version control information from a Jenkins build.
     * Only populates fields that are null or empty (allows manual override).
     */
    public static void populateVersionControlInfo(Run<?, ?> build, BuildstashUploadRequest request) {
        populateVersionControlInfo(build, request, null);
    }

    /**
     * Detects and populates version control information from a Jenkins build.
     * Only populates fields that are null or empty (allows manual override).
     */
    public static void populateVersionControlInfo(Run<?, ?> build, BuildstashUploadRequest request, TaskListener listener) {
        try {
            // Get SCM from the build's parent project
            SCM scm = null;
            try {
                if (build.getParent() instanceof AbstractProject) {
                    AbstractProject<?, ?> project = (AbstractProject<?, ?>) build.getParent();
                    scm = project.getScm();
                }
            } catch (Exception e) {
                // Ignore - SCM might not be available
            }

            if (scm == null) {
                // For pipelines, BuildData is the most reliable source for Git information
                populateFromBuildData(build, request);
                return;
            }

            // Detect SCM type
            String detectedHostType = detectHostType(scm);
            if (detectedHostType != null && isEmpty(request.getVcHostType())) {
                request.setVcHostType(detectedHostType);
            }

            // Get repository URL
            String repoUrl = getRepositoryUrl(build, scm);
            if (repoUrl != null && !repoUrl.isBlank()) {
                // Detect host from URL
                String detectedHost = detectHostFromUrl(repoUrl);
                if (detectedHost != null && isEmpty(request.getVcHost())) {
                    request.setVcHost(detectedHost);
                }

                // Extract repo name from URL
                String detectedRepoName = extractRepoNameFromUrl(repoUrl);
                if (detectedRepoName != null && isEmpty(request.getVcRepoName())) {
                    request.setVcRepoName(detectedRepoName);
                }

                // Set repo URL if not already set
                if (isEmpty(request.getVcRepoUrl())) {
                    request.setVcRepoUrl(repoUrl);
                }
            }

            // Get branch information (try multiple methods)
            String detectedBranch = getBranch(build, scm);
            if (detectedBranch != null && isEmpty(request.getVcBranch())) {
                request.setVcBranch(detectedBranch);
            }

            // Get commit SHA (try multiple methods)
            if (isEmpty(request.getVcCommitSha())) {
                // For SVN, try SCM-specific methods first
                String detectedCommitSha = null;
                if (detectedHostType != null && detectedHostType.equals("svn")) {
                    detectedCommitSha = getSvnRevision(build, scm, listener);
                }
                // Fall back to generic method if SVN-specific didn't work
                if (detectedCommitSha == null || detectedCommitSha.isBlank()) {
                    detectedCommitSha = getCommitSha(build);
                }
                if (detectedCommitSha != null && !detectedCommitSha.isBlank()) {
                    request.setVcCommitSha(detectedCommitSha);
                }
            }

            // Generate commit URL if we have repo URL and commit SHA
            if (isEmpty(request.getVcCommitUrl())) {
                String finalCommitSha = request.getVcCommitSha();
                String finalRepoUrl = request.getVcRepoUrl();
                String finalHostType = request.getVcHostType();
                if (finalCommitSha != null && finalRepoUrl != null) {
                    String commitUrl = generateCommitUrl(finalRepoUrl, finalCommitSha, finalHostType);
                    if (commitUrl != null) {
                        request.setVcCommitUrl(commitUrl);
                    }
                }
            }

        } catch (Exception e) {
            // Silently fail - don't break the build if VC detection fails
            // This allows the build to continue even if VC info can't be detected
        }
    }


    /**
     * Helper to check if a string is null or empty.
     */
    private static boolean isEmpty(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Populates SCM info from BuildData (for pipelines).
     * This is more reliable than environment variables for Git information.
     */
    private static void populateFromBuildData(Run<?, ?> build, BuildstashUploadRequest request) {
        try {
            // Try to get BuildData from the build
            // In pipelines, BuildData is in actions, so check all actions first
            Object buildData = null;
            Class<?> buildDataClass = null;
            
            // First, check all actions to find BuildData (works even if Class.forName fails)
            try {
                java.util.Collection<?> actions = build.getAllActions();
                // Look for BuildData by checking class name (more reliable than Class.forName)
                for (Object action : actions) {
                    if (action != null) {
                        String actionClass = action.getClass().getName();
                        // Check if it's BuildData by class name
                        if (actionClass.equals("hudson.plugins.git.util.BuildData") || 
                            actionClass.equals("org.jenkinsci.plugins.git.util.BuildData")) {
                            buildData = action;
                            buildDataClass = action.getClass();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            
            // If not found in actions, try getAction with Class.forName
            if (buildData == null) {
                try {
                    buildDataClass = Class.forName("hudson.plugins.git.util.BuildData");
                    java.lang.reflect.Method getActionMethod = build.getClass().getMethod("getAction", Class.class);
                    buildData = getActionMethod.invoke(build, buildDataClass);
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            if (buildData == null) {
                return;
            }
            
            // If we found BuildData but don't have the class, get it from the object
            if (buildDataClass == null) {
                buildDataClass = buildData.getClass();
            }

            // Get repository URL from BuildData
            if (isEmpty(request.getVcRepoUrl())) {
                try {
                    java.lang.reflect.Method getRemoteUrls = buildDataClass.getMethod("getRemoteUrls");
                    Object remoteUrls = getRemoteUrls.invoke(buildData);
                    if (remoteUrls != null) {
                        String repoUrl = null;
                        
                        // Handle Collection (HashSet, List, etc.)
                        if (remoteUrls instanceof java.util.Collection) {
                            java.util.Collection<?> collection = (java.util.Collection<?>) remoteUrls;
                            if (!collection.isEmpty()) {
                                Object firstElement = collection.iterator().next();
                                repoUrl = firstElement != null ? firstElement.toString() : null;
                            }
                        } else if (remoteUrls instanceof java.util.Map) {
                            java.util.Map<?, ?> map = (java.util.Map<?, ?>) remoteUrls;
                            if (!map.isEmpty()) {
                                // Try values first (URLs are typically values)
                                Object firstValue = map.values().iterator().next();
                                repoUrl = firstValue != null ? firstValue.toString() : null;
                                // If that doesn't work, try keys
                                if (repoUrl == null || repoUrl.isBlank()) {
                                    Object firstKey = map.keySet().iterator().next();
                                    repoUrl = firstKey != null ? firstKey.toString() : null;
                                }
                            }
                        }
                        
                        if (repoUrl != null && !repoUrl.isBlank()) {
                            request.setVcRepoUrl(repoUrl);
                            // Detect host and repo name from URL
                            if (isEmpty(request.getVcHost())) {
                                String host = detectHostFromUrl(repoUrl);
                                if (host != null) {
                                    request.setVcHost(host);
                                }
                            }
                            if (isEmpty(request.getVcRepoName())) {
                                String repoName = extractRepoNameFromUrl(repoUrl);
                                if (repoName != null) {
                                    request.setVcRepoName(repoName);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore - extraction failed
                }
            }

            // Get branch from BuildData
            if (isEmpty(request.getVcBranch())) {
                // Try getBuildsByBranchName first (more reliable for pipelines)
                try {
                    java.lang.reflect.Method getBuildsByBranchName = buildDataClass.getMethod("getBuildsByBranchName");
                    Object buildsByBranch = getBuildsByBranchName.invoke(buildData);
                    if (buildsByBranch != null && buildsByBranch instanceof java.util.Map) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) buildsByBranch;
                        if (!map.isEmpty()) {
                            Object firstKey = map.keySet().iterator().next();
                            String branchName = firstKey != null ? firstKey.toString() : null;
                            if (branchName != null && !branchName.isBlank()) {
                                // Clean up branch name
                                branchName = branchName
                                    .replaceAll("^refs/remotes/origin/", "")
                                    .replaceAll("^refs/heads/", "")
                                    .replaceAll("^origin/", "")
                                    .replaceAll("^remotes/origin/", "")
                                    .replaceAll("^\\*/", "")
                                    .replaceAll("^\\*", "");
                                request.setVcBranch(branchName);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Try getLastBuiltRevision as fallback
                    try {
                        java.lang.reflect.Method getLastBuiltRevision = buildDataClass.getMethod("getLastBuiltRevision");
                        Object revision = getLastBuiltRevision.invoke(buildData);
                        if (revision != null) {
                            try {
                                java.lang.reflect.Method getBranch = revision.getClass().getMethod("getBranch");
                                Object branch = getBranch.invoke(revision);
                                if (branch != null) {
                                    try {
                                        java.lang.reflect.Method getName = branch.getClass().getMethod("getName");
                                        Object nameObj = getName.invoke(branch);
                                        String branchName = nameObj != null ? nameObj.toString() : null;
                                        if (branchName != null && !branchName.isBlank()) {
                                            // Clean up branch name
                                            branchName = branchName
                                                .replaceAll("^refs/remotes/origin/", "")
                                                .replaceAll("^refs/heads/", "")
                                                .replaceAll("^origin/", "")
                                                .replaceAll("^remotes/origin/", "")
                                                .replaceAll("^\\*/", "")
                                                .replaceAll("^\\*", "");
                                            request.setVcBranch(branchName);
                                        }
                                    } catch (Exception e2) {
                                        // Ignore
                                    }
                                }
                            } catch (Exception e2) {
                                // Ignore
                            }
                        }
                    } catch (Exception e2) {
                        // Ignore
                    }
                }
            }

            // Get commit SHA from BuildData
            if (isEmpty(request.getVcCommitSha())) {
                String commitSha = getCommitSha(build);
                if (commitSha != null && !commitSha.isBlank()) {
                    request.setVcCommitSha(commitSha);
                }
            }

            // Generate commit URL if we have repo URL and commit SHA
            if (isEmpty(request.getVcCommitUrl())) {
                String finalCommitSha = request.getVcCommitSha();
                String finalRepoUrl = request.getVcRepoUrl();
                String finalHostType = request.getVcHostType();
                if (finalCommitSha != null && finalRepoUrl != null) {
                    String commitUrl = generateCommitUrl(finalRepoUrl, finalCommitSha, finalHostType);
                    if (commitUrl != null) {
                        request.setVcCommitUrl(commitUrl);
                    }
                }
            }

            // Set host type if not set
            if (isEmpty(request.getVcHostType())) {
                request.setVcHostType("git");
            }
        } catch (Exception e) {
            // Ignore - Git plugin might not be available
        }
    }


    /**
     * Detects the SCM host type (git, svn, hg, etc.) from the SCM object.
     */
    private static String detectHostType(SCM scm) {
        String scmClass = scm.getClass().getName();
        String scmClassLower = scmClass.toLowerCase();
        
        if (scmClassLower.contains("git")) {
            return "git";
        } else if (scmClassLower.contains("svn") || scmClassLower.contains("subversion")) {
            return "svn";
        } else if (scmClassLower.contains("mercurial") || scmClassLower.contains("hg")) {
            return "hg";
        } else if (scmClassLower.contains("bazaar") || scmClassLower.contains("bzr")) {
            return "bzr";
        } else if (scmClassLower.contains("perforce")) {
            return "perforce";
        } else if (scmClassLower.contains("cvs")) {
            return "cvs";
        }
        
        return null;
    }

    /**
     * Gets the repository URL from the build/SCM using reflection.
     */
    private static String getRepositoryUrl(Run<?, ?> build, SCM scm) {
        String scmClass = scm.getClass().getName();
        
        // Try to get from GitSCM using reflection
        try {
            if (scmClass.contains("GitSCM")) {
                // Try getUserRemoteConfigs() method
                try {
                    java.lang.reflect.Method getUserRemoteConfigs = scm.getClass().getMethod("getUserRemoteConfigs");
                    Object configs = getUserRemoteConfigs.invoke(scm);
                    if (configs instanceof Collection) {
                        Collection<?> collection = (Collection<?>) configs;
                        if (!collection.isEmpty()) {
                            Object config = collection.iterator().next();
                            // Try getUrl() method
                            try {
                                java.lang.reflect.Method getUrl = config.getClass().getMethod("getUrl");
                                String url = (String) getUrl.invoke(config);
                                if (url != null && !url.isBlank()) {
                                    return url;
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            } else if (scmClass.contains("Perforce")) {
                // Try to get Perforce depot path
                try {
                    // Try getDepotPath() or similar methods
                    try {
                        java.lang.reflect.Method getDepotPath = scm.getClass().getMethod("getDepotPath");
                        String depotPath = (String) getDepotPath.invoke(scm);
                        if (depotPath != null && !depotPath.isBlank()) {
                            // Try to get port to construct full URL
                            try {
                                java.lang.reflect.Method getP4Port = scm.getClass().getMethod("getP4Port");
                                String p4Port = (String) getP4Port.invoke(scm);
                                if (p4Port != null && !p4Port.isBlank()) {
                                    return p4Port + "/" + depotPath;
                                }
                            } catch (Exception e) {
                                // Just return depot path
                            }
                            return depotPath;
                        }
                    } catch (Exception e) {
                        // Try alternative method names
                        try {
                            java.lang.reflect.Method getDepot = scm.getClass().getMethod("getDepot");
                            String depot = (String) getDepot.invoke(scm);
                            if (depot != null && !depot.isBlank()) {
                                return depot;
                            }
                        } catch (Exception e2) {
                            // Ignore
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            } else if (scmClass.contains("SubversionSCM") || scmClass.contains("SVN")) {
                // Try to get SVN remote URL
                try {
                    // Try getLocations() or getModules() method
                    try {
                        java.lang.reflect.Method getLocations = scm.getClass().getMethod("getLocations");
                        Object locations = getLocations.invoke(scm);
                        
                        // Handle both arrays and collections
                        Object[] locationArray = null;
                        if (locations != null) {
                            if (locations instanceof Collection) {
                                Collection<?> collection = (Collection<?>) locations;
                                if (!collection.isEmpty()) {
                                    locationArray = collection.toArray();
                                }
                            } else if (locations.getClass().isArray()) {
                                int length = java.lang.reflect.Array.getLength(locations);
                                locationArray = new Object[length];
                                for (int i = 0; i < length; i++) {
                                    locationArray[i] = java.lang.reflect.Array.get(locations, i);
                                }
                            }
                        }
                        
                        if (locationArray != null && locationArray.length > 0) {
                            Object location = locationArray[0];
                            if (location != null) {
                                // Try getRemote() or getURL() method
                                try {
                                    java.lang.reflect.Method getRemote = location.getClass().getMethod("getRemote");
                                    String url = (String) getRemote.invoke(location);
                                    if (url != null && !url.isBlank()) {
                                        return url;
                                    }
                                } catch (Exception e) {
                                    try {
                                        java.lang.reflect.Method getURL = location.getClass().getMethod("getURL");
                                        String url = (String) getURL.invoke(location);
                                        if (url != null && !url.isBlank()) {
                                            return url;
                                        }
                                    } catch (Exception e2) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Try alternative: getModules()
                        try {
                            java.lang.reflect.Method getModules = scm.getClass().getMethod("getModules");
                            Object modules = getModules.invoke(scm);
                            if (modules instanceof Collection) {
                                Collection<?> collection = (Collection<?>) modules;
                                if (!collection.isEmpty()) {
                                    Object module = collection.iterator().next();
                                    // Try getURL() or getRemote()
                                    try {
                                        java.lang.reflect.Method getURL = module.getClass().getMethod("getURL");
                                        String url = (String) getURL.invoke(module);
                                        if (url != null && !url.isBlank()) {
                                            return url;
                                        }
                                    } catch (Exception e2) {
                                        // Ignore
                                    }
                                }
                            }
                        } catch (Exception e2) {
                            // Ignore
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try to get from BuildData action (for Git) using reflection
        try {
            Class<?> buildDataClass = Class.forName("hudson.plugins.git.util.BuildData");
            java.lang.reflect.Method getActionMethod = build.getClass().getMethod("getAction", Class.class);
            Object buildData = getActionMethod.invoke(build, buildDataClass);
            if (buildData != null) {
                try {
                    java.lang.reflect.Method getRemoteUrls = buildDataClass.getMethod("getRemoteUrls");
                    Object remoteUrls = getRemoteUrls.invoke(buildData);
                    if (remoteUrls instanceof java.util.Map) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) remoteUrls;
                        if (!map.isEmpty()) {
                            return (String) map.values().iterator().next();
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    /**
     * Detects the VC host (github, gitlab, etc.) from a repository URL.
     */
    private static String detectHostFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String lowerUrl = url.toLowerCase();

        // GitHub
        if (lowerUrl.contains("github.com")) {
            return "github";
        }
        // GitLab - check hosted first, then self-hosted
        if (lowerUrl.contains("gitlab.com")) {
            return "gitlab";
        }
        if (lowerUrl.contains("gitlab")) {
            return "gitlab-self";
        }
        // Bitbucket - check Cloud first, then Server/self-hosted
        if (lowerUrl.contains("bitbucket.org")) {
            return "bitbucket";
        }
        // Azure Repos
        if (lowerUrl.contains("azure.com") || lowerUrl.contains("visualstudio.com") || lowerUrl.contains("dev.azure.com")) {
            return "azure-repos";
        }
        // Gitea
        if (lowerUrl.contains("gitea")) {
            return "gitea";
        }
        // Forgejo
        if (lowerUrl.contains("forgejo")) {
            return "forgejo";
        }
        // Gogs
        if (lowerUrl.contains("gogs")) {
            return "gogs";
        }
        // Codeberg
        if (lowerUrl.contains("codeberg")) {
            return "codeberg";
        }
        // SourceForge
        if (lowerUrl.contains("sourceforge.com")) {
            return "sourceforge";
        }
        // Sourcehut
        if (lowerUrl.contains("sourcehut") || lowerUrl.contains("sr.ht")) {
            return "sourcehut";
        }
        // AWS CodeCommit
        if (lowerUrl.contains("codecommit")) {
            return "aws-codecommit";
        }
        // Perforce
        if (lowerUrl.contains("perforce")) {
            return "perforce";
        }
        // Gitee
        if (lowerUrl.contains("gitee")) {
            return "gitee";
        }
        // RiouxSVN (custom SVN host)
        if (lowerUrl.contains("svn.riouxsvn.com") || lowerUrl.contains("riouxsvn")) {
            return "riouxsvn";
        }

        return null;
    }

    /**
     * Extracts the repository name from a repository URL.
     * Handles various formats:
     * - GitHub/GitLab Cloud: https://host.com/user/repo.git -> repo
     * - GitLab self-hosted: https://gitlab.example.com/group/subgroup/repo.git -> repo
     * - Bitbucket Cloud: https://bitbucket.org/user/repo.git -> repo
     * - Bitbucket Server: https://bitbucket.example.com/scm/project/repo.git -> repo
     * - Bitbucket Server: https://bitbucket.example.com/projects/PROJECT/repos/repo.git -> repo
     */
    private static String extractRepoNameFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            // Remove .git suffix if present
            url = url.replaceAll("\\.git$", "");
            
            // Detect host type to handle special cases
            String host = detectHostFromUrl(url);
            String lowerUrl = url.toLowerCase();
            
            // Try to parse as URI
            URI uri = new URI(url);
            String path = uri.getPath();
            
            if (path != null && !path.isEmpty()) {
                // Remove leading slash
                path = path.startsWith("/") ? path.substring(1) : path;
                
                // Handle Azure DevOps special format
                if ("azure-repos".equals(host) && lowerUrl.contains("/_git/")) {
                    // Format: /TEAMNAME/PROJECTNAME/_git/REPO-NAME
                    String[] parts = path.split("/");
                    // Find the part after /_git/
                    for (int i = 0; i < parts.length - 1; i++) {
                        if ("_git".equalsIgnoreCase(parts[i])) {
                            String repoName = parts[i + 1];
                            if (repoName != null && !repoName.isEmpty()) {
                                return repoName.split("\\?")[0].split("#")[0];
                            }
                        }
                    }
                }
                
                // Handle Bitbucket Server special formats
                if ("bitbucket".equals(host) && (lowerUrl.contains("/scm/") || lowerUrl.contains("/projects/"))) {
                    // Format: /scm/project/repo or /projects/PROJECT/repos/repo
                    String[] parts = path.split("/");
                    // Find the last non-empty part after /scm/ or /repos/
                    for (int i = parts.length - 1; i >= 0; i--) {
                        if (!parts[i].isEmpty() && !parts[i].equalsIgnoreCase("scm") 
                            && !parts[i].equalsIgnoreCase("projects") 
                            && !parts[i].equalsIgnoreCase("repos")) {
                            String repoName = parts[i];
                            return repoName.split("\\?")[0].split("#")[0];
                        }
                    }
                }
                
                // For standard Git URLs (GitHub, GitLab, Bitbucket Cloud), extract the last part
                // Format: owner/repo or group/subgroup/repo
                String[] parts = path.split("/");
                if (parts.length > 0) {
                    String repoName = parts[parts.length - 1];
                    // Remove any trailing slashes or query params
                    repoName = repoName.split("\\?")[0].split("#")[0];
                    if (!repoName.isEmpty()) {
                        return repoName;
                    }
                }
            }
        } catch (URISyntaxException e) {
            // If URI parsing fails, try regex extraction
            // Pattern: extract repo name from common URL formats
            Pattern pattern = Pattern.compile("([^/]+?)(?:\\.git)?(?:[/?#]|$)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                String repoName = matcher.group(1);
                // Try to get the last segment
                String[] parts = url.split("/");
                if (parts.length > 0) {
                    repoName = parts[parts.length - 1].replaceAll("\\.git$", "");
                    return repoName.split("\\?")[0].split("#")[0];
                }
            }
        }

        return null;
    }

    /**
     * Gets the branch name from the build/SCM using reflection.
     */
    private static String getBranch(Run<?, ?> build, SCM scm) {
        // Try to get from BuildData (for Git) using reflection
        try {
            Class<?> buildDataClass = Class.forName("hudson.plugins.git.util.BuildData");
            java.lang.reflect.Method getActionMethod = build.getClass().getMethod("getAction", Class.class);
            Object buildData = getActionMethod.invoke(build, buildDataClass);
            if (buildData != null) {
                try {
                    // Try getLastBuiltRevision() first
                    java.lang.reflect.Method getLastBuiltRevision = buildDataClass.getMethod("getLastBuiltRevision");
                    Object revision = getLastBuiltRevision.invoke(buildData);
                    if (revision != null) {
                        try {
                            java.lang.reflect.Method getBranch = revision.getClass().getMethod("getBranch");
                            Object branch = getBranch.invoke(revision);
                            if (branch != null) {
                                    try {
                                        java.lang.reflect.Method getName = branch.getClass().getMethod("getName");
                                        String branchName = (String) getName.invoke(branch);
                                        if (branchName != null && !branchName.isBlank()) {
                                        // Clean up branch name: remove refs/heads/, origin/, */ prefixes
                                        return branchName
                                            .replaceAll("^refs/heads/", "")
                                            .replaceAll("^origin/", "")
                                            .replaceAll("^\\*/", "")
                                            .replaceAll("^\\*", "");
                                    }
                                } catch (Exception e) {
                                    // Ignore
                                }
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }

                // Try getBuildsByBranchName() as alternative
                try {
                    java.lang.reflect.Method getBuildsByBranchName = buildDataClass.getMethod("getBuildsByBranchName");
                    Object buildsByBranch = getBuildsByBranchName.invoke(buildData);
                    if (buildsByBranch instanceof java.util.Map) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) buildsByBranch;
                        if (!map.isEmpty()) {
                            // Get the first branch name
                            String branchName = (String) map.keySet().iterator().next();
                            if (branchName != null && !branchName.isBlank()) {
                                // Clean up branch name: remove refs/heads/, origin/, */ prefixes
                                return branchName
                                    .replaceAll("^refs/heads/", "")
                                    .replaceAll("^origin/", "")
                                    .replaceAll("^\\*/", "")
                                    .replaceAll("^\\*", "");
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore - Git plugin might not be available
        }

        // Try to get from SCM branches configuration (for GitSCM)
        try {
            if (scm.getClass().getName().contains("GitSCM")) {
                try {
                    java.lang.reflect.Method getBranches = scm.getClass().getMethod("getBranches");
                    Object branches = getBranches.invoke(scm);
                    if (branches instanceof Collection) {
                        Collection<?> branchCollection = (Collection<?>) branches;
                        if (!branchCollection.isEmpty()) {
                            Object branchSpec = branchCollection.iterator().next();
                            // Try to get name from BranchSpec
                            try {
                                java.lang.reflect.Method getName = branchSpec.getClass().getMethod("getName");
                                String branchName = (String) getName.invoke(branchSpec);
                                if (branchName != null && !branchName.isBlank()) {
                                    // Clean up branch name: remove refs/heads/, origin/, */ prefixes
                                    return branchName
                                        .replaceAll("^refs/heads/", "")
                                        .replaceAll("^origin/", "")
                                        .replaceAll("^\\*/", "")
                                        .replaceAll("^\\*", "");
                                }
                            } catch (Exception e) {
                                // Try toString() as fallback
                                String branchStr = branchSpec.toString();
                                if (branchStr != null && !branchStr.isBlank()) {
                                    return branchStr
                                        .replaceAll("^refs/heads/", "")
                                        .replaceAll("^origin/", "")
                                        .replaceAll("^\\*/", "")
                                        .replaceAll("^\\*", "");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    /**
     * Gets the SVN revision from the build using SVN-specific methods.
     */
    private static String getSvnRevision(Run<?, ?> build, SCM scm, TaskListener listener) {
        // Method 1: Try to get from environment variables
        try {
            hudson.EnvVars env = build.getEnvironment(listener);
            String[] envVars = {"SVN_REVISION", "SVN_REV", "SVN_REVISION_NUMBER", "SVN_VERSION"};
            for (String envVar : envVars) {
                String value = env.get(envVar);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // Method 2: Try to get from SCM object directly
        try {
            // Try methods that might return revision
            String[] methodNames = {"getRevision", "getRevisionNumber", "getLastRevision", "getCurrentRevision"};
            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method method = scm.getClass().getMethod(methodName);
                    Object result = method.invoke(scm);
                    if (result != null) {
                        String revision = result.toString();
                        if (revision != null && !revision.isBlank()) {
                            return revision;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // Method doesn't exist, try next
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // Method 3: Try to get from build's display name or description
        try {
            String displayName = build.getDisplayName();
            // Display name might contain revision like "#123 r456"
            if (displayName != null) {
                // Try to extract revision number from display name
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("r(\\d+)");
                java.util.regex.Matcher matcher = pattern.matcher(displayName);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return null;
    }

    /**
     * Gets the commit SHA from the build.
     * Tries multiple methods to get the commit SHA, which is shown as "Revision" in Jenkins UI.
     */
    private static String getCommitSha(Run<?, ?> build) {
        // Method 1: Try to get from BuildData (for Git) - this is what shows as "Revision" in UI
        try {
            Class<?> buildDataClass = Class.forName("hudson.plugins.git.util.BuildData");
            java.lang.reflect.Method getActionMethod = build.getClass().getMethod("getAction", Class.class);
            Object buildData = getActionMethod.invoke(build, buildDataClass);
            if (buildData != null) {
                // Try getLastBuiltRevision().getSha1String()
                try {
                    java.lang.reflect.Method getLastBuiltRevision = buildDataClass.getMethod("getLastBuiltRevision");
                    Object revision = getLastBuiltRevision.invoke(buildData);
                    if (revision != null) {
                        // Try getSha1String() first
                        try {
                            java.lang.reflect.Method getSha1String = revision.getClass().getMethod("getSha1String");
                            String sha = (String) getSha1String.invoke(revision);
                            if (sha != null && !sha.isBlank()) {
                                return sha;
                            }
                        } catch (Exception e) {
                            // Try getSha1() and convert to string
                            try {
                                java.lang.reflect.Method getSha1 = revision.getClass().getMethod("getSha1");
                                Object sha1Obj = getSha1.invoke(revision);
                                if (sha1Obj != null) {
                                    String sha = sha1Obj.toString();
                                    if (sha != null && !sha.isBlank()) {
                                        return sha;
                                    }
                                }
                            } catch (Exception e2) {
                                // Try getName() as alternative
                                try {
                                    java.lang.reflect.Method getName = revision.getClass().getMethod("getName");
                                    String sha = (String) getName.invoke(revision);
                                    if (sha != null && !sha.isBlank()) {
                                        return sha;
                                    }
                                } catch (Exception e3) {
                                    // Ignore
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }

                // Try getBuildsByBranchName() and get the revision from there
                try {
                    java.lang.reflect.Method getBuildsByBranchName = buildDataClass.getMethod("getBuildsByBranchName");
                    Object buildsByBranch = getBuildsByBranchName.invoke(buildData);
                    if (buildsByBranch instanceof java.util.Map) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) buildsByBranch;
                        if (!map.isEmpty()) {
                            // Get the first revision from the map
                            Object revisionObj = map.values().iterator().next();
                            if (revisionObj != null) {
                                try {
                                    java.lang.reflect.Method getSha1String = revisionObj.getClass().getMethod("getSha1String");
                                    String sha = (String) getSha1String.invoke(revisionObj);
                                    if (sha != null && !sha.isBlank()) {
                                        return sha;
                                    }
                                } catch (Exception e) {
                                    // Try toString()
                                    String sha = revisionObj.toString();
                                    if (sha != null && !sha.isBlank() && sha.length() >= 7) {
                                        return sha;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore - Git plugin might not be available
        }

        // Method 2: Try to get from change sets (works for Git, SVN, etc.)
        try {
            java.lang.reflect.Method getChangeSetMethod = build.getClass().getMethod("getChangeSet");
            ChangeLogSet<?> changeSet = (ChangeLogSet<?>) getChangeSetMethod.invoke(build);
            
            if (changeSet != null && !changeSet.isEmptySet()) {
                for (ChangeLogSet.Entry entry : changeSet) {
                    if (entry != null) {
                        // For SVN, try getRevision() first
                        try {
                            java.lang.reflect.Method getRevision = entry.getClass().getMethod("getRevision");
                            Object revisionObj = getRevision.invoke(entry);
                            if (revisionObj != null) {
                                String revision = revisionObj.toString();
                                if (revision != null && !revision.isBlank()) {
                                    return revision;
                                }
                            }
                        } catch (Exception e) {
                            // Not SVN, try getCommitId() for Git
                            try {
                                String commitId = entry.getCommitId();
                                if (commitId != null && !commitId.isBlank()) {
                                    return commitId;
                                }
                            } catch (Exception e2) {
                                // Ignore
                            }
                        }
                        
                        // Only process first entry for now
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // Method 3: Try to get from all actions (look for BuildData in all actions, or SVNRevisionState for SVN)
        try {
            java.util.Collection<?> actions = build.getAllActions();
            for (Object action : actions) {
                if (action != null) {
                    String actionClass = action.getClass().getName();
                    
                    // Try SVNRevisionState for SVN revision
                    if (actionClass.equals("hudson.scm.SVNRevisionState")) {
                        try {
                            // Try getRevision() method - check if it takes parameters
                            try {
                                java.lang.reflect.Method getRevision = null;
                                try {
                                    // Try no-arg version first
                                    getRevision = action.getClass().getMethod("getRevision");
                                } catch (NoSuchMethodException e) {
                                    // Try with parameters - maybe it needs a module?
                                    java.lang.reflect.Method[] methods = action.getClass().getMethods();
                                    for (java.lang.reflect.Method m : methods) {
                                        if (m.getName().equals("getRevision")) {
                                            getRevision = m;
                                            break;
                                        }
                                    }
                                }
                                
                                if (getRevision != null) {
                                    Object revisionObj = null;
                                    if (getRevision.getParameterCount() == 0) {
                                        revisionObj = getRevision.invoke(action);
                                    } else {
                                        // Try with null or empty parameters
                                        Object[] params = new Object[getRevision.getParameterCount()];
                                        revisionObj = getRevision.invoke(action, params);
                                    }
                                    if (revisionObj != null) {
                                        String revision = revisionObj.toString();
                                        if (revision != null && !revision.isBlank()) {
                                            return revision;
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                            
                            // Try getRevisionNumber() method
                            try {
                                java.lang.reflect.Method getRevisionNumber = action.getClass().getMethod("getRevisionNumber");
                                Object revisionObj = getRevisionNumber.invoke(action);
                                if (revisionObj != null) {
                                    String revision = revisionObj.toString();
                                    if (revision != null && !revision.isBlank()) {
                                        return revision;
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    
                    // Try BuildData for Git
                    if (actionClass.contains("BuildData")) {
                        try {
                            java.lang.reflect.Method getLastBuiltRevision = action.getClass().getMethod("getLastBuiltRevision");
                            Object revision = getLastBuiltRevision.invoke(action);
                            if (revision != null) {
                                try {
                                    java.lang.reflect.Method getSha1String = revision.getClass().getMethod("getSha1String");
                                    String sha = (String) getSha1String.invoke(revision);
                                    if (sha != null && !sha.isBlank()) {
                                        return sha;
                                    }
                                } catch (Exception e) {
                                    // Ignore
                                }
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }


    /**
     * Extracts repository name from Perforce depot path.
     * Examples:
     * - //depot/main/project -> project
     * - //depot/streams/main -> main
     * - //depot/project -> project
     */
    private static String extractPerforceRepoName(String depotPath) {
        if (depotPath == null || depotPath.isBlank()) {
            return null;
        }
        
        // Remove leading // if present
        String path = depotPath.startsWith("//") ? depotPath.substring(2) : depotPath;
        
        // Split by / and get the last meaningful part
        String[] parts = path.split("/");
        if (parts.length > 0) {
            // Skip common depot prefixes
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i];
                if (!part.isEmpty() && !part.equalsIgnoreCase("depot") 
                    && !part.equalsIgnoreCase("streams") 
                    && !part.equalsIgnoreCase("main")) {
                    return part;
                }
            }
            // If all parts were skipped, return the last part anyway
            if (parts.length > 0) {
                return parts[parts.length - 1];
            }
        }
        
        return null;
    }

    /**
     * Generates a commit URL from repository URL, commit SHA, and host type.
     */
    private static String generateCommitUrl(String repoUrl, String commitSha, String hostType) {
        if (repoUrl == null || commitSha == null) {
            return null;
        }

        try {
            // Remove .git suffix and any trailing slashes
            String baseUrl = repoUrl.replaceAll("\\.git$", "").replaceAll("/+$", "");
            
            // Generate commit URL based on host type
            String host = detectHostFromUrl(repoUrl);
            if (host == null) {
                // If we can't detect host from URL, but we have a host type, use that
                // This helps with SVN where the URL might not contain obvious host indicators
                if (hostType != null && !hostType.isBlank()) {
                    if ("svn".equals(hostType)) {
                        // For SVN, check if commit SHA is numeric (revision number)
                        if (commitSha.matches("^\\d+$")) {
                            // Generic SVN revision URL format (varies by host, but common pattern)
                            // Most SVN web interfaces use /revision/{revision} or /r/{revision}
                            // Since we don't know the exact format, return null for now
                            // SourceForge SVN is handled separately below
                            return null;
                        }
                    }
                }
                return null;
            }

            if ("github".equals(host)) {
                // GitHub: https://github.com/user/repo/commit/abc123
                return baseUrl + "/commit/" + commitSha;
            } else if ("gitlab".equals(host) || "gitlab-self".equals(host)) {
                // GitLab (both hosted and self-hosted): https://gitlab.com/user/repo/-/commit/abc123
                return baseUrl + "/-/commit/" + commitSha;
            } else if ("bitbucket".equals(host)) {
                // Bitbucket Cloud: https://bitbucket.org/USERNAME/REPO-NAME/commits/COMMIT-SHA
                // Bitbucket Server: https://bitbucket.example.com/projects/PROJECT/repos/repo/commits/COMMIT-SHA
                // Both use /commits/ path (note: plural "commits")
                return baseUrl + "/commits/" + commitSha;
            } else if ("gitea".equals(host) || "forgejo".equals(host) || "gogs".equals(host) || "codeberg".equals(host)) {
                // Gitea/Forgejo/Gogs/Codeberg: https://host.com/user/repo/commit/abc123
                return baseUrl + "/commit/" + commitSha;
            } else if ("sourcehut".equals(host)) {
                // Sourcehut: https://git.sr.ht/user/repo/commit/abc123
                return baseUrl + "/commit/" + commitSha;
            } else if ("azure-repos".equals(host)) {
                // Azure DevOps: https://dev.azure.com/TEAM/PROJECTNAME/_git/REPO-NAME/commit/COMMIT-SHA
                // Note: The commit URL includes the repo name again in the path
                // Format: baseUrl/commit/COMMIT-SHA (baseUrl already includes /_git/REPO-NAME)
                return baseUrl + "/commit/" + commitSha;
            } else if ("gitee".equals(host)) {
                // Gitee: https://gitee.com/user/repo/commit/abc123
                return baseUrl + "/commit/" + commitSha;
            } else if ("sourceforge".equals(host)) {
                // SourceForge Git: https://sourceforge.net/p/project/code/ci/{commitSha}/
                // SourceForge SVN: https://sourceforge.net/p/project/code/{revision}/
                // Check if it's a numeric revision (SVN) or hex SHA (Git)
                if (commitSha.matches("^\\d+$")) {
                    // SVN revision (numeric)
                    return baseUrl + "/" + commitSha + "/";
                } else {
                    // Git commit SHA (hex)
                    return baseUrl + "/ci/" + commitSha + "/";
                }
            } else if ("svn".equals(hostType) || (host != null && host.contains("svn"))) {
                // Generic SVN revision URL (for non-SourceForge SVN repositories)
                // SVN revision numbers are numeric
                if (commitSha.matches("^\\d+$")) {
                    // Common SVN web interface patterns (varies by host)
                    // Try /revision/{revision} first, but many hosts use different formats
                    // Since we can't reliably determine the format, return null
                    // Users can manually specify vcCommitUrl if needed
                    return null;
                }
            } else if ("perforce".equals(host)) {
                // Perforce: changelist URLs vary by installation (Swarm, P4Web, etc.)
                // We can't reliably generate a web URL without knowing the specific setup
                // Return null to indicate we can't generate a URL
                return null;
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }
}


