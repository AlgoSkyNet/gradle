/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.tooling.r213
import org.gradle.integtests.tooling.fixture.GradleConnectionToolingApiSpecification
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.connection.GradleConnectionBuilder
import org.gradle.tooling.model.eclipse.EclipseProject
/**
 * Builds a composite with a single project.
 */
class SingleProjectCompositeBuildCrossVersionSpec extends GradleConnectionToolingApiSpecification {
    def "can create composite of a single multi-project build"() {
        given:
        multiProjectBuildInRootFolder("multi-build", ['a', 'b', 'c'])

        when:
        def models = getUnwrappedModelsWithGradleConnection(EclipseProject)

        then:
        models.size() == 4
        rootProjects(models).size() == 1
        containsProjects(models, [':', ':a', ':b', ':c'])
    }

    def "can create composite of a single single-project build"() {
        given:
        singleProjectBuildInRootFolder("single-build")

        when:
        def models = getUnwrappedModelsWithGradleConnection(EclipseProject)

        then:
        models.size() == 1
        rootProjects(models).size() == 1
        containsProjects(models, [':'])
    }

    def "participant is always treated as root of a build"() {
        given:
        multiProjectBuildInRootFolder("bad-parent", ['a', 'b', 'c']) {
            buildFile << """
                allprojects {
                    throw new RuntimeException("Badly configured project")
                }
"""
        }
        def goodChildProject = file("c").createDir()
        goodChildProject.file("build.gradle") <<"""
            apply plugin: 'java'
"""

        when:
        def models = getUnwrappedModelsWithGradleConnection(EclipseProject)

        then:
        models.size() == 1
        EclipseProject project = models.get(0)
        project.gradleProject.parent == null
        project.gradleProject.path == ':'
        project.projectDirectory == goodChildProject
    }

    def "sees changes to composite build when projects are added"() {
        given:
        singleProjectBuildInRootFolder("single-build")
        GradleConnectionBuilder connector = toolingApi.gradleConnectionBuilder()
        connector.forRootDirectory(projectDir)
        def connection = connector.build()

        when:
        def firstRetrieval = unwrap(connection.getModels(EclipseProject))

        then:
        firstRetrieval.size() == 1
        rootProjects(firstRetrieval).size() == 1
        containsProjects(firstRetrieval, [':'])

        when:
        // make project a multi-project build
        settingsFile << "include 'a'"

        and:
        def secondRetrieval = unwrap(connection.getModels(EclipseProject))

        then:
        secondRetrieval.size() == 2
        rootProjects(secondRetrieval).size() == 1
        containsProjects(secondRetrieval, [':', ':a'])

        when:
        // adding more projects to multi-project build
        settingsFile << "include 'b', 'c'"

        and:
        def thirdRetrieval = unwrap(connection.getModels(EclipseProject))

        then:
        thirdRetrieval.size() == 4
        rootProjects(thirdRetrieval).size() == 1
        containsProjects(thirdRetrieval, [':', ':a', ':b', ':c'])

        when:
        // remove the existing project
        projectDir.deleteDir()

        and:
        def fourthRetrieval = unwrap(connection.getModels(EclipseProject))

        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e,
            "Could not fetch models of type 'EclipseProject'",
            "The root project is not yet available for build")

        cleanup:
        connection?.close()
    }

    Iterable<EclipseProject> rootProjects(Iterable<EclipseProject> projects) {
        projects.findAll { it.parent == null }
    }

    void containsProjects(models, projects) {
        def projectsFoundByPath = models.collect { it.gradleProject.path }
        assert projectsFoundByPath.containsAll(projects)
    }
}
