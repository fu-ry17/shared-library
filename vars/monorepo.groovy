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

    // Wait a bit for jobs to be created
    sleep(5)

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
        println "Found job: ${job?.class?.name} at path: ${fullFolderPath}"
        if (job instanceof jenkins.branch.MultiBranchProject) {
            println "Triggering scan for: ${fullFolderPath}"
            job.scheduleBuild2(0)
            job.compute()
        } else {
            println "Warning: Job not found or not a MultiBranchProject at: ${fullFolderPath}"
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
    
    // Create all intermediate folders first
    def allFolderPaths = []
    jenkinsfiles.each { file ->
        def path = file.path
        if (path == 'Jenkinsfile') {
            return
        }
        
        def parts = path.split('/')
        def currentPath = 'Generated/my-monorepo'
        
        // Add intermediate folders (but not the final component folder)
        for (int i = 0; i < parts.length - 2; i++) {
            currentPath += "/${parts[i]}"
            if (!allFolderPaths.contains(currentPath)) {
                allFolderPaths.add(currentPath)
            }
        }
    }
    
    // Create intermediate folders
    allFolderPaths.sort().each { folderPath ->
        script += """
            folder('${folderPath}') {
                description('Generated folder for ${folderPath.substring(folderPath.lastIndexOf('/') + 1)}')
            }
        """
    }
    
    // Create MultiBranch Pipeline jobs
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
                    println "Configuring job: ${fullFolderPath}"
                    def traits = node / sources / data / 'jenkins.branch.BranchSource' / source / traits
                    traits << 'jenkins.plugins.git.traits.BranchDiscoveryTrait' {
                        strategyId(1)
                    }
                    traits << 'jenkins.plugins.git.traits.TagDiscoveryTrait'()
                    traits << 'jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait' {
                        includes('*')
                        excludes('')
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
                triggers {
                    periodicTrigger {
                        interval('5m')
                    }
                }
            }
        """
    }
    
    return script
} 
