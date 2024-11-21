def call() {
    // Find all Jenkinsfiles in the repository
    def jenkinsfiles = findFiles(glob: '**/Jenkinsfile')
    
    // Generate Job DSL script
    def dslScript = generateJobDsl(jenkinsfiles)
    
    // Execute Job DSL
    jobDsl scriptText: dslScript,
           removedJobAction: 'DELETE',
           removedViewAction: 'DELETE',
           lookupStrategy: 'SEED_JOB'
}

def generateJobDsl(jenkinsfiles) {
    def script = """
        folder('Generated') {
            description('Auto-generated pipelines for monorepo')
        }
        
        folder('Generated/my-monorepo') {
            description('Pipelines for my-monorepo')
        }
    """
    
    jenkinsfiles.each { file ->
        def path = file.path
        if (path == 'Jenkinsfile') {
            return // Skip root Jenkinsfile
        }
        
        def folders = path.split('/')
        folders.pop() // Remove Jenkinsfile name
        
        def folderPath = "Generated/my-monorepo/${folders.join('/')}"
        
        script += """
            folder('${folderPath}') {
                description('Generated folder for ${folders.join('/')}')
            }
            
            multibranchPipelineJob('${folderPath}') {
                branchSources {
                    git {
                        id('${folders.join('-')}-id')
                        remote('${env.GIT_URL}')
                        credentialsId('git-credentials')
                    }
                }
                factory {
                    workflowBranchProjectFactory {
                        scriptPath('${path}')
                    }
                }
                orphanedItemStrategy {
                    discardOldItems {
                        numToKeep(20)
                        daysToKeep(7)
                    }
                }
            }
        """
    }
    
    return script
} 