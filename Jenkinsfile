node {
    stage("checkout") {
        checkout([
                $class                           : 'GitSCM',
                branches                         : [[name: '**']],
                doGenerateSubmoduleConfigurations: false,
                extensions                       : [],
                submoduleCfg                     : [],
                userRemoteConfigs                : [
                        [credentialsId: 'se3-gitlab',
                         url          : 'http://212.129.149.40/171250558_teamnamecannotbeempty/backend-webtest.git']
                ]
        ])
    }

    stage("mvn") {
        sh 'sh ./mvnw clean package -Dmaven.test.skip=true'
    }
    def image
    stage("docker-build") {
        sh "docker build -f Dockerfile -t se3app:latest ."
    }
    stage("restart") {
        try {
            sh 'docker rm -f se3'
        } catch(ignored){
            echo('Container\'s not running')
        }
        sh " docker run -d -p 9090:9090 -v /etc/localtime:/etc/localtime --link se3mysql:se3mysql --name se3 se3app:latest"
    }
}