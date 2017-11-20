/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.xctest.plugins

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.swift.plugins.SwiftApplicationPlugin
import org.gradle.language.swift.plugins.SwiftLibraryPlugin
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkMachOBundle
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite
import org.gradle.nativeplatform.test.xctest.tasks.InstallXCTestBundle
import org.gradle.nativeplatform.test.xctest.tasks.XcTest
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

class XCTestConventionPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testApp").build()

    def "adds extension with convention for source layout and module name"() {
        given:
        def src = projectDir.file("src/test/swift/test.swift").createFile()

        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        project.xctest instanceof SwiftXCTestSuite
        project.xctest.module.get() == "TestAppTest"
        project.xctest.swiftSource.files == [src] as Set
    }

    def "sets tested component to main component when applying Swift library plugin"() {
        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        project.xctest.testedComponent.orNull == null

        when:
        project.pluginManager.apply(SwiftLibraryPlugin)

        then:
        project.xctest.testedComponent.orNull == project.library
    }

    def "sets tested component to Swift application when applying swift application plugin"() {
        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        project.xctest.testedComponent.orNull == null

        when:
        project.pluginManager.apply(SwiftApplicationPlugin)

        then:
        project.xctest.testedComponent.orNull == project.executable
    }

    def "registers a component for the test suite"() {
        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        project.components.test == project.xctest
        project.components.testExecutable == project.xctest.developmentBinary
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "adds compile, link and install tasks on macOS"() {
        given:
        def src = projectDir.file("src/test/swift/test.swift").createFile()

        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        def compileSwift = project.tasks.compileTestSwift
        compileSwift instanceof SwiftCompile
        compileSwift.source.files == [src] as Set
        compileSwift.objectFileDir.get().asFile == projectDir.file("build/obj/test")
        compileSwift.debuggable
        !compileSwift.optimized

        def link = project.tasks.linkTest
        link instanceof LinkMachOBundle
        link.binaryFile.get().asFile == projectDir.file("build/exe/test/" + OperatingSystem.current().getExecutableName("TestAppTest"))
        link.debuggable

        def install = project.tasks.installTest
        install instanceof InstallXCTestBundle
        install.installDirectory.get().asFile == project.file("build/install/test")
        install.runScriptFile.get().asFile.name == OperatingSystem.current().getScriptName("TestAppTest")

        def test = project.tasks.xcTest
        test instanceof XcTest
        test.workingDirectory.get().asFile == projectDir.file("build/install/test")
    }

    @Requires(TestPrecondition.NOT_MAC_OS_X)
    def "adds compile, link and install tasks"() {
        given:
        def src = projectDir.file("src/test/swift/test.swift").createFile()

        when:
        project.pluginManager.apply(XCTestConventionPlugin)

        then:
        def compileSwift = project.tasks.compileTestSwift
        compileSwift instanceof SwiftCompile
        compileSwift.source.files == [src] as Set
        compileSwift.objectFileDir.get().asFile == projectDir.file("build/obj/test")
        compileSwift.debuggable
        !compileSwift.optimized

        def link = project.tasks.linkTest
        link instanceof LinkExecutable
        link.binaryFile.get().asFile == projectDir.file("build/exe/test/" + OperatingSystem.current().getExecutableName("TestAppTest"))
        link.debuggable

        def install = project.tasks.installTest
        install instanceof InstallExecutable
        install.installDirectory.get().asFile == project.file("build/install/test")
        install.runScriptFile.get().asFile.name == OperatingSystem.current().getScriptName("TestAppTest")

        def test = project.tasks.xcTest
        test instanceof XcTest
        test.workingDirectory.get().asFile == projectDir.file("build/install/test")
    }

    def "output locations reflects changes to buildDir"() {
        when:
        project.pluginManager.apply(XCTestConventionPlugin)
        project.buildDir = project.file("output")

        then:
        def compileSwift = project.tasks.compileTestSwift
        compileSwift.objectFileDir.get().asFile == projectDir.file("output/obj/test")

        def link = project.tasks.linkTest
        link.binaryFile.get().asFile == projectDir.file("output/exe/test/" + OperatingSystem.current().getExecutableName("TestAppTest"))

        def install = project.tasks.installTest
        install.installDirectory.get().asFile == project.file("output/install/test")
        install.runScriptFile.get().asFile.name == OperatingSystem.current().getScriptName("TestAppTest")

        def test = project.tasks.xcTest
        test.workingDirectory.get().asFile == projectDir.file("output/install/test")
    }
}
