#!/usr/bin/env groovy
// CLEANUP REQUIRED
pipeline { 
    agent {
        node {
            label "docker-${OS_VERSION}-node"
        }
    }

    options { 
        skipDefaultCheckout()
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        ansiColor('xterm')  
    }

    parameters {  
	    string(name: 'MOTR_REPO', defaultValue: 'https://github.com/Seagate/cortx-motr', description: 'Repo for Motr')
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch for Motr')
        }

    environment {

        // Motr Repo Info

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        MOTR_URL = "${ghprbGhRepository != null ? GPR_REPO : MOTR_URL}"
        MOTR_BRANCH = "${sha1 != null ? sha1 : MOTR_BRANCH}"

        MOTR_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        MOTR_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        MOTR_PR_REFSEPEC = "${ghprbPullId != null ? MOTR_GPR_REFSEPEC : MOTR_BRANCH_REFSEPEC}"

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////
        // OS_VERSION and COMPONENTS_BRANCH are manually created parameters in jenkins job.
        
        COMPONENT_NAME = "motr".trim()
        BRANCH = "${ghprbTargetBranch != null ? ghprbTargetBranch : COMPONENTS_BRANCH}"
        HARE_BRANCH = "main"
        HARE_REPO = "https://github.com/Seagate/cortx-hare"
    }
    stages {

        // Build motr fromm PR source code
        stage('Chekout') {
            steps {
				script { build_stage = env.STAGE_NAME }
                script { manager.addHtmlBadge("&emsp;<b>Target Branch : ${BRANCH}</b>&emsp;<br />") }

                 sh """
                    set +x
                    echo "--------------BUILD PARAMETERS -------------------"
                    echo "MOTR_REPO              = ${MOTR_REPO}"
                    echo "MOTR_BRANCH           = ${MOTR_BRANCH}"
                    echo "MOTR_PR_REFSEPEC       = ${MOTR_PR_REFSEPEC}"
                    echo "-----------------------------------------------------------"
                """
                 
                dir("motr") {

                    checkout([$class: 'GitSCM', branches: [[name: "${MOTR_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${MOTR_REPO}",  name: 'origin', refspec: "${MOTR_PR_REFSEPEC}"]]])

                }
                dir ('hare') {

                    checkout([$class: 'GitSCM', branches: [[name: "*/${HARE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false,  timeout: 5], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${HARE_REPO}"]]])

                }
            }
        }

        // Run DTM-Integration-Test
        stage ("DTM-Integration Test") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def buildCortxAllImage = build job: '/Motr/DTM-Integration-Test', wait: true,
                            parameters: [
                                string(name: 'MOTR_REPO', value: "${MOTR_REPO}"),
                                string(name: 'MOTR_BRANCH', value: "${MOTR_BRANCH}"),
                                string(name: 'HARE_BRANCH', value: "${HARE_BRANCH}"),
                                string(name: 'HARE_REPO', value: "${HARE_REPO}")
                            ]
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "DTM-Integration-Test failed"
                    }
                }
            }
        }
    }
}