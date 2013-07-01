package liveplugin.pluginrunner;

import clojure.lang.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static liveplugin.MyFileUtil.findSingleFileIn;

class ClojurePluginRunner implements PluginRunner {
	private static final String MAIN_SCRIPT = "plugin.clj";

	private static boolean initialized;
	private static final ExecutorService executor = newSingleThreadExecutor();

	private final ErrorReporter errorReporter;
	private final Map<String, String> environment; // TODO use it

	public ClojurePluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = environment;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override public void runPlugin(final String pathToPluginFolder, final String pluginId, final Map<String, ?> binding) {
		// make clojure execution thread-confined, otherwise it fails with "clojure.core$eval1 cannot be cast to clojure.lang.IFn"
		Future<Void> future = executor.submit(new Callable<Void>() {
			@Override public Void call() throws Exception {
				if (!initialized) {
					// need this to avoid "java.lang.IllegalStateException: Attempting to call unbound fn: #'clojure.core/refer"
					// use classloader of RunPluginAction assuming that clojure was first initialized from it
					// (see https://groups.google.com/forum/#!topic/clojure/F3ERon6Fye0)
					Thread.currentThread().setContextClassLoader(RunPluginAction.class.getClassLoader());

					// need to initialize RT before Compiler, otherwise Compiler initialization fails with NPE
					RT.init();
					initialized = true;
				}

				for (Map.Entry<String, ?> entry : binding.entrySet()) {
					Var key = createKey("*" + entry.getKey() + "*");
					Var.pushThreadBindings(Var.getThreadBindings().assoc(key, entry.getValue()));
				}

				File scriptFile = findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT);
				assert scriptFile != null;
				try {

					clojure.lang.Compiler.loadFile(scriptFile.getAbsolutePath());

				} catch (IOException e) {
					errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile);
				} catch (Exception e) {
					errorReporter.addRunningPluginException(pluginId, e);
				}
				return null;
			}
		});
		try {
			future.get();
		} catch (InterruptedException ignored) {
		} catch (ExecutionException ignored) {
		}
	}

	private static Var createKey(String name) {
		return Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")), Symbol.intern(name), "no_" + name).setDynamic();
	}
}
