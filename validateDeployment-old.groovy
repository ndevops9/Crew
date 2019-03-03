def call(Map config, Map executionPlan) {

  imagesToPromote = executionPlan.imagesToPromote
  imageToRemove = executionPlan.imagesToRemove

  token = "bQ64mgWzai0rLIqj1dtI5nS1_eIpG8eg2na9tw1vv00"
  
  for (int i = 0; i < imagesToPromote.size(); i++) {
    def image = imagesToPromote[i]["image"].getName()
    def tag = imagesToPromote[i]["image"].getTag()
    def rrUpdateImage = imagesToPromote[i]["rrUpdateImage"]
    def rrUpdateResources = imagesToPromote[i]["rrUpdateResources"]
    def wwUpdateImage = imagesToPromote[i]["wwUpdateImage"]
    def wwUpdateResources = imagesToPromote[i]["wwUpdateResources"]
    loadScript(name: "verifyDeployment.sh")
    sh """
      ./verifyDeployment.sh \
        ${config.project} \
        ${token} \
        ${env.COMPONENT_NAME}
      """
  }

  for (int i = 0; i < imagesToRemove.size(); i++) {

    def image = imagesToRemove[i]["image"].getName()
    
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