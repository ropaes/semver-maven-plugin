node {
  def mvnHome
  def gitRemoteUser = 'jenkins';
  def gitRemoteUrl = 'github.com/sidohaakma/semver-maven-plugin';
  def artifactId = "semver-maven-plugin";
  stage('Preparation') {
    // Clean workspace
    step([$class: 'WsCleanup', cleanWhenFailure: false])
    // Get code from github.com
    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'jenkins-git', url: 'http://' + gitRemoteUser + '@' + gitRemoteUrl + '.git']]]
    // Get the Maven tool.
    mvnHome = tool 'MAVEN'
  }
  stage('Test') {
    echo "Test artifact : ${artifactId}"
    // sh "'${mvnHome}/bin/mvn' test"
    echo "Generate junit test report"
    // junit '**/target/surefire-reports/TEST-*.xml'
  }
  stage('Build') {
    echo "Build artifact : ${artifactId}"
    sh "'${mvnHome}/bin/mvn' package -DskipTests -X"
  }
  stage('Release') {
    echo "Deploy artifact : ${artifactId}";
    // deploy it in the staging repo
    sh "'${mvnHome}/bin/mvn' deploy -DskipTests -X"
    emailext body: '''Hi everybody,
      <br>
      <br>
      There is a new version available of <i>${JOB_NAME}</i>.
      <br>
      <br>
      The new version is: <b>''' + versionTag + '''</b>.
      <br>
      <br>
      The Changelog is available on:
      <br>
      <br>
      <a target='_blank' href='http://''' + gitRemoteUrl + ''''>http://''' + gitRemoteUrl + '''</a>
      <br>
      <br>
      Kind regards,
      <br>
      <br>
      Build-team''', subject: 'New version of ${JOB_NAME} - ' + versionTag, to: 'sido@haakma.org'
    currentBuild.result = 'SUCCESS';
  }
}
