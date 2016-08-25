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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import spock.lang.Unroll

/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 *
 * TODO:
 * - Check exception when build does not exist
 * - Check exception when task does not exist for build
 */
class CompositeBuildTaskDelegationIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    task logProject << {
                        println "Executing build '" + project.rootProject.name + "' project '" + project.path + "' task '" + path + "'"
                    }
                }
"""
        }
        includedBuilds << buildB
    }

    def "can delegate to task in root project of included build"() {
        when:
        buildA.buildFile << """
    task delegate(type: org.gradle.composite.internal.CompositeBuildTaskDelegate) {
        build = 'buildB'
        task = ':logProject'
    }
"""

        execute(buildA, ":delegate")

        then:
        executed ":buildB:logProject"
        output.contains("Executing build 'buildB' project ':' task ':logProject'")
    }

    def "can depend on task in root project of included build"() {
        when:
        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildB').task(':logProject')
    }
"""

        execute(buildA, ":delegate")

        then:
        executed ":buildB:logProject"
        output.contains("Executing build 'buildB' project ':' task ':logProject'")
    }

    def "can delegate to task in subproject of included build"() {
        when:
        buildA.buildFile << """
    task delegate(type: org.gradle.composite.internal.CompositeBuildTaskDelegate) {
        build = 'buildB'
        task = ':b1:logProject'
    }
"""

        execute(buildA, ":delegate")

        then:
        executed ":buildB:b1:logProject"
        output.contains("Executing build 'buildB' project ':b1' task ':b1:logProject'")
    }

    def "can depend on task in subproject of included build"() {
        when:
        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildB').task(':b1:logProject')
    }
"""

        execute(buildA, ":delegate")

        then:
        executed ":buildB:b1:logProject"
        output.contains("Executing build 'buildB' project ':b1' task ':b1:logProject'")
    }

    def "can depend on multiple tasks of included build"() {
        when:
        buildA.buildFile << """
    def buildB = gradle.includedBuild('buildB')
    task delegate {
        dependsOn 'delegate1', 'delegate2'
    }

    task delegate1 {
        dependsOn buildB.task(':logProject')
        dependsOn buildB.task(':b1:logProject')
    }

    task delegate2 {
        dependsOn buildB.task(':logProject')
    }
"""

        execute(buildA, ":delegate")

        then:
        executed ":buildB:logProject", ":buildB:b1:logProject"
        output.contains("Executing build 'buildB' project ':' task ':logProject'")
        output.contains("Executing build 'buildB' project ':b1' task ':b1:logProject'")
    }

    def "can depend on task with name in all included builds"() {
        when:
        BuildTestFile buildC = singleProjectBuild("buildC") {
            buildFile.text = buildB.buildFile.text
        }
        includedBuilds << buildC

        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuilds*.task(':logProject')
    }
"""

        execute(buildA, ":delegate")

        then:
        executed ":buildB:logProject", ":buildC:logProject"
        output.contains("Executing build 'buildB' project ':' task ':logProject'")
        output.contains("Executing build 'buildC' project ':' task ':logProject'")
    }

    @Unroll
    def "included build cannot reference tasks in #scenario"() {
        when:
        BuildTestFile buildC = singleProjectBuild("buildC") {
            buildFile << """
    task illegal {
        dependsOn gradle.includedBuild('$buildName').task(':logProject')
    }
"""
        }
        includedBuilds << buildC

        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildC').task(':illegal')
    }
    task logProject {}
"""

        then:
        fails(buildA, ":delegate")

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'buildC'.")
        failure.assertHasCause("Build is not a composite: it has no included builds.")

        where:
        scenario | buildName
        "sibling" | "buildB"
        "parent" | "buildA"
    }
}
