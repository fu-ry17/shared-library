#!/usr/bin/env groovy

def call() {
    // Get component name from the Jenkinsfile path
    def path = env.JOB_NAME.tokenize('/') as String[]
    def component = path[-2] // Gets the last folder name before the branch name
    
    pipeline {
        agent any
        
        stages {
            stage('Build') {
                steps {
                    echo "Building ${component} component..."
                    // Add your common build steps here
                }
            }
            
            stage('Test') {
                steps {
                    echo "Testing ${component} component..."
                    // Add your common test steps here
                }
            }

             stage('Hello Amigos') {
                steps {
                    echo "Jenkins shared libs works well"
                    // Add your common test steps here
                }
            }

            stage('Check Changes') {
                steps {
                    script {
                        def changedDirs = getChangedDirs()
                        echo "Changed directories: ${changedDirs}"
                        
                        // Only proceed if changes affect this component
                        if (changedDirs.contains(component)) {
                            echo "Changes detected in ${component} component"
                        } else {
                            echo "No changes in ${component} component - skipping further stages"
                            currentBuild.result = 'SUCCESS'
                            return
                        }
                    }
                }
            }
        }

        post {
            success {
                echo "${component} pipeline completed successfully"
            }
            failure {
                echo "${component} pipeline failed"
            }
        }
    }
} 
