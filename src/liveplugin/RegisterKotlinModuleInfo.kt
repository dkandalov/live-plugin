package liveplugin

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.ModuleScopeProviderImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.NotNullFunction
import com.intellij.util.Processor
import com.intellij.util.messages.MessageBus
import liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH
import liveplugin.MyFileUtil.fileNamesMatching
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.KotlinPluginUtil
import org.jetbrains.kotlin.idea.caches.resolve.ModuleTestSourceInfo
import org.picocontainer.PicoContainer
import java.util.*
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

    // TODO module will be null if "LivePlugin" module is not in the project; if module added after file is open initKotlinSyntaxHighlighting() is not called again
//    val module = findModuleWithLibrary(project, LIVE_PLUGIN_LIBRARY) ?: return

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

            val module = AModule(project)

            val jars = {
                val virtualFileManager = VirtualFileManager.getInstance()
                val kotlinJars = fileNamesMatching("kotlin.*jar", LIVEPLUGIN_LIBS_PATH).map {
                    virtualFileManager.refreshAndFindFileByUrl("file://$LIVEPLUGIN_LIBS_PATH/$it")!!
                }
                val livePluginJar = virtualFileManager.refreshAndFindFileByUrl("file://$LIVEPLUGIN_LIBS_PATH/LivePlugin.jar")!!
                kotlinJars + livePluginJar
            }()

            val orderEntries = listOf(
                ALibraryOrderEntry(module, "libs", jars),
                object: ModuleOrderEntry {
                    override fun getFiles(type: OrderRootType?): Array<VirtualFile> = emptyArray()
                    override fun getPresentableName() = ""
                    override fun getUrls(rootType: OrderRootType?): Array<String> = emptyArray()
                    override fun setExported(value: Boolean) { }
                    override fun compareTo(other: OrderEntry?): Int = 0
                    override fun getModule() = module
                    override fun getOwnerModule() = module
                    override fun isExported() = false
                    override fun getModuleName() = module.name
                    override fun <R: Any?> accept(policy: RootPolicy<R>?, initialValue: R?): R { error("") }
                    override fun isSynthetic() = false
                    override fun getScope(): DependencyScope { error("") }
                    override fun isValid() = true
                    override fun setScope(scope: DependencyScope) { error("") }
                }
            )
            val orderEnumerator = AnOrderEnumerator(orderEntries)
            module.rootManager = AModuleRootManager(module, orderEnumerator)

//            module.picoContainer.registerComponent(newComponentConfigComponentAdapter(
//                ModuleRootManager::class.java,
//                AModuleRootManager::class.java,
//                null,
//                false
//            ))
//            ModuleRootManager.getInstance(module)

            val testSourceInfo = ModuleTestSourceInfo(module)

            psiFile.putUserData(key!!, testSourceInfo)
        }

//        private fun newComponentConfigComponentAdapter(java: Class<ModuleRootManager>, java1: Class<AModuleRootManager>): ComponentAdapter {
//            error("")
//        }

    }.execute()
}

class ALibraryOrderEntry(val module: Module, val name: String, val files: List<VirtualFile>): LibraryOrderEntry {
    override fun getPresentableName() = name
    override fun getOwnerModule() = module
    override fun getFiles(type: OrderRootType?) = files.toTypedArray()
    override fun getUrls(rootType: OrderRootType?) = files.map { it.url }.toTypedArray()
    override fun <R: Any?> accept(policy: RootPolicy<R>?, initialValue: R?): R { error("") }
    override fun compareTo(other: OrderEntry?) = 0
    override fun isValid() = true
    override fun isSynthetic() = false

    override fun getLibraryLevel() = ""
    override fun getLibraryName() = name
    override fun getRootFiles(type: OrderRootType) = getFiles(type)
    override fun setExported(value: Boolean) {}
    override fun isExported() = false
    override fun getLibrary() = null
    override fun isModuleLevel() = true
    override fun getRootUrls(type: OrderRootType) = getUrls(type)
    override fun getScope(): DependencyScope { error("") }
    override fun setScope(scope: DependencyScope) { error("") }
}

class AnOrderEnumerator(val entries: List<OrderEntry>): OrderEnumerator() {
    override fun forEach(processor: Processor<OrderEntry>) { entries.forEach { processor.process(it) } } 
    override fun withoutSdk() = this 
    override fun satisfying(condition: Condition<OrderEntry>?) = this 
    override fun forEachLibrary(processor: Processor<Library>) { error("") } 
    override fun productionOnly() = this 
    override fun using(provider: RootModelProvider) = this 
    override fun compileOnly() = this 
    override fun roots(rootType: OrderRootType): OrderRootsEnumerator { error("") } 
    override fun roots(rootTypeProvider: NotNullFunction<OrderEntry, OrderRootType>): OrderRootsEnumerator { error("") } 
    override fun sources(): OrderRootsEnumerator { error("") } 
    override fun forEachModule(processor: Processor<Module>) { error("") } 
    override fun shouldRecurse(entry: ModuleOrderEntry, handlers: MutableList<OrderEnumerationHandler>): Boolean { error("") } 
    override fun classes(): OrderRootsEnumerator { error("") } 
    override fun withoutDepModules() = this 
    override fun withoutModuleSourceEntries() = this 
    override fun exportedOnly() = this 
    override fun runtimeOnly() = this 
    override fun withoutLibraries() = this 
    override fun <R: Any?> process(policy: RootPolicy<R>, initialValue: R): R { error("") } 
    override fun recursively() = this 
}

class AModuleRootManager(private val module: AModule, private val orderEnumerator: AnOrderEnumerator): ModuleRootManager() {
    override fun orderEntries() = orderEnumerator
    override fun getExcludeRoots(): Array<VirtualFile> = emptyArray()
    override fun getExternalSource(): ProjectModelExternalSource? = null
    override fun <T: Any?> getModuleExtension(klass: Class<T>?): T = null as T
    override fun getDependencyModuleNames(): Array<String> = arrayOf(module.name)
    override fun getModifiableModel(): ModifiableRootModel { error("") } 
    override fun getModule() = module
    override fun isSdkInherited() = false
    override fun getOrderEntries(): Array<OrderEntry> = orderEnumerator.entries.toTypedArray()
    override fun getSourceRootUrls(): Array<String> = emptyArray()
    override fun getSourceRootUrls(includingTests: Boolean): Array<String> = emptyArray()
    override fun getContentEntries(): Array<ContentEntry> = arrayOf(AContentEntry())
    override fun getExcludeRootUrls(): Array<String> = emptyArray()
    override fun <R: Any?> processOrder(policy: RootPolicy<R>?, initialValue: R): R = null as R
    override fun getFileIndex() = AModuleFileIndex()
    override fun getSdk(): Sdk? = null
    override fun getDependencies(): Array<Module> = arrayOf(module)
    override fun getDependencies(includeTests: Boolean): Array<Module> = arrayOf(module)
    override fun getSourceRoots(): Array<VirtualFile> = emptyArray()
    override fun getSourceRoots(includingTests: Boolean): Array<VirtualFile> = emptyArray()
    override fun getSourceRoots(rootType: JpsModuleSourceRootType<*>): MutableList<VirtualFile> = ArrayList()
    override fun getSourceRoots(rootTypes: MutableSet<out JpsModuleSourceRootType<*>>): MutableList<VirtualFile> = ArrayList()
    override fun isDependsOn(module: Module?) = module == this.module
    override fun getContentRoots(): Array<VirtualFile> = emptyArray()
    override fun getContentRootUrls(): Array<String> = emptyArray()
    override fun getModuleDependencies(): Array<Module> = arrayOf(module)
    override fun getModuleDependencies(includeTests: Boolean): Array<Module> = arrayOf(module)
}

class AContentEntry: ContentEntry {
    override fun getSourceFolders() = arrayOf(ASourceFolder())
    override fun getSourceFolders(rootType: JpsModuleSourceRootType<*>) = mutableListOf(ASourceFolder())
    override fun getSourceFolders(rootTypes: MutableSet<out JpsModuleSourceRootType<*>>) = mutableListOf(ASourceFolder())

    override fun setExcludePatterns(patterns: MutableList<String>) { } 
    override fun getUrl(): String = "" 
    override fun getExcludeFolders(): Array<ExcludeFolder> = emptyArray() 
    override fun addExcludePattern(pattern: String) {  } 
    override fun getFile(): VirtualFile? = null 
    override fun removeExcludeFolder(excludeFolder: ExcludeFolder) { } 
    override fun removeExcludeFolder(url: String): Boolean = false 
    override fun getExcludeFolderFiles(): Array<VirtualFile> = emptyArray() 
    override fun getSourceFolderFiles(): Array<VirtualFile> = emptyArray()
    override fun clearExcludeFolders() { } 
    override fun removeExcludePattern(pattern: String) { } 
    override fun getExcludePatterns(): MutableList<String> = ArrayList() 
    override fun addSourceFolder(file: VirtualFile, isTestSource: Boolean): SourceFolder { error("") }
    override fun addSourceFolder(file: VirtualFile, isTestSource: Boolean, packagePrefix: String): SourceFolder { error("")  }
    override fun <P: JpsElement?> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder { error("") } 
    override fun <P: JpsElement?> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>): SourceFolder { error("") } 
    override fun addSourceFolder(url: String, isTestSource: Boolean): SourceFolder { error("") } 
    override fun <P: JpsElement?> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>): SourceFolder { error("") } 
    override fun <P: JpsElement?> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder { error("") } 
    override fun clearSourceFolders() {  } 
    override fun getExcludeFolderUrls(): MutableList<String> = ArrayList() 
    override fun addExcludeFolder(file: VirtualFile): ExcludeFolder { error("") } 
    override fun addExcludeFolder(url: String): ExcludeFolder { error("") } 
    override fun isSynthetic(): Boolean = false 
    override fun removeSourceFolder(sourceFolder: SourceFolder) { } 
}


class ASourceFolder: SourceFolder {
    override fun getUrl() = ""
    override fun getFile() = null
    override fun getPackagePrefix() = ""
    override fun getContentEntry(): ContentEntry { error("") }
    override fun isTestSource() = true
    override fun isSynthetic() = false
    override fun getRootType(): JpsModuleSourceRootType<*> { error("") }
    override fun getJpsElement(): JpsModuleSourceRoot { error("") }
    override fun setPackagePrefix(packagePrefix: String) { error("") }
}


class AModule(@JvmField val project: Project): UserDataHolderBase(), Module {
    var rootManager: ModuleRootManager? = null
    private val facetManager = FacetManagerImpl(this, project.messageBus)
    private val scopeProvider = ModuleScopeProviderImpl(this)

    override fun getName() = "name"
    override fun getProject() = project
    override fun getOptionValue(key: String) = null
    override fun getModuleFilePath(): String = ""
    override fun getModuleFile(): VirtualFile? = null
    override fun isLoaded() = true
    override fun <T: Any?> getExtensions(extensionPointName: ExtensionPointName<T>): Array<T> = emptyArray<Any>() as Array<T>
    override fun setOption(key: String, value: String?) {}
    override fun getModuleWithLibrariesScope() = scopeProvider.getModuleWithLibrariesScope()
    override fun getModuleWithDependentsScope() = scopeProvider.getModuleWithDependentsScope()
    override fun getModuleContentScope() = scopeProvider.getModuleContentScope()
    override fun getModuleWithDependenciesScope() = scopeProvider.getModuleWithDependenciesScope()
    override fun getModuleWithDependenciesAndLibrariesScope(includeTests: Boolean) = scopeProvider.getModuleWithDependenciesAndLibrariesScope(includeTests)
    override fun getModuleContentWithDependenciesScope() = scopeProvider.getModuleContentWithDependenciesScope()
    override fun getModuleTestsWithDependentsScope(): GlobalSearchScope = scopeProvider.getModuleTestsWithDependentsScope()
    override fun getModuleScope() = scopeProvider.getModuleScope()
    override fun getModuleScope(includeTests: Boolean) = scopeProvider.getModuleScope(includeTests)
    override fun getModuleRuntimeScope(includeTests: Boolean) = scopeProvider.getModuleRuntimeScope(includeTests)

    override fun <T: Any?> getComponent(interfaceClass: Class<T>): T =
        if (interfaceClass == ModuleRootManager::class.java) rootManager as T
        else if (interfaceClass == FacetManager::class.java) facetManager as T
        else null as T

    override fun <T: Any?> getComponents(baseClass: Class<T>) = error("")
    override fun isDisposed() = false
    override fun getComponent(name: String): BaseComponent { error("") }
    override fun <T: Any?> getComponent(interfaceClass: Class<T>, defaultImplementationIfAbsent: T): T { error("") }
    override fun getDisposed(): Condition<*> { error("") }
    override fun getPicoContainer(): PicoContainer { error("") }
    override fun hasComponent(interfaceClass: Class<*>): Boolean { error("") }
    override fun getMessageBus(): MessageBus { error("") }
    override fun dispose() {}
}

class AModuleFileIndex: ModuleFileIndex {
    override fun isInTestSourceContent(fileOrDir: VirtualFile) = false
    override fun iterateContent(processor: ContentIterator) = false
    override fun iterateContent(processor: ContentIterator, filter: VirtualFileFilter?) = false
    override fun iterateContentUnderDirectory(dir: VirtualFile, processor: ContentIterator) = false
    override fun iterateContentUnderDirectory(dir: VirtualFile, processor: ContentIterator, customFilter: VirtualFileFilter?) = false
    override fun isContentSourceFile(file: VirtualFile) = false
    override fun isInSourceContent(fileOrDir: VirtualFile) = false
    override fun getOrderEntriesForFile(fileOrDir: VirtualFile): MutableList<OrderEntry> = ArrayList()
    override fun isUnderSourceRootOfType(fileOrDir: VirtualFile, rootTypes: MutableSet<out JpsModuleSourceRootType<*>>) = false
    override fun getOrderEntryForFile(fileOrDir: VirtualFile): OrderEntry? = null
    override fun isInContent(fileOrDir: VirtualFile) = false
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
