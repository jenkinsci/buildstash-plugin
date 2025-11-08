package com.buildstash;

import hudson.model.Run;
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
                // Try to get info from environment variables (for pipelines)
                populateFromEnvironment(build, request);
                return;
            }

            // Detect SCM type
            String detectedHostType = detectHostType(scm);
            if (detectedHostType != null && isEmpty(request.getVcHostType())) {
                request.setVcHostType(detectedHostType);
            }

            // Get repository URL
            String repoUrl = getRepositoryUrl(build, scm);
            if (repoUrl != null && !repoUrl.trim().isEmpty()) {
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

            // Also try environment variables first (even if SCM is available)
            // This is important for Pipeline builds where env vars are more reliable
            populateFromEnvironment(build, request);

            // Get commit SHA (try multiple methods) - only if not already set from environment
            if (isEmpty(request.getVcCommitSha())) {
                String detectedCommitSha = getCommitSha(build);
                if (detectedCommitSha != null && !detectedCommitSha.trim().isEmpty()) {
                    request.setVcCommitSha(detectedCommitSha);
                }
            }

            // Generate commit URL if we have repo URL and commit SHA
            if (isEmpty(request.getVcCommitUrl())) {
                String finalCommitSha = request.getVcCommitSha();
                String finalRepoUrl = request.getVcRepoUrl();
                if (finalCommitSha != null && finalRepoUrl != null) {
                    String commitUrl = generateCommitUrl(finalRepoUrl, finalCommitSha);
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
        return str == null || str.trim().isEmpty();
    }

    /**
     * Populates VC info from environment variables (useful for pipelines).
     */
    private static void populateFromEnvironment(Run<?, ?> build, BuildstashUploadRequest request) {
        try {
            // Try to get environment variables using reflection
            Object env = null;
            try {
                // For WorkflowRun, try to get environment
                if (build.getClass().getName().contains("WorkflowRun")) {
                    java.lang.reflect.Method getEnvMethod = build.getClass().getMethod("getEnvironment", hudson.model.TaskListener.class);
                    env = getEnvMethod.invoke(build, (hudson.model.TaskListener) null);
                }
            } catch (Exception e) {
                // Ignore
            }

            if (env != null) {
                try {
                    java.lang.reflect.Method getMethod = env.getClass().getMethod("get", String.class);
                    
                    // Try GIT_URL
                    String gitUrl = (String) getMethod.invoke(env, "GIT_URL");
                    if (gitUrl != null && !gitUrl.trim().isEmpty() && isEmpty(request.getVcRepoUrl())) {
                        request.setVcRepoUrl(gitUrl);
                        if (isEmpty(request.getVcHost())) {
                            String host = detectHostFromUrl(gitUrl);
                            if (host != null) {
                                request.setVcHost(host);
                            }
                        }
                        if (isEmpty(request.getVcRepoName())) {
                            String repoName = extractRepoNameFromUrl(gitUrl);
                            if (repoName != null) {
                                request.setVcRepoName(repoName);
                            }
                        }
                    }

                    // Try GIT_BRANCH
                    String gitBranch = (String) getMethod.invoke(env, "GIT_BRANCH");
                    if (gitBranch != null && !gitBranch.trim().isEmpty() && isEmpty(request.getVcBranch())) {
                        // Clean up branch name: remove origin/, refs/heads/, */ prefixes
                        String cleanedBranch = gitBranch
                            .replaceAll("^origin/", "")
                            .replaceAll("^refs/heads/", "")
                            .replaceAll("^\\*/", "")
                            .replaceAll("^\\*", "");
                        request.setVcBranch(cleanedBranch);
                    }

                    // Try GIT_COMMIT
                    String gitCommit = (String) getMethod.invoke(env, "GIT_COMMIT");
                    if (gitCommit != null && !gitCommit.trim().isEmpty() && isEmpty(request.getVcCommitSha())) {
                        request.setVcCommitSha(gitCommit);
                    }

                    // Also try GIT_COMMIT_SHORT if GIT_COMMIT wasn't available
                    if (isEmpty(request.getVcCommitSha())) {
                        String gitCommitShort = (String) getMethod.invoke(env, "GIT_COMMIT_SHORT");
                        if (gitCommitShort != null && !gitCommitShort.trim().isEmpty()) {
                            // Try to get full commit from BuildData if available
                            String fullCommit = getFullCommitFromShort(build, gitCommitShort);
                            request.setVcCommitSha(fullCommit != null ? fullCommit : gitCommitShort);
                        }
                    }

                    // Generate commit URL if we have both
                    String finalCommitSha = request.getVcCommitSha();
                    if (isEmpty(request.getVcCommitUrl()) && gitUrl != null && finalCommitSha != null) {
                        String commitUrl = generateCommitUrl(gitUrl, finalCommitSha);
                        if (commitUrl != null) {
                            request.setVcCommitUrl(commitUrl);
                        }
                    }

                    // Set host type if not set
                    if (isEmpty(request.getVcHostType()) && (gitUrl != null || gitCommit != null)) {
                        request.setVcHostType("git");
                    }

                    // Try Perforce environment variables (P4 Plugin)
                    String p4Changelist = (String) getMethod.invoke(env, "P4_CHANGELIST");
                    String p4DepotPath = (String) getMethod.invoke(env, "P4_DEPOT_PATH");
                    String p4Port = (String) getMethod.invoke(env, "P4_PORT");
                    
                    if (p4Changelist != null || p4DepotPath != null || p4Port != null) {
                        // Set host type to perforce
                        if (isEmpty(request.getVcHostType())) {
                            request.setVcHostType("perforce");
                        }
                        
                        // Set host to perforce
                        if (isEmpty(request.getVcHost())) {
                            request.setVcHost("perforce");
                        }
                        
                        // Use changelist as commit SHA (Perforce uses changelists instead of commits)
                        if (p4Changelist != null && !p4Changelist.trim().isEmpty() && isEmpty(request.getVcCommitSha())) {
                            request.setVcCommitSha(p4Changelist);
                        }
                        
                        // Use depot path as repo URL
                        if (p4DepotPath != null && !p4DepotPath.trim().isEmpty() && isEmpty(request.getVcRepoUrl())) {
                            // Construct Perforce URL from port and depot path
                            String p4Url = p4DepotPath;
                            if (p4Port != null && !p4Port.trim().isEmpty()) {
                                // Format: perforce://server:port/depot/path
                                p4Url = p4Port + "/" + p4DepotPath;
                            }
                            request.setVcRepoUrl(p4Url);
                            
                            // Extract repo name from depot path
                            if (isEmpty(request.getVcRepoName())) {
                                String repoName = extractPerforceRepoName(p4DepotPath);
                                if (repoName != null) {
                                    request.setVcRepoName(repoName);
                                }
                            }
                        }
                        
                        // Use depot path or stream as branch (Perforce uses streams or depot paths)
                        if (isEmpty(request.getVcBranch())) {
                            // Try P4_STREAM if available
                            String p4Stream = (String) getMethod.invoke(env, "P4_STREAM");
                            if (p4Stream != null && !p4Stream.trim().isEmpty()) {
                                request.setVcBranch(p4Stream);
                            } else if (p4DepotPath != null && !p4DepotPath.trim().isEmpty()) {
                                // Use depot path as branch-like identifier
                                request.setVcBranch(p4DepotPath);
                            }
                        }
                        
                        // Generate changelist URL if we have the necessary info
                        if (isEmpty(request.getVcCommitUrl()) && p4Changelist != null && p4Port != null) {
                            String changelistUrl = generatePerforceChangelistUrl(p4Port, p4Changelist);
                            if (changelistUrl != null) {
                                request.setVcCommitUrl(changelistUrl);
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
    }

    /**
     * Detects the SCM host type (git, svn, hg, etc.) from the SCM object.
     */
    private static String detectHostType(SCM scm) {
        String scmClass = scm.getClass().getName().toLowerCase();
        
        if (scmClass.contains("git")) {
            return "git";
        } else if (scmClass.contains("svn") || scmClass.contains("subversion")) {
            return "svn";
        } else if (scmClass.contains("mercurial") || scmClass.contains("hg")) {
            return "hg";
        } else if (scmClass.contains("bazaar") || scmClass.contains("bzr")) {
            return "bzr";
        } else if (scmClass.contains("perforce")) {
            return "perforce";
        } else if (scmClass.contains("cvs")) {
            return "cvs";
        }
        
        return null;
    }

    /**
     * Gets the repository URL from the build/SCM using reflection.
     */
    private static String getRepositoryUrl(Run<?, ?> build, SCM scm) {
        // Try to get from GitSCM using reflection
        try {
            if (scm.getClass().getName().contains("GitSCM")) {
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
                                if (url != null && !url.trim().isEmpty()) {
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
            // Ignore - Git plugin might not be available
        }

        return null;
    }

    /**
     * Detects the VC host (github, gitlab, etc.) from a repository URL.
     */
    private static String detectHostFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
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
        if (lowerUrl.contains("bitbucket")) {
            // Could be Bitbucket Server (self-hosted) - still use "bitbucket" as host type
            return "bitbucket";
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
        if (lowerUrl.contains("sourceforge")) {
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
        // Azure Repos
        if (lowerUrl.contains("azure.com") || lowerUrl.contains("visualstudio.com") || lowerUrl.contains("dev.azure.com")) {
            return "azure-repos";
        }
        // Perforce
        if (lowerUrl.contains("perforce")) {
            return "perforce";
        }
        // Gitee
        if (lowerUrl.contains("gitee")) {
            return "gitee";
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
        if (url == null || url.trim().isEmpty()) {
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
                                    if (branchName != null && !branchName.trim().isEmpty()) {
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
                            if (branchName != null && !branchName.trim().isEmpty()) {
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
                                if (branchName != null && !branchName.trim().isEmpty()) {
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
                                if (branchStr != null && !branchStr.trim().isEmpty()) {
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
                            if (sha != null && !sha.trim().isEmpty()) {
                                return sha;
                            }
                        } catch (Exception e) {
                            // Try getSha1() and convert to string
                            try {
                                java.lang.reflect.Method getSha1 = revision.getClass().getMethod("getSha1");
                                Object sha1Obj = getSha1.invoke(revision);
                                if (sha1Obj != null) {
                                    String sha = sha1Obj.toString();
                                    if (sha != null && !sha.trim().isEmpty()) {
                                        return sha;
                                    }
                                }
                            } catch (Exception e2) {
                                // Try getName() as alternative
                                try {
                                    java.lang.reflect.Method getName = revision.getClass().getMethod("getName");
                                    String sha = (String) getName.invoke(revision);
                                    if (sha != null && !sha.trim().isEmpty()) {
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
                                    if (sha != null && !sha.trim().isEmpty()) {
                                        return sha;
                                    }
                                } catch (Exception e) {
                                    // Try toString()
                                    String sha = revisionObj.toString();
                                    if (sha != null && !sha.trim().isEmpty() && sha.length() >= 7) {
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

        // Method 2: Try to get from change sets
        try {
            java.lang.reflect.Method getChangeSetMethod = build.getClass().getMethod("getChangeSet");
            ChangeLogSet<?> changeSet = (ChangeLogSet<?>) getChangeSetMethod.invoke(build);
            if (changeSet != null && !changeSet.isEmptySet()) {
                ChangeLogSet.Entry entry = changeSet.iterator().next();
                if (entry != null) {
                    String commitId = entry.getCommitId();
                    if (commitId != null && !commitId.trim().isEmpty()) {
                        return commitId;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // Method 3: Try to get from all actions (look for BuildData in all actions)
        try {
            java.util.Collection<?> actions = build.getActions();
            for (Object action : actions) {
                if (action != null && action.getClass().getName().contains("BuildData")) {
                    try {
                        java.lang.reflect.Method getLastBuiltRevision = action.getClass().getMethod("getLastBuiltRevision");
                        Object revision = getLastBuiltRevision.invoke(action);
                        if (revision != null) {
                            try {
                                java.lang.reflect.Method getSha1String = revision.getClass().getMethod("getSha1String");
                                String sha = (String) getSha1String.invoke(revision);
                                if (sha != null && !sha.trim().isEmpty()) {
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
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    /**
     * Tries to get full commit SHA from short commit using BuildData.
     */
    private static String getFullCommitFromShort(Run<?, ?> build, String shortCommit) {
        try {
            Class<?> buildDataClass = Class.forName("hudson.plugins.git.util.BuildData");
            java.lang.reflect.Method getActionMethod = build.getClass().getMethod("getAction", Class.class);
            Object buildData = getActionMethod.invoke(build, buildDataClass);
            if (buildData != null) {
                try {
                    java.lang.reflect.Method getLastBuiltRevision = buildDataClass.getMethod("getLastBuiltRevision");
                    Object revision = getLastBuiltRevision.invoke(buildData);
                    if (revision != null) {
                        try {
                            java.lang.reflect.Method getSha1String = revision.getClass().getMethod("getSha1String");
                            String sha = (String) getSha1String.invoke(revision);
                            if (sha != null && sha.startsWith(shortCommit)) {
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
        if (depotPath == null || depotPath.trim().isEmpty()) {
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
     * Generates a Perforce changelist URL.
     * Note: Perforce doesn't have a standard web URL format, but if Swarm is available,
     * the URL format might be: http://swarm-server/changes/CHANGELIST
     */
    private static String generatePerforceChangelistUrl(String p4Port, String changelist) {
        if (p4Port == null || changelist == null) {
            return null;
        }
        
        // Try to detect if Swarm is available from the port
        // Swarm URLs are typically: http://swarm-server/changes/CHANGELIST
        // But we can't reliably detect this without more info
        
        // For now, return a Perforce URL format: perforce://server/changes/CHANGELIST
        // This is a best-effort URL that may not be clickable but provides info
        String server = p4Port.replaceAll("^perforce://", "").replaceAll(":.*$", "");
        if (!server.isEmpty()) {
            return "perforce://" + server + "/changes/" + changelist;
        }
        
        return null;
    }

    /**
     * Generates a commit URL from repository URL and commit SHA.
     */
    private static String generateCommitUrl(String repoUrl, String commitSha) {
        if (repoUrl == null || commitSha == null) {
            return null;
        }

        try {
            // Remove .git suffix and any trailing slashes
            String baseUrl = repoUrl.replaceAll("\\.git$", "").replaceAll("/+$", "");
            
            // Generate commit URL based on host type
            String host = detectHostFromUrl(repoUrl);
            if (host == null) {
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
            } else if ("perforce".equals(host)) {
                // Perforce: changelist URLs are handled separately in generatePerforceChangelistUrl
                // For standard Perforce, we can't generate a web URL without Swarm
                // Return null to indicate we can't generate a URL
                return null;
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }
}
