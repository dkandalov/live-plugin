package liveplugin

import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import liveplugin.implementation.Editors.registerEditorListener
import liveplugin.toolwindow.settingsmenu.languages.AddKotlinLibsAsDependency
import liveplugin.toolwindow.util.DependenciesUtil
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.KotlinPluginUtil
import org.jetbrains.kotlin.idea.caches.resolve.ModuleTestSourceInfo

/**
 * This class makes Kotlin plugin treat LivePlugin script files as if they were part of the currently opened project.
 * The least hacky way seems to be by putting MODULE_INFO into user data of script PSI object.
 * In theory, its value could be any implementation of ModuleInfo interface, however, at the moment all supported classes
 * are hardcoded in org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl.createFacadeForSyntheticFiles.
 */
@Suppress("UNCHECKED_CAST", "DEPRECATION")
fun listenToOpenEditorsAndRegisterKotlinReferenceResolution() {
    registerEditorListener(Disposer.newDisposable(), object: FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
            // Note that ".kt" files are deliberately omitted since only ".kts" files work with the workaround below.
            if (file.extension?.toLowerCase() != "kts" || !FileUtil.startsWith(file.path, LivePluginAppComponent.pluginsRootPath())) return

            val project = source.project
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
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

                    // TODO the workaround below doesn't work, need to simulate complete on empty line or understand what's wrong
                    // Workaround to avoid the following exception:
                    //   java.lang.AssertionError: Resolver for 'completion/highlighting in ScriptModuleInfo()...' does not know how to resolve ModuleProductionSourceInfo(module=...)
                    // There is no particular reason to do this, except that it fixes the exception for some reason (also using auto-complete in the editor fixes the issue).
                    // It's worth understanding at some point what is going on there.
                    __hack__triggerResolveScope(psiFile)

//                    val configuration = CompletionSessionConfiguration(false, false, false, true, false, false)
//                    val psiElement = TODO()
//                    val offset = TODO()
//                    val editor = TODO()
//                    val parameters = CompletionParameters(psiElement, psiFile, CompletionType.BASIC, offset, editor)
//                    BasicCompletionSession(configuration, parameters, mapper, resultSet)

                    psiFile.putUserData(key!!, ModuleTestSourceInfo(module))
                }
            }.execute()
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {}

        override fun selectionChanged(event: FileEditorManagerEvent) {}
    })
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