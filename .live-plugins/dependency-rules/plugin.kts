import com.intellij.icons.AllIcons.Ide.Gift
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.*
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile

// depends-on-plugin org.jetbrains.kotlin
// depends-on-plugin com.intellij.java.ide

// Use "Validate Dependencies" action to check the dependencies below.

val all = createScope("liveplugin") { it.startsWith("liveplugin.implementation") }
val core = createScope("core") { it.startsWith("liveplugin.implementation.LivePlugin.kt") }
val common = createScope("common") { it.startsWith("liveplugin.implementation.common") }
val actions = createScope("actions") { it.startsWith("liveplugin.implementation.actions") }
val pluginRunner = createScope("pluginrunner") { it.startsWith("liveplugin.implementation.pluginrunner") }

val validationManager = DependencyValidationManager.getInstance(project!!)
fun NamedScope.nothingDependsOnIt() = validationManager.denyUsages(of = this, `in` = all - this)
fun NamedScope.doesNotDependOnAnything() = validationManager.denyUsages(of = all - this, `in` = this)
fun NamedScope.dependsOnlyOn(vararg scopes: NamedScope) = validationManager.denyUsages(of = all - this - scopes.union(), `in` = this)

validationManager.removeAllRules()
actions.nothingDependsOnIt()
pluginRunner.dependsOnlyOn(core, common)
common.doesNotDependOnAnything()


fun DependencyValidationManager.denyUsages(of: NamedScope, `in`: NamedScope) = addRule(DependencyRule(`in`, of, true))
fun DependencyValidationManager.allowUsages(of: NamedScope, onlyIn: NamedScope) = addRule(DependencyRule(onlyIn, of, false))

fun createScope(id: String, f: (String) -> Boolean) =
    NamedScope(id, Gift, object : FilteredPackageSet(id) {
        override fun contains(file: VirtualFile, project: Project) =
            f((file.toPsiFile(project) as? KtFile)?.packageFqName?.asString() + ".${file.name}")
    })

operator fun NamedScope.plus(that: NamedScope) =
    NamedScope("$scopeId + ${that.scopeId}", icon, UnionPackageSet.create(value!!, that.value!!))

operator fun NamedScope.minus(that: NamedScope) =
    NamedScope("$scopeId - ${that.scopeId}", icon, IntersectionPackageSet.create(value!!, ComplementPackageSet(that.value!!)))

fun NamedScope.inverted() =
    NamedScope("!$scopeId", icon, ComplementPackageSet(value!!))

fun NamedScope.intersect(that: NamedScope) =
    NamedScope("$scopeId && ${that.scopeId}", icon, IntersectionPackageSet.create(value!!, that.value!!))

fun Array<out NamedScope>.union() = reduce { a, b -> a + b }
