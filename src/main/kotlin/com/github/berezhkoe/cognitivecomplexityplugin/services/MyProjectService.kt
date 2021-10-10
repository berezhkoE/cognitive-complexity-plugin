package com.github.berezhkoe.cognitivecomplexityplugin.services

import com.intellij.openapi.project.Project
import com.github.berezhkoe.cognitivecomplexityplugin.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
