package libraries.git

import hudson.AbortException

// create PR to Chart repo
void call(){

    // Centralize helm repo
    def chartRepo = "git@github.com:huy-axie/helm-kubernetes-services.git"

    def service = "${JOB_NAME}".split("/").first()

    def env = envDetector("${env.BRANCH_NAME}")

    def new_branch_name = "${service}-to-${env}-${UUID.randomUUID().toString()}"


    // checkout Charts repo 
    stage "Checkout Charts", {

        def images = get_images_to_build()
        
        def imgage = images[0]
            
        sh """
            mkdir -p -m 0600 ~/.ssh
            ssh-keyscan github.com >> ~/.ssh/known_hosts
            git clone ${chartRepo}
        """

        // Replace image tag        
        dir("helm-kubernetes-services"){
            
            sh "git config user.email \"jenkins@axie.com\""
            sh "git config user.name \"Jenkins\""
            sh "git checkout -b deploy/${new_branch_name}"

            // Sed images
            sh """
            sed -i 's/tag:.*/tag: ${images[0].tag}/g'  services/${service}/${env}/values.yaml
            git commit -am 'Deploy ${images[0].tag}'
            git push --set-upstream origin deploy/${new_branch_name}
            """

            // create pull request
            withCredentials([file(credentialsId: 'GITHUB_TOKEN', variable: 'TOKEN')]) {
            def tmpSvc = service.toUpperCase()
            sh """
                    gh auth login --with-token < $TOKEN
                    gh pr create --title "${tmpSvc}" --body "${service}. ${env}" --base main --head "deploy/${new_branch_name}"
                """
            }
        }
    }
}


// quick switch for env
String envDetector(String branch){
    println branch
    switch(branch){
        case "develop": 
            return "develop"
            break
        case "dev":
            return "develop"
            break
        case "master":
            return "production"
            break
        case "main":
            return "production"
            break
        case "staging":
            return "staging"
            break
        default: 
            "No branch match."
    }

}