def call() {
    def changedFiles = sh(script: 'git diff --name-only HEAD^', returnStdout: true).trim()
    def changedDirs = changedFiles.split('\n').collect { file ->
        file.tokenize('/')[0]
    }.unique()
    return changedDirs
} 