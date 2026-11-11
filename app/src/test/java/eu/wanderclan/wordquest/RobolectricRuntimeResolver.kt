package eu.wanderclan.wordquest

import org.robolectric.internal.dependency.DependencyJar
import org.robolectric.internal.dependency.DependencyResolver
import java.io.File
import java.net.URL

/**
 * Robolectric's offline mode, with a failure that says what to fix: Gradle copies the Android runtimes
 * listed in `robolectricRuntimes` (app/build.gradle.kts) into `robolectric.dependency.dir`, and a runtime
 * that is not there is never downloaded. Registered in META-INF/services; it outranks Robolectric's own
 * resolver, which has the lowest priority.
 */
class RobolectricRuntimeResolver : DependencyResolver {
    override fun getLocalArtifactUrl(dependency: DependencyJar): URL {
        val dir = requireNotNull(System.getProperty("robolectric.dependency.dir")) {
            "Run through Gradle: the build passes robolectric.dependency.dir"
        }
        val jar = File(dir, "${dependency.artifactId}-${dependency.version}.${dependency.type}")
        check(jar.isFile) {
            "Robolectric needs ${dependency.groupId}:${dependency.artifactId}:${dependency.version}, which is not in $dir. " +
                "Add \"${dependency.groupId}:${dependency.artifactId}:${dependency.version}\" to robolectricRuntimes " +
                "in app/build.gradle.kts; the tests never download it themselves."
        }
        return jar.toURI().toURL()
    }
}
