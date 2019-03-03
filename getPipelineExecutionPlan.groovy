
def call() throws Exception {

  def manifest = readYaml file: "deployment-pipeline/manifest.yaml"

  // Validate 'gitCredentialId' is set
  if (manifest["gitCredentialId"] == null) {
    error("The 'gitCredentialId' property is required")
  }

  // Validate 'source' section
  if (manifest["source"] == null) {
    error("The 'source' property is required")
  }
  if (manifest["source"]["project"] == null) {
    error("The 'source.project' property is required")
  }
  if (manifest["source"]["url"] == null) {
    error("The 'source.url' property is required")
  }
  if (manifest["source"]["registryUrl"] == null) {
    error("The 'source.registryUrl' property is required")
  }
  if (manifest["source"]["tokenId"] == null) {
    error("The 'source.tokenId' property is required")
  } 

  // Validate destinationOptions section
  if (manifest["destinationOptions"] == null) {
    error("The 'destinationOptions' property is required")
  }
  if (manifest["destinationOptions"].size() == 0) {
    error("At least 1 'destinationOptions' element is required")
  }
  for (int i = 0; i < manifest["destinationOptions"].size(); i++) {
    if (manifest["destinationOptions"][i]["option"] == null) {
        error("The 'destinationOptions.option' property is required")
    }
    if (manifest["destinationOptions"][i]["clusters"] == null) {
        error("The 'destinationOptions.clusters' property is required")
    }
    if (manifest["destinationOptions"][i]["clusters"].size() == 0) {
      error("At least 1 'destinationOptions.clusters' element is required")
    }
    for (int j = 0; j < manifest["destinationOptions"][i]["clusters"].size(); j++) {
      if (manifest["destinationOptions"][i]["clusters"][j]["project"] == null) {
        error("The 'destinationOptions.project.clusters.project' property is required")
      }
      if (manifest["destinationOptions"][i]["clusters"][j]["dataCenter"] == null) {
        error("The 'destinationOptions.project.clusters.dataCenter' property is required")
      }
      if (manifest["destinationOptions"][i]["clusters"][j]["url"] == null) {
        error("The 'destinationOptions.project.clusters.url' property is required")
      }
      if (manifest["destinationOptions"][i]["clusters"][j]["registryUrl"] == null) {
        error("The 'destinationOptions.project.clusters.registryUrl' property is required")
      }
      if (manifest["destinationOptions"][i]["clusters"][j]["tokenId"] == null) {
        error("The 'destinationOptions.project.clusters.tokenId' property is required")
      }
    } // for j
  }  // for i

  // Validate 'services' section
  if (manifest["services"] == null) {
    error("The 'services' property is required")
  }
  if (manifest["services"].size() == 0) {
    error("At least 1 'services' element is required")
  }
  for (int i = 0; i < manifest["services"].size(); i++) {
    if (manifest["services"][i]["name"] == null) {
        error("The 'services.name' property is required")
    }
    if (manifest["services"][i]["version"] == null) {
        error("The 'services.version' property is required")
    }
    if (manifest["services"][i]["gitBranch"] == null) {
        error("The 'services.gitBranch' property is required")
    }
    if (manifest["services"][i]["templates"] == null) {
        error("The 'services.templates' property is required")
    }
    if (manifest["services"][i]["templates"].size() == 0) {
        error("At least 1 'services.templates' element is required")
    }
    for (int j = 0; j < manifest["services"][i]["templates"].size(); j++) {
      if (manifest["services"][i]["templates"][j]["name"] == null) {
        error("The 'services.templates.name' property is required")
      }
      if (manifest["services"][i]["templates"][j]["parameters"] == null) {
        error("The 'services.templates.parameters' property is required")
      }
      if (manifest["services"][i]["templates"][j]["parameters"].size() == 0) {
        error("At least 1 'services.templates.parameters' element is required")
      }
    } // for j
  }  // for i

  // Create an executionPlan object and populate from the manifest
  def executionPlan = [:]
  executionPlan.gitCredentialId = manifest["gitCredentialId"]

  // Add the source section
  executionPlan.source = [:]
  executionPlan.source.project = manifest["source"]["project"]
  executionPlan.source.url = manifest["source"]["url"]
  executionPlan.source.registryUrl = manifest["source"]["registryUrl"]
  executionPlan.source.tokenId = manifest["source"]["tokenId"]

  // Generate an options list for an input box from the list of available destination projects
  def destinationProjectChoices = []
  for (int i = 0; i < manifest["destinationOptions"].size(); i++) {
    destinationProjectChoices.push(manifest["destinationOptions"][i]["option"])
  }

  // Prompt the user to select the destination project
  def selectedOption = input(
    message: "Select destination project", 
    parameters: [choice(choices: destinationProjectChoices.join('\n'), 
    name: 'destProject')])

  // Add only the destionation project that was selected by the user
  executionPlan.destinationClusters = []
  for (int i = 0; i < manifest["destinationOptions"].size(); i++) {
    if (manifest["destinationOptions"][i]["option"] == selectedOption) {
      for (int j = 0; j < manifest["destinationOptions"][i]["clusters"].size(); j++) {
        executionPlan.destinationClusters.push(
          project: manifest["destinationOptions"][i]["clusters"][j]["project"],
          dataCenter: manifest["destinationOptions"][i]["clusters"][j]["dataCenter"],
          url: manifest["destinationOptions"][i]["clusters"][j]["url"],
          registryUrl: manifest["destinationOptions"][i]["clusters"][j]["registryUrl"],
          tokenId: manifest["destinationOptions"][i]["clusters"][j]["tokenId"]
        )
      }
    }
  }

  // Add the services from the manifest to the execution plan. Some will be removed as they are compared with
  // the current state
  executionPlan.services = []
  for (int i = 0; i < manifest["services"].size(); i++) {
      def templates = []
      for (int j = 0; j < manifest["services"][i]["templates"].size(); j++) {
        templates.push(
            name: manifest["services"][i]["templates"]["name"],
            parameters: manifest["services"][i]["templates"]["parameters"]
        )
      } // for j
      executionPlan.services.push(
        name: manifest["services"][i]["name"],
        version: manifest["services"][i]["version"],
        gitBranch: manifest["services"][i]["gitBranch"],
        templates: templates
      )
  } // for i


  // TODO: Refactor to do all work for each cluster.
  executionPlan.destinationClusters.each { cluster ->
    this.withCredentials([[$class: 'StringBinding', credentialsId: cluster.tokenId, variable: 'token']]) {
      sh """
        oc login ${cluster.url} --token=${token}
      """
      executionPlan.services.each { service ->
        // Get a list of all images that are currently deployed in the destination projects to see if they need to be deployed  
        def versionMatches = sh (
          script: """
            if [ \$(oc get istag ${service.name}:${service.version} -n ${cluster.project} -o jsonpath=\"{.image.dockerImageReference}\" 2>/dev/null || echo istagnotfound) == \$( oc get dc/${service.name} -n ${cluster.project} -o \"jsonpath={.spec.template.spec.containers[?(@.name=='${service.name}')].image}\" 2>/dev/null || echo dcnotfound) ]; then echo true; else echo false; fi
          """,
          returnStdout: true
        ).trim() == 'true'
        service.imageMatches = versionMatches
        print "service ${service.name}:${service.version} matches = '${versionMatches}'"

        // Get a list of deployment configs in the destination projects to determine if they need to be updated
        def deploymentConfigVersion = sh (
          script: "oc get dc/${service.name} -n ${cluster.project} -o=jsonpath={.metadata.labels.version} 2>/dev/null",
          returnStdout: true
        ).trim()
        service.configurationMatches = deploymentConfigVersion == service.version
        print "deploymentConfigVersion = ${deploymentConfigVersion}"

      }
    }
  }

  //Get a list of all available images from the source project to make sure they are available to be deployed
  this.withCredentials([[$class: 'StringBinding', credentialsId: executionPlan.source.tokenId, variable: 'token']]) {
    sh """
      oc login ${executionPlan.source.url} --token=${token}
    """
    executionPlan.services.each { service ->
      def sourceFound = sh (
        script: "oc get istag ${service.name}:${service.version} -n ${executionPlan.source.project}",
        returnStatus: true
      ) == 0
      service.sourceImageAvailable = sourceFound
      print "sourceFound = '${sourceFound}'"
    }
  } 


  // Print out a report of the execution plan
  def report = "column -t <<< 'Project DataCenter Service ImageChange ConfigChange SourceAvailable\n"
  report += "--------------- --------------- --------------- --------------- --------------- ---------------\n"
  executionPlan.destinationClusters.each { cluster ->
    executionPlan.services.each { service ->
      report += "${cluster.project} ${cluster.dataCenter} ${service.name} ${service.imageMatches} ${service.configurationMatches} ${service.sourceImageAvailable}\n"
    }
  }
  report += "'\n"
  sh "${report}"


  //     Project                          Service           Image Change      Config Change    Source Available
  // ============================================================================================================
  //  crew-stress (rr)    crew-demo-app:1.0.0-RELEASE         true              true               true
  //  crew-stress (ww)    crew-demo-app:1.0.0-RELEASE         true              true               true

  return executionPlan
}
