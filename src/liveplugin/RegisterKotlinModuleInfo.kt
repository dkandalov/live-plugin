package liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import liveplugin.toolwindow.settingsmenu.languages.AddKotlinLibsAsDependency
import liveplugin.toolwindow.util.DependenciesUtil
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.KotlinPluginUtil
import org.jetbrains.kotlin.idea.caches.resolve.ModuleTestSourceInfo
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class makes Kotlin plugin treat LivePlugin script files as if they were part of the currently opened project.
 * The least hacky way seems to be by putting ModuleInfo into user data of script PSI file
 * so that it's picked up by `org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl.getFacadeToAnalyzeFiles`.
 *
 * In theory, it could be any implementation of `ModuleInfo` interface, however, at the moment all supported classes
 * are hardcoded in `org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl.createFacadeForSyntheticFiles`.
 *
 * It is also important to put `ModuleInfo` into PSI file before kotlin annotator processes the file.
 * Otherwise, `ResolverForProject` is cached (including list of modules it uses for resolution)
 * and AnalyzerFacade fails to use `ModuleInfo` added later (see `org.jetbrains.kotlin.analyzer.ResolverForProjectImpl.descriptorForModule`).
 */
fun listenToOpenEditorsAndRegisterKotlinReferenceResolution() {

    registerProjectListener(ApplicationManager.getApplication(), object: ProjectManagerListener {
        override fun projectOpened(project: Project) {
            val documentManager = PsiDocumentManager.getInstance(project)

            val listener = object: PsiDocumentManager.Listener {
                override fun fileCreated(psiFile: PsiFile, document: Document) {
                    initKotlinSyntaxHighlighting(psiFile, project)
                }
                override fun documentCreated(document: Document, psiFile: PsiFile?) {
                    initKotlinSyntaxHighlighting(psiFile ?: return, project)
                }
            }

            documentManager.addListener(listener)
            project.whenDisposed {
                documentManager.removeListener(listener)
            }
        }
    })
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
private fun initKotlinSyntaxHighlighting(psiFile: PsiFile, project: Project) {
    val file = psiFile.virtualFile ?: return
    if (file.extension?.toLowerCase() != "kts" || !FileUtil.startsWith(file.path, LivePluginAppComponent.pluginsRootPath())) return
    val module = DependenciesUtil.findModuleWithLibrary(project, AddKotlinLibsAsDependency.LIBRARY_NAME) ?: return

    object: WriteAction<Any>() {
        override fun run(result: Result<Any>) {
            if (key == null) {
                // Attempt to force initialisation of MODULE_INFO key. It's currently loaded in:
                //  var PsiFile.moduleInfo: ModuleInfo? by UserDataProperty(Key.create("MODULE_INFO"))
                // which is not guaranteed to run before this code.
                __hack__triggerResolveScope(psiFile)
                key = Key.findKeyByName("MODULE_INFO") as Key<ModuleInfo>?
            }
            if (key == null) return

            psiFile.putUserData(key!!, ModuleTestSourceInfo(module))
        }
    }.execute()
}

private var key: Key<ModuleInfo>? = null

private fun __hack__triggerResolveScope(psiFile: PsiFile) {
    val ktFile = "org.jetbrains.kotlin.psi.KtFile".loadClass()?.cast(psiFile) ?: return
    val scopeUtilsKtClass = "org.jetbrains.kotlin.idea.caches.resolve.ScopeUtilsKt".loadClass() ?: return
    val getResolveScope = scopeUtilsKtClass.declaredMethods.toList().find { it.name == "getResolveScope" } ?: return
    getResolveScope.invoke(null, ktFile)
}

/**
 * Use reflection and KotlinPluginUtil classloader because both kotlin-plugin.jar and kotlin-compiler-embeddable.jar
 * contain common classes (e.g. KtFile) which are namespaced by classloader.
 * Because LivePlugin loads kotlin-compiler-embeddable.jar, it'll by default use the wrong class.
 */
private fun String.loadClass() = KotlinPluginUtil::class.java.classLoader.loadClass(this)

private fun registerProjectListener(disposable: Disposable, listener: ProjectManagerListener) {
    val connection = ApplicationManager.getApplication().messageBus.connect(disposable)
    connection.subscribe(ProjectManager.TOPIC, listener)
}

private fun Disposable.whenDisposed(callback: () -> Any) = newDisposable(listOf(this), callback)

private fun newDisposable(parents: Collection<Disposable>, callback: () -> Any = {}): Disposable {
    val isDisposed = AtomicBoolean(false)
    val disposable = Disposable {
        if (!isDisposed.get()) {
            isDisposed.set(true)
            callback()
        }
    }
    parents.forEach { parent ->
        // can't use here "Disposer.register(parent, disposable)"
        // because Disposer only allows one parent to one child registration of Disposable objects
        Disposer.register(parent, Disposable {
            Disposer.dispose(disposable)
        })
    }
    return disposable
}
