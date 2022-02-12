// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package liveplugin.pluginrunner;

import com.intellij.diagnostic.*;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.cl.*;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.*;
import com.intellij.util.*;
import com.intellij.util.lang.*;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Fork of com.intellij.ide.plugins.cl.PluginClassLoader
 * because its an internal IJ API and it has been changing in IJ 2020.2 causing LivePlugin to break.
 * The assumption is that using fork will make LivePlugin forward-compatible with more IJ version
 * given that PluginClassLoader_Fork implementation compatibility is more stable than PluginClassLoader API.
 */
@SuppressWarnings("ALL")
public class PluginClassLoader_Fork extends UrlClassLoader implements PluginAwareClassLoader {
    public static final ClassLoader[] EMPTY_CLASS_LOADER_ARRAY = new ClassLoader[0];

    private static final boolean isParallelCapable = registerAsParallelCapable();

    private static final @Nullable Writer logStream;
    private static final AtomicInteger instanceIdProducer = new AtomicInteger();
    private static final AtomicInteger parentListCacheIdCounter = new AtomicInteger();

    private static final Set<String> KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES;

    // avoid capturing reference to classloader in AccessControlContext
    private static final ProtectionDomain PROTECTION_DOMAIN = new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null);

    static {
        @SuppressWarnings("SSBasedInspection")
        Set<String> kotlinStdlibClassesUsedInSignatures = new HashSet<>(Arrays.asList(
            "kotlin.Function",
            "kotlin.sequences.Sequence",
            "kotlin.ranges.IntRange",
            "kotlin.ranges.IntRange$Companion",
            "kotlin.ranges.IntProgression",
            "kotlin.ranges.ClosedRange",
            "kotlin.ranges.IntProgressionIterator",
            "kotlin.ranges.IntProgression$Companion",
            "kotlin.ranges.IntProgression",
            "kotlin.collections.IntIterator",
            "kotlin.Lazy", "kotlin.Unit",
            "kotlin.Pair", "kotlin.Triple",
            "kotlin.jvm.internal.DefaultConstructorMarker",
            "kotlin.jvm.internal.ClassBasedDeclarationContainer",
            "kotlin.properties.ReadWriteProperty",
            "kotlin.properties.ReadOnlyProperty",
            "kotlin.coroutines.ContinuationInterceptor",
            "kotlinx.coroutines.CoroutineDispatcher",
            "kotlin.coroutines.Continuation",
            "kotlin.coroutines.CoroutineContext",
            "kotlin.coroutines.CoroutineContext$Element",
            "kotlin.coroutines.CoroutineContext$Key"
        ));
        String classes = System.getProperty("idea.kotlin.classes.used.in.signatures");
        if (classes != null) {
            for (StringTokenizer t = new StringTokenizer(classes, ","); t.hasMoreTokens(); ) {
                kotlinStdlibClassesUsedInSignatures.add(t.nextToken());
            }
        }
        KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES = kotlinStdlibClassesUsedInSignatures;

        Writer logStreamCandidate = null;
        String debugFilePath = System.getProperty("plugin.classloader.debug", "");
        if (!debugFilePath.isEmpty()) {
            try {
                logStreamCandidate = Files.newBufferedWriter(Paths.get(debugFilePath));
                ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (logStream != null) {
                                logStream.close();
                            }
                        }
                        catch (IOException e) {
                            Logger.getInstance(PluginClassLoader_Fork.class).error(e);
                        }
                    }
                });
            }
            catch (IOException e) {
                Logger.getInstance(PluginClassLoader_Fork.class).error(e);
            }
        }

        logStream = logStreamCandidate;
    }

    private ClassLoader[] parents;
    // cache of computed list of all parents (not only direct)
    private volatile ClassLoader[] allParents;
    private volatile int allParentsLastCacheId;

    private final PluginDescriptor pluginDescriptor;
    // to simplify analyzing of heap dump (dynamic plugin reloading)
    private final PluginId pluginId;
    private final String packagePrefix;
    private final List<String> libDirectories;

    private final AtomicLong edtTime = new AtomicLong();
    private final AtomicLong backgroundTime = new AtomicLong();

    private final AtomicInteger loadedClassCounter = new AtomicInteger();
    private final @NotNull ClassLoader coreLoader;

    private final int instanceId;
    private volatile int state = ACTIVE;

    public PluginClassLoader_Fork(@NotNull UrlClassLoader.Builder builder,
                                  @NotNull ClassLoader @NotNull [] parents,
                                  @NotNull PluginDescriptor pluginDescriptor,
                                  @Nullable Path pluginRoot,
                                  @NotNull ClassLoader coreLoader,
                                  @Nullable String packagePrefix,
                                  @Nullable ClassPath.ResourceFileFactory resourceFileFactory) {
        super(builder, null, isParallelCapable);

        instanceId = instanceIdProducer.incrementAndGet();

        this.parents = parents;
        this.pluginDescriptor = pluginDescriptor;
        pluginId = pluginDescriptor.getPluginId();
        this.packagePrefix = (packagePrefix == null || packagePrefix.endsWith(".")) ? packagePrefix : (packagePrefix + '.');
        this.coreLoader = coreLoader;

        // Commented out because this error is thrown for plugins with: depends-on-plugin org.jetbrains.kotlin
//        if (PluginClassLoader_Fork.class.desiredAssertionStatus()) {
//            for (ClassLoader parent : this.parents) {
//                if (parent == coreLoader) {
//                    Logger.getInstance(PluginClassLoader_Fork.class).error("Core loader must be not specified in parents " +
//                        "(parents=" + Arrays.toString(parents) + ", coreLoader=" + coreLoader + ")");
//                }
//            }
//        }

        libDirectories = new SmartList<>();
        if (pluginRoot != null) {
            Path libDir = pluginRoot.resolve("lib");
            if (Files.exists(libDir)) {
                libDirectories.add(libDir.toAbsolutePath().toString());
            }
        }
    }

    @Override
    public final @Nullable String getPackagePrefix() {
        return packagePrefix;
    }

    @Override
    @ApiStatus.Internal
    public final int getState() {
        return state;
    }

    @ApiStatus.Internal
    public final void setState(int state) {
        this.state = state;
    }

    @Override
    public final int getInstanceId() {
        return instanceId;
    }

    @Override
    public final long getEdtTime() {
        return edtTime.get();
    }

    @Override
    public final long getBackgroundTime() {
        return backgroundTime.get();
    }

    @Override
    public final long getLoadedClassCount() {
        return loadedClassCounter.get();
    }

    @Override
    public final Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
        Class<?> c = tryLoadingClass(name, false);
        if (c == null) {
            flushDebugLog();
            throw new ClassNotFoundException(name + " " + this);
        }
        return c;
    }

    /**
     * See https://stackoverflow.com/a/5428795 about resolve flag.
     */
    @Override
    public final @Nullable Class<?> tryLoadingClass(@NotNull String name, boolean forceLoadFromSubPluginClassloader)
        throws ClassNotFoundException {
        if (mustBeLoadedByPlatform(name)) {
            return coreLoader.loadClass(name);
        }

        long startTime = StartUpMeasurer.measuringPluginStartupCosts ? StartUpMeasurer.getCurrentTime() : -1;
        Class<?> c;
        try {
            c = loadClassInsideSelf(name, forceLoadFromSubPluginClassloader);
        }
        catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }

        if (c == null) {
            for (ClassLoader classloader : getAllParents()) {
                if (classloader instanceof UrlClassLoader) {
                    try {
//                        c = ((PluginClassLoader_Fork)classloader).loadClassInsideSelf(name, false);
                        c = ((UrlClassLoader)classloader).loadClassInsideSelf(name, name, 0, false);
                    }
                    catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                    if (c != null) {
                        break;
                    }
                }
                else {
                    try {
                        c = classloader.loadClass(name);
                        if (c != null) {
                            break;
                        }
                    }
                    catch (ClassNotFoundException ignoreAndContinue) {
                        // ignore and continue
                    }
                }
            }
        }

        if (startTime != -1) {
            // EventQueue.isDispatchThread() is expensive
            (EDT.isCurrentThreadEdt() ? edtTime : backgroundTime).addAndGet(StartUpMeasurer.getCurrentTime() - startTime);
        }

        return c;
    }

    private @NotNull ClassLoader @NotNull[] getAllParents() {
        ClassLoader[] result = allParents;
        if (result != null && allParentsLastCacheId == parentListCacheIdCounter.get()) {
            return result;
        }

        if (parents.length == 0) {
            result = new ClassLoader[]{coreLoader};
            allParents = result;
            return result;
        }

        Set<ClassLoader> parentSet = new LinkedHashSet<>();
        Deque<ClassLoader> queue = new ArrayDeque<>();
        Collections.addAll(queue, parents);
        ClassLoader classLoader;
        while ((classLoader = queue.pollFirst()) != null) {
            if (classLoader == coreLoader || !parentSet.add(classLoader)) {
                continue;
            }

            if (classLoader instanceof PluginClassLoader_Fork) {
                Collections.addAll(queue, ((PluginClassLoader_Fork)classLoader).parents);
            }
        }
        parentSet.add(coreLoader);
        result = parentSet.toArray(EMPTY_CLASS_LOADER_ARRAY);
        allParents = result;
        allParentsLastCacheId = parentListCacheIdCounter.get();
        return result;
    }

    public final void clearParentListCache() {
        allParents = null;
    }

    private static boolean mustBeLoadedByPlatform(@NonNls String className) {
        if (className.startsWith("java.")) {
            return true;
        }

        // some commonly used classes from kotlin-runtime must be loaded by the platform classloader. Otherwise if a plugin bundles its own version
        // of kotlin-runtime.jar it won't be possible to call platform's methods with these types in signatures from such a plugin.
        // We assume that these classes don't change between Kotlin versions so it's safe to always load them from platform's kotlin-runtime.
        return className.startsWith("kotlin.") && (className.startsWith("kotlin.jvm.functions.") ||
            (className.startsWith("kotlin.reflect.") &&
                className.indexOf('.', 15 /* "kotlin.reflect".length */) < 0) ||
            KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES.contains(className));
    }

//    @Override
    public @Nullable Class<?> loadClassInsideSelf(@NotNull String name, boolean forceLoadFromSubPluginClassloader) throws IOException {
        if (packagePrefix != null && isDefinitelyAlienClass(name, packagePrefix)) {
            return null;
        }

        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null && c.getClassLoader() == this) {
                return c;
            }

            Writer logStream = PluginClassLoader_Fork.logStream;
            try {
                c = classPath.findClass(name, name, 0, classDataConsumer);
            }
            catch (LinkageError e) {
                if (logStream != null) {
                    logClass(name, logStream, e);
                }
                flushDebugLog();
                throw new PluginException("Cannot load class " + name + " (" +
                    "\n  error: " + e.getMessage() +
                    ",\n  classLoader=" + this + "\n)", e, pluginId);
            }

            if (c == null) {
                return null;
            }

            loadedClassCounter.incrementAndGet();
            if (logStream != null) {
                logClass(name, logStream, null);
            }
            return c;
        }
    }

    private void logClass(@NotNull String name, @NotNull Writer logStream, @Nullable LinkageError exception) {
        try {
            // must be as one write call since write is performed from multiple threads
            String specifier = getClass() == PluginClassLoader_Fork.class ? "m" : "s = " + ((IdeaPluginDescriptor)pluginDescriptor).getDescriptorPath();
            logStream.write(name + " [" + specifier + "] " + pluginId.getIdString() + (packagePrefix == null ? "" : (':' + packagePrefix)) + '\n' + (exception == null ? "" : exception.getMessage()));
        }
        catch (IOException ignored) {
        }
    }

    protected boolean isDefinitelyAlienClass(@NotNull String name, @NotNull String packagePrefix) {
        // packed into plugin jar
        return !name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier");
    }

    @Override
    public final @Nullable URL findResource(@NotNull String name) {
        String canonicalPath = toCanonicalPath(name);
        Resource resource = classPath.findResource(canonicalPath);
        if (resource != null) return resource.getURL();

        URL result = doFindResource(canonicalPath);
        if (result == null && canonicalPath.startsWith("/")) {
            Logger.getInstance(PluginClassLoader_Fork.class).error(
                "Do not request resource from classloader using path with leading slash", new IllegalArgumentException(name));
            result = doFindResource(canonicalPath.substring(1));
        }
        return result;
    }

    private @Nullable URL doFindResource(String canonicalPath) {
        for (ClassLoader classloader : getAllParents()) {
            if (classloader instanceof PluginClassLoader_Fork) {
                Resource resource = ((PluginClassLoader_Fork)classloader).classPath.findResource(canonicalPath);
                if (resource != null) {
                    return resource.getURL();
                }
            }
            else {
                URL resourceUrl = classloader.getResource(canonicalPath);
                if (resourceUrl != null) {
                    return resourceUrl;
                }
            }
        }
        return null;
    }

    @Override
    public final @Nullable InputStream getResourceAsStream(@NotNull String name) {
        String canonicalPath = toCanonicalPath(name);

        Resource resource = classPath.findResource(canonicalPath);
        if (resource != null) {
            try {
                return resource.getInputStream();
            }
            catch (IOException e) {
                Logger.getInstance(PluginClassLoader_Fork.class).error(e);
            }
        }

        for (ClassLoader classloader : getAllParents()) {
            if (classloader instanceof PluginClassLoader_Fork) {
                resource = ((PluginClassLoader_Fork)classloader).classPath.findResource(canonicalPath);
                if (resource != null) {
                    try {
                        return resource.getInputStream();
                    }
                    catch (IOException e) {
                        Logger.getInstance(PluginClassLoader_Fork.class).error(e);
                    }
                }
            }
            else {
                InputStream stream = classloader.getResourceAsStream(canonicalPath);
                if (stream != null) {
                    return stream;
                }
            }
        }

        if (name.startsWith("/")) {
            throw new IllegalArgumentException("Do not request resource from classloader using path with leading slash (path=" + name + ")");
        }
        return null;
    }

    @Override
    public final @NotNull Enumeration<URL> findResources(@NotNull String name) throws IOException {
        List<Enumeration<URL>> resources = new ArrayList<>();
        resources.add(classPath.getResources(name));
        for (ClassLoader classloader : getAllParents()) {
            if (classloader instanceof PluginClassLoader_Fork) {
                resources.add(((PluginClassLoader_Fork)classloader).classPath.getResources(name));
            }
            else {
                try {
                    resources.add(classloader.getResources(name));
                }
                catch (IOException ignore) {
                }
            }
        }
        return new DeepEnumeration(resources);
    }

    @SuppressWarnings("UnusedDeclaration")
    public final void addLibDirectories(@NotNull Collection<String> libDirectories) {
        this.libDirectories.addAll(libDirectories);
    }

    @Override
    protected final String findLibrary(String libName) {
        if (!libDirectories.isEmpty()) {
            String libFileName = System.mapLibraryName(libName);
            ListIterator<String> i = libDirectories.listIterator(libDirectories.size());
            while (i.hasPrevious()) {
                File libFile = new File(i.previous(), libFileName);
                if (libFile.exists()) {
                    return libFile.getAbsolutePath();
                }
            }
        }
        return null;
    }

    @Override
    public final @NotNull PluginId getPluginId() {
        return pluginId;
    }

    @Override
    public final @NotNull PluginDescriptor getPluginDescriptor() {
        return pluginDescriptor;
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "(plugin=" + pluginDescriptor +
            ", packagePrefix=" + packagePrefix +
            ", instanceId=" + instanceId +
            ", state=" + (state == ACTIVE ? "active" : "unload in progress") +
            ")";
    }

    private static final class DeepEnumeration implements Enumeration<URL> {
        private final @NotNull List<? extends Enumeration<URL>> list;
        private int myIndex;

        DeepEnumeration(@NotNull List<? extends Enumeration<URL>> enumerations) {
            list = enumerations;
        }

        @Override
        public boolean hasMoreElements() {
            while (myIndex < list.size()) {
                Enumeration<URL> e = list.get(myIndex);
                if (e != null && e.hasMoreElements()) {
                    return true;
                }
                myIndex++;
            }
            return false;
        }

        @Override
        public URL nextElement() {
            if (!hasMoreElements()) {
                throw new NoSuchElementException();
            }
            return list.get(myIndex).nextElement();
        }
    }

    @TestOnly
    @ApiStatus.Internal
    public final @NotNull List<ClassLoader> _getParents() {
        //noinspection SSBasedInspection
        return Collections.unmodifiableList(Arrays.asList(parents));
    }

    @ApiStatus.Internal
    public final void attachParent(@NotNull ClassLoader classLoader) {
        int length = parents.length;
        ClassLoader[] result = new ClassLoader[length + 1];
        System.arraycopy(parents, 0, result, 0, length);
        result[length] = classLoader;
        parents = result;
        parentListCacheIdCounter.incrementAndGet();
    }

    /**
     * You must clear allParents cache for all loaded plugins.
     */
    @ApiStatus.Internal
    public final boolean detachParent(@NotNull ClassLoader classLoader) {
        for (int i = 0; i < parents.length; i++) {
            if (classLoader != parents[i]) {
                continue;
            }

            int length = parents.length;
            ClassLoader[] result = new ClassLoader[length - 1];
            System.arraycopy(parents, 0, result, 0, i);
            System.arraycopy(parents, i + 1, result, i, length - i - 1);
            parents = result;
            parentListCacheIdCounter.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    protected final ProtectionDomain getProtectionDomain() {
        return PROTECTION_DOMAIN;
    }

    private static void flushDebugLog() {
        if (logStream != null) {
            try {
                logStream.flush();
            }
            catch (IOException ignore) {
            }
        }
    }
}
