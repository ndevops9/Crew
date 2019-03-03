/**
 * Processes and applies an openshift template
 * @params config = takes a map with the following parameters:
 *   (String, Required) project - project name
 *   (String, Required) cluster - cluster name
 *   (String, Required) token - service account token
 *   (String, Required) params - a map of params to be used when processing the template
 */

def call(Map config) {

  // grab any templates with 's2i' in the name
  def templateFiles = null
  if ( env.CONTEXT_DIR == "/" || env.CONTEXT_DIR == "." || !env.CONTEXT_DIR ) {
      templateFiles = findFiles(glob: "**/ocp/templates/${env.OCP_TEMPLATE}")
  } else {
      templateFiles = findFiles(glob: "**/${env.CONTEXT_DIR}/ocp/templates/${env.OCP_TEMPLATE}")
  }
  templatePath  = templateFiles[0].path

  // if non-build project, remove build config from JSON file
  if (config.project != env.BUILD_PROJECT) {
    def templateInput = readJSON file: templatePath

    objects = templateInput["objects"]

    for (int i = 0; i < objects.size(); i++) {
      if (objects[i]["kind"] == "BuildConfig"){
        objects.remove(i)
        writeJSON file: templatePath,
          json: templateInput

        break
      } // if
    } // for
  } // if

  // grab params from Jenkinsfile and turn into string
  String paramArgs = ""
  config.params.each { key, value ->
    paramArgs += " $key=$value"
  }
  println "Parameter Arguments are : " + paramArgs
  env.OCP_PARAMS = paramArgs

  // pass in current project, cluster, and service account token
  loadScript(name: "processTemplate.sh")
  sh "./processTemplate.sh ${config.project} ${config.cluster} ${config.token} ${templatePath}"

}
