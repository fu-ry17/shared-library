def call() {
    // Find all Jenkinsfiles in the repository
    def jenkinsfiles = findFiles(glob: '**/Jenkinsfile')
    
    // Generate Job DSL script
    def dslScript = generateJobDsl(jenkinsfiles)
    
    // Execute Job DSL
    jobDsl scriptText: dslScript,
           removedJobAction: 'DELETE',
           removedViewAction: 'DELETE',
           lookupStrategy: 'SEED_JOB',
           ignoreExisting: true

    // Trigger immediate scan for all created jobs
    jenkinsfiles.each { file ->
        def path = file.path
        if (path == 'Jenkinsfile') {
            return
        }
        def folderPath = path.substring(0, path.lastIndexOf('/'))
        def fullFolderPath = "Generated/my-monorepo/${folderPath}"
        
        // Trigger branch indexing using the correct method
        def job = jenkins.model.Jenkins.instance.getItemByFullName(fullFolderPath)
        if (job instanceof jenkins.branch.MultiBranchProject) {
            job.scheduleBuild2(0)
            job.compute()
        }
    }
}

def generateJobDsl(jenkinsfiles) {
    // Create base folders first
    def script = """
        if(jenkins.model.Jenkins.instance.getItem('Generated')) {
            jenkins.model.Jenkins.instance.getItem('Generated').delete()
        }
        
        folder('Generated') {
            description('Auto-generated pipelines for monorepo')
        }
        
        folder('Generated/my-monorepo') {
            description('Pipelines for my-monorepo')
        }
    """
    
    // Collect all unique folder paths
    def allFolderPaths = []
    jenkinsfiles.each { file ->
        def path = file.path
        if (path == 'Jenkinsfile') {
            return
        }
        
        def folderPath = path.substring(0, path.lastIndexOf('/'))
        def parts = folderPath.split('/')
        def currentPath = 'Generated/my-monorepo'
        
        // Add each level of the path
        parts.each { part ->
            currentPath += "/${part}"
            if (!allFolderPaths.contains(currentPath)) {
                allFolderPaths.add(currentPath)
            }
        }
    }
    
    // Create all folders in order
    allFolderPaths.sort().each { folderPath ->
        script += """
            folder('${folderPath}') {
                description('Generated folder for ${folderPath.substring(folderPath.lastIndexOf('/') + 1)}')
            }
        """
    }
    
    // Create jobs
    jenkinsfiles.each { file ->
        def path = file.path
        if (path == 'Jenkinsfile') {
            return
        }
        
        def folderPath = path.substring(0, path.lastIndexOf('/'))
        def fullFolderPath = "Generated/my-monorepo/${folderPath}"
        def folderId = folderPath.replace('/', '-')
        
        script += """
            multibranchPipelineJob('${fullFolderPath}') {
                displayName('${folderPath.split('/').last()}')
                branchSources {
                    git {
                        id('${folderId}-id')
                        remote('${env.GIT_URL}')
                    }
                }
                configure { node ->
                    def traits = node / sources / data / 'jenkins.branch.BranchSource' / source / traits
                    traits << 'jenkins.plugins.git.traits.BranchDiscoveryTrait' {
                        strategyId(1)
                    }
                    traits << 'jenkins.plugins.git.traits.TagDiscoveryTrait'()
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
                configure {
                    def triggers = it / triggers / 'com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger'
                    triggers << {
                        spec('H/5 * * * *')    // Every 5 minutes
                        interval(300000)        // 5 minutes in milliseconds
                    }
                }
            }
        """
    }
    
    return script
} 
