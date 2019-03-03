def call(Map config, Map executionPlan) {

  imageParams = executionPlan.imageParams
  imageParams = executionPlan.imageParams

  print "Validating Dep"

  token = "bQ64mgWzai0rLIqj1dtI5nS1_eIpG8eg2na9tw1vv00"
  
  for (int i = 0; i < imageParams.size(); i++) {
    def contextDir = imageParams[i].name
    def srcProject = imageParams[i].srcProject
    def template = imageParams[i].template
    def componentName = imageParams[i].name
    def branch = imageParams[i].branch
    def tag = imageParams[i].tag
    def deleted = imageParams[i].deleted
    loadScript(name: "verifyDeployment.sh")
    sh """
      ./verifyDeployment.sh \
        ${config.project} \
        ${token} \
        ${env.COMPONENT_NAME}
      """
  }

  for (int i = 0; i < imageParams.size(); i++) {

    def image = imageParams[i]["image"].getName()
    
    node(config.ocAgent) {
      config.project.login()
      def isTags = sh (
        script: "oc get istag --output=name -n ${config.project.getName()} | grep ${image}",
        returnStdout: true
      )
      if (isTags == "") print "Image is deleted"
      else print "Image is not deleted"
    }
  }
}