/**
 * Runs the pipeline according to gitflow conventions.
 * @params body = takes an object with the following parameters:
 *   (String, Required) name - microservice name
 *   (String, Required) app - application name
 *   (String, Required) contextDir - path to microservice in git repo
 *   (String, Required) ocpTemplate - name of template
 *   (Map, Optional) buildProjectConfig - map of parameters to use in build project
 *   (Map, Optional) integrationProjectConfig - map of parameters to use in integration project
 *   (Map, Optional) acceptanceProjectConfig - map of parameters to use in acceptance project
 *   (Map, Optional) stressProjectConfig - map of parameters to use in stress project
 *   (Map, Optional) prodProjectConfig - map of parameters to use in prod project
 *   (Array,  Optional) inclusions - list of strings names of required microservices
 *   (Map,  Optional) integrationTest - map of parameters used to run the test
 *     (String,  Required) testScript - test path/script to be run
 *     (String,  Required) localRsyncDirectory - directory copied from Jenkins agent
 *     (String,  Required) remoteRsyncDirectory - directory copied to remote pod
 *     (Number,  Required) timeoutHours - Hours to wait after executing integration test
 *     (Map,  Required) params - map of params substituted for test template
 *       (String,  Required) NAME - name given to the test pod
 *       (String,  Required) IMAGE_STREAM_NAME - image to use for pod creation
 *       (String,  Required) IMAGE_STREAM_NAMESPACE - namespace where the image resides
 *   (Map,  Optional) stressTest - map of parameters used to run the test
 *     (String,  Required) testScript - test path/script to be run
 *     (String,  Required) localRsyncDirectory - directory copied from Jenkins agent
 *     (String,  Required) remoteRsyncDirectory - directory copied to remote pod
 *     (Number,  Required) timeoutHours - Hours to wait after executing stress test
 *     (Map,  Required) params - map of params substituted for test template
 *       (String,  Required) NAME - name given to the test pod
 *       (String,  Required) IMAGE_STREAM_NAME - image to use for pod creation
 *       (String,  Required) IMAGE_STREAM_NAMESPACE - namespace where the image resides
 *   (Map, Required) gates - map to activate gates
 *     (Boolean, Required) integration - gate active if true
 *     (Boolean, Required) stress - gate active if true
 *     (Boolean, Required) acceptance - gate active if true
 */

def call(body) {
  def pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()
  def hook = null

  if (env.BRANCH_NAME == "development") {
    ACCEPTANCE_PROJECT = "crew-uat"
  }

  if (env.BRANCH_NAME == "release-crew-demo") {
    BUILD_PROJECT = "crew-release-build"
    INTEGRATION_PROJECT = "crew-release-integration"
    ACCEPTANCE_PROJECT = "crew-release-uat"
  }

  pipeline {
    agent none

    environment {
      COMPONENT_NAME = "${pipelineParams.name}"
      APPLICATION_NAME = "${pipelineParams.app}"
      CONTEXT_DIR = "${pipelineParams.contextDir}"
      OCP_TEMPLATE = "${pipelineParams.ocpTemplate}"

      DEV_CLUSTER_API_URL = 'https://master-paas-dev-njrar-02.ams1907.com'
//      STRESS_RR_CLUSTER_API_URL = 'https://master-paas-stress-njrar-02.ams1907.com'
//      STRESS_WW_CLUSTER_API_URL = 'https://master-paas-stress-gaalp-02.ams1907.com'
//      PROD_RR_CLUSTER_API_URL = 'https://master-paas-prod-njrar-02.ups.com'
//      PROD_WW_CLUSTER_API_URL = 'https://master-paas-prod-gaalp-02.ups.com'

      DEV_DOCKER_REGISTRY_URL = 'registry.paas-dev-njrar-02.ams1907.com'
//      STRESS_RR_DOCKER_REGISTRY_URL = 'registry-njrar-02.paas-stress.ams1907.com'
//      STRESS_WW_DOCKER_REGISTRY_URL = 'registry-gaalp-02.paas-stress.ams1907.com'
//      PROD_RR_DOCKER_REGISTRY_URL = 'registry-njrar-02.paas.ups.com'
//      PROD_WW_DOCKER_REGISTRY_URL = 'registry-gaalp-02.paas.ups.com'

      BUILD_PROJECT = getBuildProject(pipelineParams)
      INTEGRATION_PROJECT = getIntegrationProject(pipelineParams)
//      STRESS_PROJECT = getStressProject(pipelineParams)
      ACCEPTANCE_PROJECT = getAcceptanceProject(pipelineParams)
//      PROD_PROJECT = getProdProject(pipelineParams)

      DEV_SERVICE_ACCOUNT_TOKEN = credentials("${pipelineParams.app}-build-token")
//      STRESS_RR_SERVICE_ACCOUNT_TOKEN = credentials("${pipelineParams.app}-stress-rr-token")
//      STRESS_WW_SERVICE_ACCOUNT_TOKEN = credentials("${pipelineParams.app}-stress-ww-token")
//      PROD_RR_SERVICE_ACCOUNT_TOKEN = credentials("${pipelineParams.app}-prod-rr-token")
//      PROD_WW_SERVICE_ACCOUNT_TOKEN = credentials("${pipelineParams.app}-prod-ww-token")
    }

    stages {

      stage("Build/Deploy in Dev") {
        agent {label 'jenkins-agent-base-39'}

        when { anyOf { branch 'feature*'; branch 'dev*'; branch 'release*'; branch 'hotfix*' } }

        steps {
          setupFeatureProject(
            project: getForkProject(pipelineParams.app),
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN,
            inclusions: pipelineParams?.inclusions
          )

          // Process and apply OCP template
          ocpApplyTemplate(
            params: pipelineParams.buildProjectConfig,
            project: env.BUILD_PROJECT,
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )
            sh """
              oc tag crew-stress/$COMPONENT_NAME:latest crew-stress/$COMPONENT_NAME:$TAG
            """
          // Load build script and start build
          loadScript(name: "ocpBuild.sh")
          sh "./ocpBuild.sh"

          // Process and apply OCP template
          ocpDeploy(
            waitTime: pipelineParams.deployWaitTime,
            project: env.BUILD_PROJECT,
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )
        }
      } // stage

      stage("Promote to Integration Checkpoint") {
        agent none

        // Only run this stage for dev/release/hotfix branches
        when {
          anyOf { branch 'release*'; branch 'hotfix*'; branch 'dev*' }
          expression { return pipelineParams?.gates?.integration }
        }

        steps { checkpoint "Promote to Integration" }
      } // stage

      stage("Promote to Integration Gate") {
        agent none

        // Only run this stage for dev/release/hotfix branches
        when {
          anyOf {  branch 'release*'; branch 'hotfix*'; branch 'dev*' }
          expression { return pipelineParams?.gates?.integration }
        }

        steps { input message: "Promote to Integration?" }
      } // stage

      stage("Promote/Deploy in Integration") {
        agent {label 'jenkins-agent-base-39'}

        // Only run this stage for dev/release branches
        when { anyOf { branch 'dev*'; branch 'release*' } }

        steps {
          // Process and apply OCP template
          ocpApplyTemplate(
            params: pipelineParams.integrationProjectConfig,
            project: env.INTEGRATION_PROJECT,
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )

          // Tag image from build project to integration project
          ocpPromoteIntraCluster(
            srcProject: env.BUILD_PROJECT,
            destProject: env.INTEGRATION_PROJECT,
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )

          // Follow new deployment
          ocpDeploy(
            waitTime: pipelineParams.deployWaitTime,
            project: env.INTEGRATION_PROJECT,
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )
        }
      } // stage


/*
      stage("Run Integration Tests") {
        agent {label 'jenkins-agent-base-39'}

        // Only run this stage for dev/release branches
        when {
          anyOf { branch 'feature*'; branch 'dev*'; branch 'release*'; branch 'hotfix*' }
          expression { return pipelineParams?.integrationTest }
        }

        steps {
          ocpTest(
            params: pipelineParams.integrationTest.params,
            localRsyncDirectory: pipelineParams.integrationTest.localRsyncDirectory,
            remoteRsyncDirectory: pipelineParams.integrationTest.remoteRsyncDirectory,
            testScript: pipelineParams.integrationTest.testScript,
            project: getIntegrationTestProject(),
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )
        }
      } // stage

      stage("Wait for Integration Test Input") {
        agent none

        // Only run this stage for dev/release branches
        when {
          anyOf { branch 'feature*'; branch 'dev*'; branch 'release*'; branch 'hotfix*' }
          expression { return pipelineParams?.integrationTest }
        }

        steps {
          timeout(time: pipelineParams.integrationTest.timeoutHours, unit: 'HOURS') {
            input id: "Test-webhook", message: "Waiting for test results. Don't click me."
          }
        }
      } // stage

      stage("Collect Integration Test Results") {
        agent {label 'jenkins-agent-base-39'}

        // Only run this stage for dev/release branches
        when {
          anyOf { branch 'feature*'; branch 'dev*'; branch 'release*'; branch 'hotfix*' }
          expression { return pipelineParams?.integrationTest }
        }

        steps {
          ocpTestResults(
            params: pipelineParams.integrationTest.params,
            localRsyncDirectory: pipelineParams.integrationTest.localRsyncDirectory,
            remoteRsyncDirectory: pipelineParams.integrationTest.remoteRsyncDirectory,
            project: getIntegrationTestProject(),
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )
        }
      } // stage

      stage("Promote to Stress Checkpoint") {
        agent none

        // Only run this stage for dev/release/hotfix branches
        when {
          //branch 'hotfix*';
          anyOf { branch 'release*'; branch 'dev*' }
          expression { return pipelineParams?.gates?.stress }
        }

        steps { checkpoint "Promote to Stress" }
      } // stage

      stage("Promote to Stress Gate") {
        agent none

        // Only run this stage for dev/release/hotfix branches
        when {
          //branch 'hotfix*';
          anyOf { branch 'release*'; branch 'dev*' }
          expression { return pipelineParams?.gates?.stress }
        }

        steps { input message: "Promote to Stress?" }
      } // stage

      stage("Promote/Deploy in Stress") {

        // Only run this stage for dev/release branches
        when { anyOf { branch 'dev*'; branch 'release*' } }

        parallel {
          stage("Stress RR") {
            agent {label 'jenkins-agent-skopeo-39'}

            steps {
              // Process and apply OCP template
              ocpApplyTemplate(
                params: pipelineParams.stressProjectConfig,
                project: env.STRESS_PROJECT,
                cluster: env.STRESS_RR_CLUSTER_API_URL,
                token: env.STRESS_RR_SERVICE_ACCOUNT_TOKEN
              )

              // Tag image from integration project to stress RR project
              ocpPromoteInterCluster(
                srcProject: env.INTEGRATION_PROJECT,
                srcToken: env.DEV_SERVICE_ACCOUNT_TOKEN,
                srcRegistry: env.DEV_DOCKER_REGISTRY_URL,
                destProject: env.STRESS_PROJECT,
                destToken: env.STRESS_RR_SERVICE_ACCOUNT_TOKEN,
                destRegistry: env.STRESS_RR_DOCKER_REGISTRY_URL
              )

              // Follow new deployment
              ocpDeploy(
                waitTime: pipelineParams.deployWaitTime,
                project: env.STRESS_PROJECT,
                cluster: env.STRESS_RR_CLUSTER_API_URL,
                token: env.STRESS_RR_SERVICE_ACCOUNT_TOKEN
              )
            } // steps
          } // stage

          stage("Stress WW") {
            agent {label 'jenkins-agent-skopeo'}

            steps {
              // Process and apply OCP template
              ocpApplyTemplate(
                params: pipelineParams.stressProjectConfig,
                project: env.STRESS_PROJECT,
                cluster: env.STRESS_WW_CLUSTER_API_URL,
                token: env.STRESS_WW_SERVICE_ACCOUNT_TOKEN
              )

              // Tag image from integration project to stress RR project
              ocpPromoteInterCluster(
                srcProject: env.INTEGRATION_PROJECT,
                srcToken: env.DEV_SERVICE_ACCOUNT_TOKEN,
                srcRegistry: env.DEV_DOCKER_REGISTRY_URL,
                destProject: env.STRESS_PROJECT,
                destToken: env.STRESS_WW_SERVICE_ACCOUNT_TOKEN,
                destRegistry: env.STRESS_WW_DOCKER_REGISTRY_URL
              )

              // Follow new deployment
              ocpDeploy(
                waitTime: pipelineParams.deployWaitTime,
                project: env.STRESS_PROJECT,
                cluster: env.STRESS_WW_CLUSTER_API_URL,
                token: env.STRESS_WW_SERVICE_ACCOUNT_TOKEN
              )
            } // steps
          } // stage
        } // parallel
      } // stage

      stage("Run Stress Tests") {
        agent {label 'jenkins-agent-base-39'}

        // Only run this stage for dev/release/hotfix branches
        when {
          //branch 'hotfix*'
          anyOf { branch 'dev*'; branch 'release*'; }
          expression { return pipelineParams?.stressTest }
        }

        steps {
          ocpTest(
            params: pipelineParams.stressTest.params,
            localRsyncDirectory: pipelineParams.stressTest.localRsyncDirectory,
            remoteRsyncDirectory: pipelineParams.stressTest.remoteRsyncDirectory,
            testScript: pipelineParams.stressTest.testScript,
            project: env.STRESS_PROJECT,
            cluster: env.STRESS_RR_CLUSTER_API_URL,
            token: env.STRESS_RR_SERVICE_ACCOUNT_TOKEN
          )
        }
      } // stage

      stage("Wait for Stress Test Input") {
        agent none

        // Only run this stage for dev/release branches
        when {
          //branch 'hotfix*'
          anyOf { branch 'dev*'; branch 'release*'; }
          expression { return pipelineParams?.stressTest }
        }

        steps {
          timeout(time: pipelineParams.stressTest.timeoutHours, unit: 'HOURS') {
            input id: "Test-webhook", message: "Waiting for test results. Don't click me."
          }
        }
      } // stage

      stage("Collect Stress Test Results") {
        agent {label 'jenkins-agent-base-39'}

        // Only run this stage for dev/release branches
        when {
          //branch 'hotfix*'
          anyOf { branch 'dev*'; branch 'release*'; }
          expression { return pipelineParams?.stressTest }
        }

        steps {
          ocpTestResults(
            params: pipelineParams.stressTest.params,
            localRsyncDirectory: pipelineParams.stressTest.localRsyncDirectory,
            remoteRsyncDirectory: pipelineParams.stressTest.remoteRsyncDirectory,
            project: env.STRESS_PROJECT,
            cluster: env.STRESS_RR_CLUSTER_API_URL,
            token: env.STRESS_RR_SERVICE_ACCOUNT_TOKEN
          )
        }
      } // stage
*/
      stage("Promote to Acceptance Checkpoint") {
        agent none

        // Only run this stage for dev/release/hotfix branches
        when {
          //branch 'hotfix*';
          anyOf { branch 'release*'; branch 'dev*' }
          expression { return pipelineParams?.gates?.acceptance }
        }

        steps { checkpoint "Promote to Acceptance" }
      } // stage

      stage("Promote to Acceptance Gate") {
        agent none

        // Only run this stage for dev/release/hotfix branches
        when {
          //branch 'hotfix*';
          anyOf { branch 'release*'; branch 'dev*' }
          expression { return pipelineParams?.gates?.acceptance }
        }

        steps { input message: "Promote to Acceptance?" }
      } // stage

      stage("Promote/Deploy in Acceptance") {
        agent {label 'jenkins-agent-skopeo-39'}

        // Only run this stage for dev/release/hotfix branches

        when { anyOf {  branch 'dev*'; branch 'release*'; branch 'hotfix*' } }

        steps {
          // Process and apply OCP template
          ocpApplyTemplate(
            params: pipelineParams.acceptanceProjectConfig,
            project: env.ACCEPTANCE_PROJECT,
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )

          // Tag image from integration project to acceptance project
          ocpPromoteIntraCluster(
            srcProject: env.INTEGRATION_PROJECT,
            destProject: env.ACCEPTANCE_PROJECT,
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )

          // Follow new deployment
          ocpDeploy(
            waitTime: pipelineParams.deployWaitTime,
            project: env.ACCEPTANCE_PROJECT,
            cluster: env.DEV_CLUSTER_API_URL,
            token: env.DEV_SERVICE_ACCOUNT_TOKEN
          )
        }
      } // stage
/*
      stage("Promote to Production Checkpoint") {
        agent none

        // Only run this stage for release/hotfix branches
        when { anyOf { branch 'release*'; branch 'hotfix*' } }

        steps { checkpoint "Promote to Production" }
      } // stage

      stage("Promote to Production Gate") {
        agent none

        // Only run this stage for release/hotfix branches
        when { anyOf { branch 'release*'; branch 'hotfix*' } }

        steps {
          // Show change control ID input and check CCManager API
          checkChangeControl()
          input message: "Promote to Production?"
        }
      } // stage

      stage("Promote to Production") {
        // Only run this stage for release/hotfix branches
        when { anyOf { branch 'release*'; branch 'hotfix*' } }

        parallel {
          stage("Prod RR") {
            agent {label 'jenkins-agent-skopeo-39'}

            steps {
              // Process and apply OCP template
              ocpApplyTemplate(
                params: pipelineParams.prodProjectConfig,
                project: env.PROD_PROJECT,
                cluster: env.PROD_RR_CLUSTER_API_URL,
                token: env.PROD_RR_SERVICE_ACCOUNT_TOKEN
              )

              // Tag image from integration project to stress RR project
              ocpPromoteInterCluster(
                srcProject: env.ACCEPTANCE_PROJECT,
                srcToken: env.DEV_SERVICE_ACCOUNT_TOKEN,
                srcRegistry: env.DEV_DOCKER_REGISTRY_URL,
                destProject: env.PROD_PROJECT,
                destToken: env.PROD_RR_SERVICE_ACCOUNT_TOKEN,
                destRegistry: env.PROD_RR_DOCKER_REGISTRY_URL
              )

              // Follow new deployment
              ocpDeploy(
                waitTime: pipelineParams.deployWaitTime,
                project: env.PROD_PROJECT,
                cluster: env.PROD_RR_CLUSTER_API_URL,
                token: env.PROD_RR_SERVICE_ACCOUNT_TOKEN
              )
            } // steps
          } // stage

          stage("Prod WW") {
            agent {label 'jenkins-agent-skopeo-39'}

            steps {
              // Process and apply OCP template
              ocpApplyTemplate(
                params: pipelineParams.prodProjectConfig,
                project: env.PROD_PROJECT,
                cluster: env.PROD_WW_CLUSTER_API_URL,
                token: env.PROD_WW_SERVICE_ACCOUNT_TOKEN
              )

              // Tag image from integration project to stress RR project
              ocpPromoteInterCluster(
                srcProject: env.ACCEPTANCE_PROJECT,
                srcToken: env.DEV_SERVICE_ACCOUNT_TOKEN,
                srcRegistry: env.DEV_DOCKER_REGISTRY_URL,
                destProject: env.PROD_PROJECT,
                destToken: env.PROD_WW_SERVICE_ACCOUNT_TOKEN,
                destRegistry: env.PROD_WW_DOCKER_REGISTRY_URL
              )

              // Follow new deployment
              ocpDeploy(
                waitTime: pipelineParams.deployWaitTime,
                project: env.PROD_PROJECT,
                cluster: env.PROD_WW_CLUSTER_API_URL,
                token: env.PROD_WW_SERVICE_ACCOUNT_TOKEN
              )
            } // steps
          } // stage
        } // parallel
      } // stage
*/
    } // stages

    post {
      always { deleteFeatureProject(cluster: env.DEV_CLUSTER_API_URL, token: env.DEV_SERVICE_ACCOUNT_TOKEN) }
    }
  }
}
