/**
 */

def call(body) {
  def pipelineParams = [:]
  def config = body.config
  def app = config.app
  def deployWaitTime = config.deployWaitTime

  def executionPlan = [:]
  def destinationProject

  pipeline {
    agent none

    environment {
      GIT_CREDENTIAL = credentials("${config.gitCredential}")
    }

    stages {

      stage("Determine Execution Plan") {
        agent {label 'jenkins-agent-base-39'}

        steps {
          script {
            executionPlan = getPipelineExecutionPlan()
          }
        }
      }

      stage("Execute Deployment") {
        agent {label config.skopeoAgent}

        steps {
          script {

            // lock ("${config.projects.stressRRProject.getName()}")
            // TODO: Add lock - lock name = "${app}-deployment-${destProject}"
            executionPlan.destinationClusters.each { cluster ->
              executionPlan.services.each { service ->
                this.withCredentials([[$class: 'StringBinding', credentialsId: cluster.tokenId, variable: 'destToken']]) {

                  sh """ 
                    git clone "https://${GIT_CREDENTIAL}@tfs.ups.com/tfs/UpsProd/P07AGit_FlightCrew/_git/${service.name}" -b ${service.gitBranch}
                  """
                  
                  // TODO:
                  // if (deleted)
                  // oc login ${cluster.url} ${destToken}; oc delete all -n ${cluster.project} -l component=${service.name}
                  // else
                  // ocApply; ocPromote
                  // if (!service.sourceImageAvailable) then delete?

                  // if change detected, execute changes
                  if (service.sourceImageAvailable) {
                    if (service.configurationMatches || service.imageMatches) {
                      for (int i = 0; i < service["templates"].size(); i++) {
                        env.COMPONENT_NAME = service.name
                        env.CONTEXT_DIR = service.name
                        env.OCP_TEMPLATE = service.templates[i].name[i]

                        print "ApplyTemplate ${env.OCP_TEMPLATE}"
                        print "Test ${service.parameters}"
                      
                        ocpApplyTemplate(
                          params: service.parameters,
                          project: cluster.project,
                          cluster: cluster.url,
                          token: destToken
                        )

                        this.withCredentials([[$class: 'StringBinding', credentialsId: executionPlan.source.tokenId, variable: 'srcToken']]) {
                          ocpPromoteInterCluster(
                            srcProject: executionPlan.source.project,
                            srcToken: srcToken,
                            srcRegistry: executionPlan.source.registryUrl,
                            destProject: cluster.project,
                            destToken: destToken,
                            destRegistry: cluster.registryUrl
                          )
                        }
                      }//for
                    }//if
                  }//if
                }//with creds dest 
              }//each service
            }//each cluster
          }//script
        }//steps
      }//stage

      stage("Validate Deployment") {
        agent {label 'jenkins-agent-skopeo-39'}
        steps {
          script {
            executionPlan.destinationClusters.each { cluster ->
              this.withCredentials([[$class: 'StringBinding', credentialsId: cluster.tokenId, variable: 'destToken']]) {
                
                // TODO: Verify for deletes

                ocpDeploy(
                      waitTime: deployWaitTime,
                      project: cluster.project,
                      cluster: cluster.url,
                      token: destToken
                    )
              }

            }//each cluster
          }//script
        }//steps
      } // stage
    } // stages
  } // pipeline
}
