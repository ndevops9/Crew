def call (Map params = [:], Map executionPlan) {

def config = params.config
def imagesToPromote = executionPlan.imagesToPromote
def imagesToRemove = executionPlan.imagesToRemove

stage ('Promote to Stress') {
      // Set the currentStage
      currentStage = 'Promote to Stress'

      lock ("${config.projects.stressRRProject.getName()}") {

        def infrastructureSecretsConfigMapsAppliedRR = false
        def infrastructureSecretsConfigMapsAppliedWW = false

        for (int i = 0; i < imagesToPromote.size(); i++) {
          def image = imagesToPromote[i]["image"].getName()
          def namespace = imagesToPromote[i]["image"].getProject()
          def tag = imagesToPromote[i]["image"].getTag()
          def branch = imagesToPromote[i]["branch"]
          def strategy = imagesToPromote[i]["strategy"]
          def template = imagesToPromote[i]["template"]
          def currentStreamRef = imagesToPromote[i]["image"]
          def parameters = imagesToPromote[i]["parameters"]
          def shutdownPriority = imagesToPromote[i]["shutdownPriority"]
          def rrUpdateImage = imagesToPromote[i]["rrUpdateImage"]
          def rrUpdateResources = imagesToPromote[i]["rrUpdateResources"]
          def wwUpdateImage = imagesToPromote[i]["wwUpdateImage"]
          def wwUpdateResources = imagesToPromote[i]["wwUpdateResources"]
          def gitCommit = imagesToPromote[i]["gitCommit"]

          checkpoint "Promote ${image}"
          
          promotedImages.push(currentStreamRef)

          print "Installing ${image}:${tag}..."

          def stressBranches = [:]

          stressBranches["stressRR"] = {

            def curParametersRR = [:]
            curParametersRR << config.parameters
            curParametersRR << parameters

            // Set up DATA_CENTER environment variable
            curParametersRR["DATA_CENTER"] = "RR"
            print "The DATA_CENTER is: ${curParametersRR.'DATA_CENTER'}"

            curParametersRR.put("COMPONENT_NAME", image)
            curParametersRR.put("SOURCE_REPOSITORY_URL", "https://tfs.ups.com/tfs/UpsProd/P07AGit_FlightCrew/_git/${image}")

            // make ocpPromote scale to 0
            if (shutdownPriority != 99){
              curParametersRR.put("NUM_REPLICAS", 0)
            }

            if (!infrastructureSecretsConfigMapsAppliedRR) {
              applyConfigMaps(config: config, "crew-infrastructure", config.projects.stressRRProject, "feature-create-deployment-pipeline")
              applySecrets(config: config, "crew-infrastructure",config.projects.stressRRProject, "feature-create-deployment-pipeline")
              infrastructureSecretsConfigMapsAppliedRR = true
            }

            applyConfigMaps(config: config, image, config.projects.stressRRProject, branch)
            applySecrets(config: config, image, config.projects.stressRRProject, branch)

            if (rrUpdateImage) {

              // Promote to the integration environment
              print "Promoting ${namespace.getName()}/${image}:${tag} to ${config.projects.stressRRProject.getName()}/${image}:latest..."
              stressRRStreamRef = ocpPromote(
                sourceImageStreamRef: currentStreamRef,
                destinationProject: config.projects.stressRRProject,
                destinationStream: image,
                destinationTag: tag,
                agent: config.skopeoAgent,
                template: template,
                keep: { ocpObj -> !(ocpObj.kind.equals('BuildConfig')) },
                parameters: curParametersRR
              )
              print 'Promotion successful'

              node (config.ocAgent) {
                config.projects.stressRRProject.login()

                // Tag the image with the appropriate commit id
                print "Tagging ${config.projects.stressRRProject.getName()}/${image}:latest as ${config.projects.stressRRProject.getName()}/${image}:${tag}..."
                stressRRStreamRef = stressRRStreamRef.tag('latest')
                print "Tagging successful"

                print "Labeling ${image} with version..."
                config.projects.stressRRProject.login()
                sh """
                  oc label all -l microservice=${image} \
                    version=${tag} \
                    --overwrite \
                    -n ${config.projects.stressRRProject.getName()}
                """
                print "Objects labeled"

                //input message: "deployment of ${image} in RR completed, proceed?"

              } // node
            } else if (rrUpdateResources) {

              // Promote to the integration environment
              print "Updating objects for ${config.projects.stressRRProject.getName()}/${image}:${tag}..."
              ocpApplyTemplate(
                destinationProject: config.projects.stressRRProject,
                agent: config.ocAgent,
                template: template,
                keep: { ocpObj -> !(ocpObj.kind.equals('BuildConfig')) },
                parameters: curParametersRR
              )
              print 'Update successful'

            } // if

            node(config.ocAgent){
              config.projects.stressRRProject.login()

              sh """
                oc label all -l microservice=${image} \
                  gitCommit=${gitCommit} \
                  --overwrite \
                  -n ${config.projects.stressRRProject.getName()}
              """
            } // node
          } // stressRR branch

          stressBranches["stressWW"] = {

            def curParametersWW = [:]
            curParametersWW << config.parameters
            curParametersWW << parameters

            // Set up DATA_CENTER environment variable
            curParametersWW["DATA_CENTER"] = "WW"
            print "The DATA_CENTER is: ${curParametersWW.'DATA_CENTER'}"

            curParametersWW.put("COMPONENT_NAME", image)
            curParametersWW.put("SOURCE_REPOSITORY_URL", "https://tfs.ups.com/tfs/UpsProd/P07AGit_FlightCrew/_git/${image}")

            // make ocpPromote scale to 0
            if (shutdownPriority != 99){
              curParametersWW.put("NUM_REPLICAS", 0)
            }

            if (!infrastructureSecretsConfigMapsAppliedWW) {
              applyConfigMaps(config: config, "acars-infrastructure", config.projects.stressWWProject, "development")
              applySecrets(config: config, "acars-infrastructure", config.projects.stressWWProject, "development")
              infrastructureSecretsConfigMapsAppliedWW = true
            }

            applyConfigMaps(config: config, image, config.projects.stressWWProject, branch)
            applySecrets(config: config, image, config.projects.stressWWProject, branch)

            print "params: ${curParametersWW}"

            if (wwUpdateImage) {

              // Promote to the integration environment
              print "Promoting ${namespace.getName()}/${image}:${tag} to ${config.projects.stressWWProject.getName()}/${image}:latest..."
              stressWWStreamRef = ocpPromote(
                sourceImageStreamRef: currentStreamRef,
                destinationProject: config.projects.stressWWProject,
                destinationStream: image,
                destinationTag: tag,
                agent: config.skopeoAgent,
                template: template,
                keep: { ocpObj -> !(ocpObj.kind.equals('BuildConfig')) },
                parameters: curParametersWW
              )
              print 'Promotion successful'

              node (config.ocAgent) {
                config.projects.stressWWProject.login()

                // Tag the image with the appropriate commit id
                print "Tagging ${config.projects.stressWWProject.getName()}/${image}:latest as ${config.projects.stressWWProject.getName()}/${image}:${tag}..."
                stressWWStreamRef = stressWWStreamRef.tag('latest')
                print "Tagging successful"

                print "Labeling ${image} with version..."
                config.projects.stressWWProject.login()
                sh """
                  oc label all -l microservice=${image} \
                    version=${tag} \
                    --overwrite \
                    -n ${config.projects.stressWWProject.getName()}
                """
                print "Objects labeled"

                //input message: "deployment of ${image} in WW completed, proceed?"

              } // node
            } else if (wwUpdateResources) {

              // Promote to the integration environment
              print "Updating objects for ${config.projects.stressWWProject.getName()}/${image}:${tag}..."
              ocpApplyTemplate(
                destinationProject: config.projects.stressWWProject,
                agent: config.ocAgent,
                template: template,
                keep: { ocpObj -> !(ocpObj.kind.equals('BuildConfig')) },
                parameters: curParametersWW
              )
              print 'Update successful'

            } // if

            node(config.ocAgent){
              config.projects.stressWWProject.login()

              sh """
                oc label all -l microservice=${image} \
                  gitCommit=${gitCommit} \
                  --overwrite \
                  -n ${config.projects.stressWWProject.getName()}
              """
            } // node
          } // stressWW branch

          parallel stressBranches

        } // for

        // if (stressTest != null) {
        //   // Run the test closure
        //   print "Running stress tests..."
        //
        //   testingProject = config.projects.stressRRProject
        //   stressTest()
        //
        //   testingProject = config.projects.stressWWProject
        //   stressTest()
        //
        // }

        node(config.ocAgent){

          print "Tagging image in release projects with 'stress-tested'..."
          print promotedImages
          for (int i = 0; i < promotedImages.size(); i++){
            promotedImages[i] = promotedImages[i].tag("stress-tested")
          }
          print "Tagging successful"
        } // node

      } // lock
    } // stage

    checkpoint "Remove Images from Stress"

    stage ("Remove Images from Stress"){
      for (int j = 0; j < imagesToRemove.size(); j++){
        def image = imagesToRemove[j]

        print "Removing ${image} from stress projects..."

        def stressRemoveBranches = [:]

        stressRemoveBranches["stressRR"] = {
          node(config.ocAgent){

            config.projects.stressRRProject.login()

            try {
              def isTags = sh (
                script: "oc get istag --output=name -n ${config.projects.stressRRProject.getName()} | grep ${image}",
                returnStdout: true
              )
              def isTagsList = isTags.replaceAll(' ','').split('\n')

              sh """
                oc delete all -l microservice=${image} -n ${config.projects.stressRRProject.getName()}
              """
              for (int x = 0; x < isTagsList.size(); x++){
                sh """
                  oc delete ${isTagsList[x]} -n ${config.projects.stressRRProject.getName()}
                """
              }
            } catch (removeErr){
              print "Resources for ${image} already removed."
            }

          } // node
        } // prodRR

        stressRemoveBranches["stressWW"] = {
          node(config.ocAgent){

            config.projects.stressWWProject.login()

            try {
              def isTags = sh (
                script: "oc get istag --output=name -n ${config.projects.stressWWProject.getName()} | grep ${image}",
                returnStdout: true
              )
              def isTagsList = isTags.replaceAll(' ','').split('\n')

              sh """
                oc delete all -l microservice=${image} -n ${config.projects.stressWWProject.getName()}
              """
              for (int x = 0; x < isTagsList.size(); x++){
                sh """
                  oc delete ${isTagsList[x]} -n ${config.projects.stressWWProject.getName()}
                """
              }
            } catch (removeErr){
              print "Resources for ${image} already removed."
            }

          } // node
        } // prodWW

        parallel stressRemoveBranches

        print "${image} removed."

      } // for
    } // stage

}