
package libraries.docker

import com.cloudbees.plugins.credentials.CredentialsProvider
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import com.cloudbees.plugins.credentials.Credentials

void call(){
  stage "Pre-pull images", {
    handleException {
      if (config.path_dockerfile != "") {
        sh """
          base_images=`cat $config.path_dockerfile | grep FROM | awk '{print \$2}'`
          for i in \$base_images
          do
            img_id=`docker images -q \$i`
            if [[ -z \$img_id ]]; then
              docker pull \$i
            fi
          done
        """
      }
    } { Exception exception ->
      // throw exception
    }
  }

  stage "Building Docker Image", {

    handleException {
      
      boolean remove_local_image = false
      if (config.remove_local_image){
          if (!(config.remove_local_image instanceof Boolean)){
              error "remove_local_image must be a Boolean, received [${config.remove_local_image.getClass()}]"
          }
          remove_local_image = config.remove_local_image
      }

      login_to_registry{
        def images = get_images_to_build()
        withBuildArgs{ args ->
          images.each{ img ->
            if (config.build_strategy == "multi") {
              //sh "docker build ${img.context} -f ${config.path_dockerfile} -t ${img.registry}/${img.repo}:${img.tag} ${args}" 
              sh "DOCKER_BUILDKIT=1 docker build ${img.context} -f ${config.path_dockerfile} -t ${img.registry}/${img.repo}:${img.tag} ${args} --ssh default" 
            } else {
              sh "docker build ${img.context} -t ${img.registry}/${img.repo}:${img.tag} ${args}"
            }
            sh "docker push ${img.registry}/${img.repo}:${img.tag}"
            if (remove_local_image) sh "docker rmi -f ${img.registry}/${img.repo}:${img.tag} 2> /dev/null"
          }
        }
        
      }
     } { Exception exception ->
        // throw exception
    }
  }
}

void withBuildArgs(Closure body){

  ArrayList buildArgs = []
  def creds = []
  
  config.build_args.each{ argument, value ->
    if(value instanceof Map){
      switch(value?.type){
        case "credential": // validate credential exists and is a secrettext cred
          def allCreds = CredentialsProvider.lookupCredentials(Credentials, Jenkins.get(),null, null)
          def cred = allCreds.find{ it.id.equals(value.id) } 
          if(cred == null){
              error "docker library: build argument '${argument}' specified credential id '${value.id}' which does not exist."
          }
          if(!(cred instanceof StringCredentialsImpl)){
            error "docker library: build argument '${argument}' credential must be a Secret Text." 
          }
          creds << string(credentialsId: value.id, variable: argument)
          buildArgs << "--build-arg ${argument}=\$${argument}"
          break; 
        case null: // no build argument type provided 
          error "docker library: build argument '${argument}' must specify a type"
          break;
        default: // unrecognized argument type 
          error "docker library: build argument '${argument}' type of '${value.type}' is not recognized"  
      }
    } else {
      buildArgs << "--build-arg ${argument}='${value}'"
    }
  }

  withCredentials(creds){
    body(buildArgs.join(" "))
  }

}

/*
 * @Validate steps run prior to template execution 
 * if the build_args configuration is present:
 * 1. it needs to be a map 
 * 2. if there are build args from credentials, make sure those credentials exist 
*/
@Validate
void validate_docker_build(){

  if(!config.containsKey("build_args")){
    return 
  }

  if(!(config.build_args instanceof Map)){
    error "docker library 'build_args' is a ${config.build_args.getClass()} when a block was expected"
  }

  config.build_args.each{ argument, value ->
    if(value instanceof Map){
      switch(value?.type){
        case "credential": // validate credential exists and is a secrettext cred
          def allCreds = CredentialsProvider.lookupCredentials(Credentials, Jenkins.get(),null, null)
          def cred = allCreds.find{ it.id.equals(value.id) } 
          if(cred == null){
              error "docker library: build argument '${argument}' specified credential id '${value.id}' which does not exist."
          }
          if(!(cred instanceof StringCredentialsImpl)){
            error "docker library: build argument '${argument}' credential must be a Secret Text." 
          }
          break; 
        case null: // no build argument type provided 
          error "docker library: build argument '${argument}' must specify a type"
          break;
        default: // unrecognized argument type 
          error "docker library: build argument '${argument}' type of '${value.type}' is not recognized"  
      }
    }
  }
}