package org.apache.maven.plugins.semver.providers;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.semver.SemverMavenPlugin;

import java.io.IOException;

/**
 *
 * <h>BranchProvider</h>
 * <p>When a version has a branch in it's GIT-tag, the branch-provider can be used to determine the branch for GIT.</p>
 *
 * @author sido
 */
public class BranchProvider {

    private Log LOG;

    private RepositoryProvider repositoryProvider;

    private String branchConversionUrl;

    /**
     *
     * <h>BranchProvider constructor</h>
     * <p>Initializes the BranchProvider.</p>
     *
     *
     * @param LOG logging from parent Mojo
     * @param repositoryProvider {@link RepositoryProvider} from parent Mojo
     * @param branchConversionUrl branch conversion url
     */
    public BranchProvider(Log LOG, RepositoryProvider repositoryProvider, String branchConversionUrl) {
        this.LOG = LOG;
        this.repositoryProvider = repositoryProvider;
        this.branchConversionUrl = branchConversionUrl;
    }

    /**
     * <p>Determine branchVersion from GIT-branch</p>
     *
     * @param branchVersion branch version for the GIT-tag
     * @return branchVersion
     */
    public String determineBranchVersionFromGitBranch(String branchVersion) {
        String value = null;
        if (branchVersion == null || branchVersion.isEmpty()) {
            LOG.info(SemverMavenPlugin.MOJO_LINE_BREAK);
            LOG.info("Determine current branchVersion from GIT-repository");
            try {
                String branch = repositoryProvider.getCurrentBranch();
                LOG.info("Current branch                    : " + branch);
                if (branch != null && !branch.isEmpty()) {
                    if (branch.matches("\\d+.\\d+.\\d+.*")) {
                        LOG.info("Current branch matches            : \\d+.\\d+.\\d+.*");
                        value = branch;
                    } else if (branch.matches("v\\d+_\\d+_\\d+.*")) {
                        LOG.info("Current branch matches            : v\\d+_\\d+_\\d+.*");
                        String rawBranch = branch.replaceAll("v", "").replaceAll("_", ".");
                        value = rawBranch.substring(0, StringUtils.ordinalIndexOf(rawBranch, ".", 3));
                    } else if (branch.equals("master")) {
                        LOG.info("Current branch matches            : master");
                        value = determineVersionFromMasterBranch(branch);
                    } else if (branch.matches("^[a-z0-9]*")) {
                        LOG.warn("Current branch matches md5-hash       : ^[a-z0-9]");
                        LOG.warn("Application is running tests");
                    } else {
                        LOG.error("Current branch does not match        : digit.digit.digit");
                        LOG.error("And current branch does not match    : v+digit.digit.digit+*");
                        LOG.error("And current branch does is not       : master");
                        LOG.error("Branch is not set, semantic versioning for RPM is terminated");
                        Runtime.getRuntime().exit(1);
                    }
                } else {
                    LOG.error("Current branch is empty or null");
                    LOG.error("Branch is not set, semantic versioning for RPM is terminated");
                    Runtime.getRuntime().exit(1);
                }
            } catch (Exception err) {
                LOG.error("An error occured while trying to reach GIT-repo: ", err);
            }
            LOG.info(SemverMavenPlugin.MOJO_LINE_BREAK);
        } else {
            value = branchVersion;
        }
        return value;
    }

    /**
     *
     * <h>Master branch version detemination</h>
     * <p>Which new version is to be determined from the master-branch. This is done by an external service defined in the configuration of the plugin</p>
     * <p>Example:</p>
     * <pre>
     *     <code>
     *          <configuration>
     *              <branchConversionUrl>http://branchvconversion.com/</branchConversionUrl>
     *          </configuration>
     *     </code>
     * </pre>
     *
     * @param branch branch from which a version has te be determined
     * @return masterBranchVersion
     */
    private String determineVersionFromMasterBranch(String branch) {
        String branchVersion = "";
        LOG.info("Setup connection to            : " + branchConversionUrl + branch);
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        CloseableHttpResponse response = null;
        try {
            HttpGet httpGet = new HttpGet(branchConversionUrl + branch);
            httpGet.addHeader("Content-Type", "application/json");
            response = httpClient.execute(httpGet);
            LOG.info("Versionizer returned response-code: " + response.getStatusLine());
            HttpEntity entity = response.getEntity();
            branchVersion = EntityUtils.toString(entity);
            if (branchVersion != null) {
                LOG.info("Versionizer returned branchversion: " + branchVersion);
            } else {
                LOG.error("No branch version could be determined");
            }
        } catch (IOException err) {
            LOG.error("Could not make request to versionizer", err);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (IOException err) {
                LOG.error("Could not close request to versionizer", err);
            }
        }
        return branchVersion;
    }

}
