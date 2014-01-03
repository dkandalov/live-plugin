package liveplugin.pluginrunner;

import clojure.lang.*;
import clojure.lang.Compiler;
import com.intellij.util.Function;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static liveplugin.MyFileUtil.*;
import static liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies;
import static liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions;

/**
 * This class should not be loaded unless clojure libs are on classpath.
 */
class ClojurePluginRunner implements PluginRunner {
	private static final String MAIN_SCRIPT = "plugin.clj";
	private static final String CLOJURE_ADD_TO_CLASSPATH_KEYWORD = "; " + ADD_TO_CLASSPATH_KEYWORD;

	private static boolean initialized;

	private final ErrorReporter errorReporter;
	private final Map<String, String> environment;


	public ClojurePluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = environment;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override public void runPlugin(final String pathToPluginFolder, final String pluginId,
	                                final Map<String, ?> binding, Function<Runnable, Void> runOnEDTCallback) {
		if (!initialized) {
			// need this to avoid "java.lang.IllegalStateException: Attempting to call unbound fn: #'clojure.core/refer"
			// use classloader of RunPluginAction assuming that clojure was first initialized from it
			// (see https://groups.google.com/forum/#!topic/clojure/F3ERon6Fye0)
			Thread.currentThread().setContextClassLoader(RunPluginAction.class.getClassLoader());

			// need to initialize RT before Compiler, otherwise Compiler initialization fails with NPE
			RT.init();
			initialized = true;
		}

		final File scriptFile = findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT);
		assert scriptFile != null;

		final List<String> additionalPaths = new ArrayList<String>();
		try {
			additionalPaths.addAll(findClasspathAdditions(readLines(asUrl(scriptFile)), CLOJURE_ADD_TO_CLASSPATH_KEYWORD, environment, new Function<String, Void>() {
				@Override public Void fun(String path) {
					errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
					return null;
				}
			}));
		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile);
			return;
		}
		final ClassLoader classLoader = createClassLoaderWithDependencies(additionalPaths, asUrl(scriptFile), pluginId, errorReporter);


		runOnEDTCallback.fun(new Runnable() {
			@Override public void run() {
				try {
					Associative bindings = Var.getThreadBindings();
					for (Map.Entry<String, ?> entry : binding.entrySet()) {
						Var key = createKey("*" + entry.getKey() + "*");
						bindings = bindings.assoc(key, entry.getValue());
					}
					bindings = bindings.assoc(Compiler.LOADER, classLoader);
					Var.pushThreadBindings(bindings);

					// assume that clojure Compile is thread-safe
					clojure.lang.Compiler.loadFile(scriptFile.getAbsolutePath());

				} catch (IOException e) {
					errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile);
				} catch (LinkageError e) {
					errorReporter.addLoadingError(pluginId, "Error linking script file: " + scriptFile);
				} catch (Exception e) {
					errorReporter.addRunningError(pluginId, e);
				} finally {
					Var.popThreadBindings();
				}
			}
		});
	}

	private static Var createKey(String name) {
		return Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")), Symbol.intern(name), "no_" + name).setDynamic();
	}
}
