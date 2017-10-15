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
import static liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDependencies;

/**
 * This class should not be loaded unless clojure libs are on classpath.
 */
public class ClojurePluginRunner implements PluginRunner {
	public static final String mainScript = "plugin.clj";
	private static final String clojureAddToClasspathKeyword = "; " + addToClasspathKeyword;
	private static final String clojureDependsOnPluginKeyword = "; " + dependsOnPluginKeyword;

	private static boolean initialized;

	private final ErrorReporter errorReporter;
	private final Map<String, String> environment;


	public ClojurePluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = environment;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findScriptFileIn(pathToPluginFolder, mainScript) != null;
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

		final File scriptFile = findScriptFileIn(pathToPluginFolder, mainScript);
		assert scriptFile != null;

		final List<String> dependentPlugins = new ArrayList<>();
		final List<String> additionalPaths = new ArrayList<>();
		try {
			environment.put("PLUGIN_PATH", pathToPluginFolder);

			dependentPlugins.addAll(findPluginDependencies(readLines(asUrl(scriptFile)), clojureDependsOnPluginKeyword));
			additionalPaths.addAll(findClasspathAdditions(readLines(asUrl(scriptFile)), clojureAddToClasspathKeyword, environment, path -> {
				errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
				return null;
			}));
		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile);
			return;
		}
		final ClassLoader classLoader = createClassLoaderWithDependencies(additionalPaths, dependentPlugins, asUrl(scriptFile), pluginId, errorReporter);


		runOnEDTCallback.fun(() -> {
			try {
				Associative bindings = Var.getThreadBindings();
				for (Map.Entry<String, ?> entry : binding.entrySet()) {
					Var key = createKey("*" + entry.getKey() + "*");
					bindings = bindings.assoc(key, entry.getValue());
				}
				bindings = bindings.assoc(Compiler.LOADER, classLoader);
				Var.pushThreadBindings(bindings);

				// assume that clojure Compile is thread-safe
				Compiler.loadFile(scriptFile.getAbsolutePath());

			} catch (IOException e) {
				errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile);
			} catch (LinkageError e) {
				errorReporter.addLoadingError(pluginId, "Error linking script file: " + scriptFile);
			} catch (Error e) {
				errorReporter.addLoadingError(pluginId, e);
			} catch (Exception e) {
				errorReporter.addRunningError(pluginId, e);
			} finally {
				Var.popThreadBindings();
			}
		});
	}

	@Override public String scriptName() {
		return mainScript;
	}

	private static Var createKey(String name) {
		return Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")), Symbol.intern(name), "no_" + name).setDynamic();
	}
}
