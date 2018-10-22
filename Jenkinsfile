#!/usr/bin/groovy

pipeline {
    agent any

    tools {
        maven 'maven-3.3.9'
        jdk 'jdk8'
    }

    stages {
        stage ('initialize') {
            steps {
                sh '''
                    echo Log result of the original merge
                    git log --max-count=16 --oneline
                    echo

                    echo Checkout target branch and pull from origin
                    git checkout -f ${ghprbTargetBranch}
                    git fetch origin ${ghprbTargetBranch}
                    git reset --hard FETCH_HEAD
                    echo Log target branch at origin
                    git log --max-count=16 --oneline
                    echo

                    echo Rebase actual commit on target branch
                    BRANCH_PREFIX=buildBranch
                    BRANCH_NAME=${BRANCH_PREFIX}#${BUILD_NUMBER}
                    git checkout -b ${BRANCH_NAME} ${ghprbActualCommit}
                    rm -rf .git/rebase-apply
                    git rebase ${ghprbTargetBranch}
                    echo Log source branch after rebase
                    git log --max-count=16 --oneline
                    echo

                    echo Fast forward the target branch
                    git checkout ${ghprbTargetBranch}
                    git merge --ff-only ${BRANCH_NAME}
                    git branch | grep $BRANC{H_PREFIX | xargs git branch -D
                    echo Log target branch after rebase
                    git log --max-count=16 --oneline
                    git status
                    echo
                '''

                sh '''
                    # kill remaining docker containers
                    for container in $(docker -H localhost:2375 ps -aq); do docker -H localhost:2375 rm -fv ${container}; done

                    # custom settings.xml as the default contains dockerhub config that we do not want
                    cat > /tmp/settings.xml << EOF
                    <?xml version="1.0"?>
                    <settings>
                    </settings>
                    EOF
                '''
            }
        }

        stage ('build') {
            steps {
                sh 'mvn -V -B -U clean install -Dts.all -s /tmp/settings.xml'
            }
            post {
                success {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage ('merge') {
            steps {
                boolean mergeable = true

                try {
                    def pullRequest = new JsonSlurper().parse(new URL("https://api.github.com/repos/${env.ghprbGhRepository}/pulls/${env.ghprbPullId}"))
                    if (pullRequest['labels'] != null) {
                        pullRequest.labels.each { label ->
                            if (label.name.equals('Do Not Merge')) {
                                mergeable = false
                                return
                            }
                        }
                    }
                } catch (Exception e) {
                    echo 'Failed to retrieve PR from GitHub API'
                    e.printStackTrace()
                }

                if (mergeable) {
                    echo "Merging pull request #${env.ghprbPullId} to origin/${env.ghprbTargetBranch}"
                    sshagent(['creds']) {
                        sh 'git push origin ${ghprbTargetBranch}'
                    }
                } else {
                    echo "Not merging pull request #${env.ghprbPullId} because 'Do Not Merge' label is set"
                }
            }
        }
    }
}

