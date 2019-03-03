import com.ups.iae.jenkins.openshift.*

/**
 * DSL Step for configuring ACARS microservices
 * @param  Map params = [:] Map of parameters. Accepts the following:
 * component (String) Name of the pipeline's target component
 */
def call(Map params = [:]) throws Exception {
  // Define a config object to store various pipeline configurations
  def config = {}

  // Set the oc-cli Jenkins agent label
  config.ocAgent = 'jenkins-agent-base'

  // Set the skopeo agent
  config.skopeoAgent = 'jenkins-agent-skopeo'

  // Set the app name
  config.app = 'crew-demo-app'

  BranchType branchType = BranchType.getType("release-stress")
  config.branchType = branchType

  // Set the default promptTimeout for clean up (in minutes)
  config.promptTimeout = 1

  // Set default wait time for deployments
  config.deployWaitTime = 960

  // Instantiate cluster(s)
  Cluster dev = new Cluster(this, 'dev',
    'https://master-paas-dev-njrar-01.ams1907.com',
    'registry.paas-dev-njrar-01.ams1907.com')
  Cluster stressRR = new Cluster (this, 'stress-rr',
    'https://master-paas-stress-njrar-01.ams1907.com',
    'registry-njrar-01.paas-stress.ams1907.com')
  Cluster stressWW = new Cluster (this, 'stress-ww',
    'https://master-paas-stress-gaalp-01.ams1907.com',
    'registry-gaalp-01.paas-stress.ams1907.com')

  // Instantiate the clusters map
  config.clusters = [
    'dev': dev,
    'stress-rr': stressRR,
    'stress-ww': stressWW
  ]

  // Instantiate the projects map
  config.projects = [
    'developBuild': developBuild,
    'developIntegration': developIntegration,
    'developAcceptance': developAcceptance,
    'releaseBuild': releaseBuild,
    'releaseIntegration': releaseIntegration,
    'releaseAcceptance': releaseAcceptance,
    'releaseHotfix': releaseHotfix,
    'developStressRR': developStressRR,
    'developStressWW': developStressWW
  ]

  config.projects.buildProject = developBuild
  config.projects.integrationProject = developIntegration
  config.projects.acceptanceProject = developAcceptance
  config.projects.releaseBuildProject = releaseBuild
  config.projects.releaseIntegrationProject = releaseIntegration
  config.projects.releaseAcceptanceProject = releaseAcceptance
  config.projects.releaseHotfixProject = releaseHotfix
  config.projects.stressRRProject = developStressRR
  config.projects.stressWWProject = developStressWW

  config.parameters = [
      APPLICATION_ID: env.APPLICATION_ID,
      APPLICATION_NAME: env.APPLICATION_NAME 
  ]
  
  return config
}
