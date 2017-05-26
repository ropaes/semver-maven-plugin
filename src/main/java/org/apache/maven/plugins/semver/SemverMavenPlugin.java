package org.apache.maven.plugins.semver;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.semver.configuration.SemverConfiguration;
import org.apache.maven.plugins.semver.factories.FileWriterFactory;
import org.apache.maven.plugins.semver.providers.BranchProvider;
import org.apache.maven.plugins.semver.providers.PomProvider;
import org.apache.maven.plugins.semver.providers.RepositoryProvider;
import org.apache.maven.plugins.semver.providers.VersionProvider;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.interactivity.Prompter;

import java.util.Map;

/**
 * <p>Abstract class to use as template for each goal in the plugin.</p>
 * <p>
 * <p>Possible usages are:</p>
 * <ul>Possible runModes are:
 * <li>When {@link RUNMODE} = RELEASE then determine version from POM-version</li>
 * <li>When {@link RUNMODE} = RELEASE_BRANCH then determine version from GIT-branch</li>
 * <li>When {@link RUNMODE} = RELEASE_BRANCH_HOSEE then determine version from POM-version (without maven-release-plugin)</li>
 * <li>When {@link RUNMODE} = NATIVE then determine version from POM-version (without maven-release-plugin)</li>
 * <li>When {@link RUNMODE} = NATIVE_BRANCH then determine version from POM-version (without maven-release-plugin)</li>
 * <li>When {@link RUNMODE} = RUNMODE_NOT_SPECIFIED does nothing</li>
 * </ul>
 *  <ul>Add a tag to the GIT-version
 * <li>tag = 1</li>
 * </ul>
 * <ul>Add the branchVersion to the GIT-tag
 * <li>branchVersion = featureX</li>
 * </ul>
 * <ul>Possible value for the branchConversionUrl is
 * <li>branchConversionUrl = http://localhost/determineBranchVersion</li>
 * </ul>
 * <ul>Add metaData to the GIT-version
 * <li>metaData = beta</li>
 * </ul>
 *
 * @author sido
 */
public abstract class SemverMavenPlugin extends AbstractMojo {

  public static final String MOJO_LINE_BREAK = "------------------------------------------------------------------------";
  public static final String FUNCTION_LINE_BREAK = "************************************************************************";

  protected final Log LOG = getLog();

  @Component
  private Prompter prompter;

  @Component
  private BuildPluginManager buildPluginManager;

  @Parameter(property = "project", defaultValue = "${project}", readonly = true, required = true)
  protected MavenProject project;
  @Parameter(property = "username", defaultValue = "")
  private String scmUsername = "";
  @Parameter(property = "password", defaultValue = "")
  private String scmPassword = "";
  @Parameter(property = "tag")
  protected String preparedReleaseTag;
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;
  @Parameter(property = "runMode", required = true, defaultValue = "NATIVE")
  private RUNMODE runMode;
  @Parameter(property = "branchVersion")
  private String branchVersion;
  @Parameter(property = "metaData")
  private String metaData;
  @Parameter(property = "branchConversionUrl")
  private String branchConversionUrl;

  private SemverConfiguration configuration;
  private RepositoryProvider repositoryProvider;
  private VersionProvider versionProvider;
  private BranchProvider branchProvider;
  private PomProvider pomProvider;

  /**
   * <p>Override runMode through configuration properties</p>
   *
   * @param runMode get runMode from plugin configuration
   */
  public void setRunMode(RUNMODE runMode) {
    this.runMode = runMode;
  }

  /**
   * <p>Override branchVersion through configuration properties</p>
   *
   * @param branchVersion get branchVersion from plugin configuration
   */
  public void setBranchVersion(String branchVersion) {
    this.branchVersion = branchVersion;
  }


  /**
   * <p>Override branchConversionUrl through configuration properties</p>
   *
   * @param branchConversionUrl get branchConversionUrl from plugin configuration
   */
  public void setBranchConversionUrl(String branchConversionUrl) {
    this.branchConversionUrl = branchConversionUrl;
  }

  /**
   * <p>Create a postfix for the versionTag</p>
   *
   * @param metaData for example "-solr"
   */
  public void setMetaData(String metaData) {
    this.metaData = metaData;
  }

  /**
   * <p>Executes the configured runMode for each goal.</p>
   *
   * @param rawVersions rawVersions are the versions determined by the goal
   */
  protected void executeRunMode(Map<RAW_VERSION, String> rawVersions) {
    if(!versionProvider.isRemoteVersionCorrupt(project.getVersion())) {
      if (getConfiguration().getRunMode() == RUNMODE.RELEASE) {
        Map<VersionProvider.FINAL_VERSION, String> finalVersions = versionProvider.determineReleaseVersions(rawVersions);
        FileWriterFactory.createReleaseProperties(LOG, project, finalVersions);
      } else if (getConfiguration().getRunMode() == RUNMODE.RELEASE_BRANCH || getConfiguration().getRunMode() == RUNMODE.RELEASE_BRANCH_HOSEE) {
        Map<VersionProvider.FINAL_VERSION, String> finalVersions = versionProvider.determineReleaseBranchVersions(rawVersions);
        FileWriterFactory.createReleaseProperties(LOG, project, finalVersions);

      } else if (getConfiguration().getRunMode() == RUNMODE.NATIVE) {
        FileWriterFactory.backupSemverPom(LOG);
        Map<VersionProvider.FINAL_VERSION, String> finalVersions = versionProvider.determineReleaseVersions(rawVersions);
        pomProvider.createReleasePom(finalVersions);
        pomProvider.createNextDevelopmentPom(finalVersions.get(VersionProvider.FINAL_VERSION.DEVELOPMENT));
        FileWriterFactory.removeBackupSemverPom(LOG);
      } else if (getConfiguration().getRunMode() == RUNMODE.NATIVE_BRANCH) {
        FileWriterFactory.backupSemverPom(LOG);
        Map<VersionProvider.FINAL_VERSION, String> finalVersions = versionProvider.determineReleaseBranchVersions(rawVersions);
        pomProvider.createReleasePom(finalVersions);
        pomProvider.createNextDevelopmentPom(finalVersions.get(VersionProvider.FINAL_VERSION.DEVELOPMENT));
        FileWriterFactory.removeBackupSemverPom(LOG);
      }
    } else {
      LOG.error("");
      LOG.error("Remote version is higher then local version in your repository");
      LOG.error("Please check your repository state");
      Runtime.getRuntime().exit(1);
    }
  }



  /**
   * <p>Get merged configuration</p>
   *
   * @return SemverConfiguration
   */
  public SemverConfiguration getConfiguration() {
    if (configuration == null) {
      configuration = new SemverConfiguration(session);
      configuration.setScmUsername(scmUsername);
      configuration.setScmPassword(scmPassword);
      configuration.setRunMode(runMode);
      configuration.setBranchConversionUrl(branchConversionUrl);
      if (branchProvider != null) {
        configuration.setBranchVersion(branchProvider.determineBranchVersionFromGitBranch(branchVersion));
      } else {
        configuration.setBranchVersion(branchVersion);
      }
      configuration.setMetaData(metaData);
    }
    return configuration;
  }

  /**
   * <p>To use the {@link RepositoryProvider} this method is needed to get access.</p>
   *
   * @return {@link RepositoryProvider}
   */
  protected RepositoryProvider getRepositoryProvider() {
    return repositoryProvider;
  }

  /**
   * <p>To use the {@link VersionProvider} this method is needed to get access.</p>
   *
   * @return {@link VersionProvider}
   */
  protected VersionProvider getVersionProvider() {
    return versionProvider;
  }

  /**
   *
   * <p>In each goal this method is called to intialize all providers.</p>
   *
   */
  protected void initializeProviders() {
    repositoryProvider = new RepositoryProvider(LOG, project, getConfiguration(), prompter);
    branchProvider = new BranchProvider(LOG, repositoryProvider, branchConversionUrl);
    versionProvider = new VersionProvider(LOG, repositoryProvider, getConfiguration());
    pomProvider = new PomProvider(LOG, repositoryProvider, project, session, buildPluginManager);
  }

  /**
   * <p>Version-type is mentoined here.</p>
   *
   * @author sido
   */
  public enum RAW_VERSION {
    DEVELOPMENT,
    RELEASE,
    SCM,
    MAJOR,
    MINOR,
    PATCH
  }

  /**
   * <ul>
   * <li>release: maak gebruik van normale semantic-versioning en release-plugin</li>
   * <li>release-rpm</li>
   * <li>native</li>
   * <li>native-rpm</li>
   * </ul>
   */
  public enum RUNMODE {
    RELEASE,
    RELEASE_BRANCH,
    RELEASE_BRANCH_HOSEE,
    NATIVE,
    NATIVE_BRANCH,
    RUNMODE_NOT_SPECIFIED;

    public static RUNMODE convertToEnum(String runMode) {
      RUNMODE value = RUNMODE_NOT_SPECIFIED;
      if (runMode != null) {
        if ("RELEASE".equals(runMode)) {
          value = RELEASE;
        } else if ("RELEASE_BRANCH".equals(runMode)) {
          value = RELEASE_BRANCH;
        } else if ("RELEASE_BRANCH_HOSEE".equals(runMode)) {
          value = RELEASE_BRANCH_HOSEE;
        } else if ("NATIVE".equals(runMode)) {
          value = NATIVE;
        } else if ("NATIVE_BRANCH".equals(runMode)) {
          value = NATIVE_BRANCH;
        }
      }
      return value;
    }
  }


}
