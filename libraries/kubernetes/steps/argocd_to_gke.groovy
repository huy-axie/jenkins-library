package libraries.kubernetes

void call(app_env) {

  stage "Deploy to ${app_env.cluster_name}", {

      def argocd_server = app_env.argocd_server ?:
                      config.argocd_server ?:
                      {error "k8s cluster not defined in library config or application environment config"}()

      def namespace  = app_env.namespace ?:
              {error "Application namespace"}()

      def cluster_name = app_env.cluster_name ?:
                      config.cluster_name ?:
                      {error "k8s cluster not defined in library config or application environment config"}()

      def release_name = app_env.release_name ?:
                  "${JOB_NAME}" ?:
                  {error "App Env Must Specify release_name"}()

      def values = app_env.values ?:
                    "[]" ?:
                    {error "App environments values"}()
  
      def argocd_project = app_env.argocd_project ?:
                    "[]" ?:
                    {error "App environments argocd_project"}()

      def images = get_images_to_build()
      withCredentials([string(credentialsId: "jenkins-argocd-deploy", variable: 'ARGOCD_AUTH_TOKEN')]) {

        // Check app exists, if not create one
        if (!isAppExists("$argocd_server","$release_name")){
          sh """
              ARGOCD_SERVER=${argocd_server} argocd  --grpc-web --insecure\
              app create ${release_name} \
              -p applicationName=${release_name} \
              --repo https://charts.skymavis.one \
              --helm-chart k8s-service \
              --revision 0.1.3 \
              --dest-namespace ${namespace} \
              --dest-server ${cluster_name} \
              --project ${argocd_project}
          """
          sh """
            ARGOCD_SERVER=${argocd_server} argocd  --grpc-web --insecure\
            app set ${release_name} \
            --values=values.yaml
          """
        }

        // write file to apply values
        writeFile(file: "${BUILD_NUMBER}-values.yaml", text: values, encoding: "UTF-8")
        images.each { img ->
          // ArgoCD to GKE
          sh """ARGOCD_SERVER=${argocd_server} argocd --grpc-web \
              --insecure \
              app set ${release_name} \
              -p containerImage.repository=${img.registry}/${img.repo} \
              -p containerImage.tag=${img.tag}
            """

          // Argocd additional values
          sh """ARGOCD_SERVER=${argocd_server} argocd --grpc-web \
              --insecure \
              app set ${release_name} \
              --values-literal-file=$BUILD_NUMBER-values.yaml
            """
          // Sync ArgoCD with running manifest
          sh """
            ARGOCD_SERVER=${argocd_server} argocd --grpc-web --insecure app sync ${release_name} --force
            ARGOCD_SERVER=${argocd_server} argocd --grpc-web --insecure app wait ${release_name} --timeout 600    
            """
        }
        // Finally delete file
        sh """
          rm -rf $BUILD_NUMBER-values.yaml
        """
      }
    }
}

// verify app exists
boolean isAppExists(String argocd_server, app_name){
   res = sh (
    script: """
              ARGOCD_SERVER=${argocd_server} argocd --grpc-web --insecure app list | grep -E -o ${app_name}
              """,
    returnStatus: true)

    if (res !=0 ){
      return false
    }
    return true
}
