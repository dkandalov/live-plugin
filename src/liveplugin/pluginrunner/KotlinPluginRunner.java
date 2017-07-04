package liveplugin.pluginrunner;

import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import kotlin.jvm.internal.Reflection;
import liveplugin.toolwindow.settingsmenu.languages.DownloadKotlinCompilerLib;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler;
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot;
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRootsKt;
import org.jetbrains.kotlin.codegen.CompilationException;
import org.jetbrains.kotlin.codegen.GeneratedClassLoader;
import org.jetbrains.kotlin.codegen.state.GenerationState;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.KotlinSourceRoot;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtScript;
import org.jetbrains.kotlin.script.KotlinScriptDefinition;
import org.jetbrains.kotlin.utils.PathUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH;
import static liveplugin.MyFileUtil.*;
import static liveplugin.pluginrunner.PluginRunner.ClasspathAddition.*;
import static org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY;
import static org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR;
import static org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.EXCEPTION;
import static org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_FULL_PATHS;
import static org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES;
import static org.jetbrains.kotlin.config.CommonConfigurationKeys.MODULE_NAME;
import static org.jetbrains.kotlin.config.JVMConfigurationKeys.*;

public class KotlinPluginRunner implements PluginRunner {
	public static final String MAIN_SCRIPT = "plugin.kts";
	private static final String KOTLIN_ADD_TO_CLASSPATH_KEYWORD = "// " + ADD_TO_CLASSPATH_KEYWORD;
	private static final String KOTLIN_DEPENDS_ON_PLUGIN_KEYWORD = "// " + DEPENDS_ON_PLUGIN_KEYWORD;

	private final ErrorReporter errorReporter;
	private final Map<String, String> environment;


	public KotlinPluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = environment;
	}

	@Override public String scriptName() {
		return MAIN_SCRIPT;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override
	public void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding, Function<Runnable, Void> runOnEDTCallback) {
		org.jetbrains.kotlin.com.intellij.openapi.Disposable rootDisposable = Disposer.newDisposable();

		try {
			String mainScriptUrl = asUrl(findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT));
			List<String> dependentPlugins = findPluginDependencies(readLines(mainScriptUrl), KOTLIN_DEPENDS_ON_PLUGIN_KEYWORD);
			List<String> pathsToAdd = findClasspathAdditions(readLines(mainScriptUrl), KOTLIN_ADD_TO_CLASSPATH_KEYWORD, environment, path -> {
				errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
				return null;
			});
			String pluginFolderUrl = "file:///" + pathToPluginFolder + "/"; // prefix with "file:///" so that unix-like path works on windows
			pathsToAdd.add(pluginFolderUrl);

			CompilerConfiguration configuration = createConfiguration(pathToPluginFolder, pluginId, errorReporter);

			//	TODO if (saveClassesDir != null) {
			//	    configuration.put(JVMConfigurationKeys.OUTPUT_DIRECTORY, saveClassesDir)
			//	}
			environment.put("PLUGIN_PATH", pathToPluginFolder);

			KotlinCoreEnvironment environment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, JVM_CONFIG_FILES);
			KotlinToJVMBytecodeCompiler compiler = KotlinToJVMBytecodeCompiler.INSTANCE;
			GenerationState state = compiler.analyzeAndGenerate(environment);
			if (state == null) return;
			ClassLoader classLoader = createClassLoaderWithDependencies(pathsToAdd, dependentPlugins, mainScriptUrl, pluginId, errorReporter);
			GeneratedClassLoader generatedClassLoader = new GeneratedClassLoader(state.getFactory(), classLoader);

			for (KtFile ktFile : environment.getSourceFiles()) {
				if (ktFile.getName().equals(MAIN_SCRIPT)) {
					KtScript ktScript = ktFile.getScript();
					assert ktScript != null;
					Class<?> aClass = generatedClassLoader.loadClass(ktScript.getFqName().asString());
					runOnEDTCallback.fun(() -> {
						try {
							// Arguments below must match constructor of KotlinScriptTemplate class.
							// There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
							aClass.getConstructors()[0].newInstance(
									(Project) binding.get("project"),
									(Boolean) binding.get("isIdeStartup"),
									(String) binding.get("pluginPath"),
									(Disposable) binding.get("pluginDisposable")
							);
						} catch (Exception e) {
							errorReporter.addRunningError(pluginId, e);
						}
					});
				}
			}

		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error creating scripting engine. " + e.getMessage());
		} catch (CompilationException | ClassNotFoundException e) {
			errorReporter.addLoadingError(pluginId, "Error compiling script. " + e.getMessage());
		} catch (Throwable e) {
			errorReporter.addLoadingError(pluginId, "Internal error compiling script. " + e.getMessage());
		} finally {
			rootDisposable.dispose();
		}
	}

	@NotNull private static CompilerConfiguration createConfiguration(String pathToPluginFolder, String pluginId, final ErrorReporter errorReporter) {
		MessageCollector messageCollector = new MessageCollector() {
			boolean hasErrors = false;
			@Override public void report(@NotNull CompilerMessageSeverity severity, @NotNull String message, @NotNull CompilerMessageLocation location) {
				if (severity == ERROR || severity == EXCEPTION) {
					errorReporter.addLoadingError(pluginId, PLAIN_FULL_PATHS.render(severity, message, location));
					hasErrors = true;
				}
			}
			@Override public boolean hasErrors() { return hasErrors; }
			@Override public void clear() {}
		};

		CompilerConfiguration configuration = new CompilerConfiguration();
		configuration.put(MESSAGE_COLLECTOR_KEY, messageCollector);
		configuration.put(MODULE_NAME, "LivePluginScript");
		JvmContentRootsKt.addJvmClasspathRoots(configuration, PathUtil.getJdkClassesRootsFromCurrentJre());
		configuration.add(CONTENT_ROOTS, new KotlinSourceRoot(pathToPluginFolder));
		configuration.add(SCRIPT_DEFINITIONS, new KotlinScriptDefinition(Reflection.createKotlinClass(KotlinScriptTemplate.class)));
		configuration.put(RETAIN_OUTPUT_IN_MEMORY, false);

		String ideaJarPath = PathManager.getJarPathForClass(IntelliJLaf.class);
		assert ideaJarPath != null;
		File[] ijLibFiles = new File(ideaJarPath).getParentFile().listFiles();
		assert ijLibFiles != null;
		for (File file : ijLibFiles) {
			configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(file));
		}

		for (String fileName : fileNamesMatching(DownloadKotlinCompilerLib.LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH)) {
			configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(new File(LIVEPLUGIN_LIBS_PATH + "/" + fileName)));
		}
		String ideLibFolderPath = new File(ideaJarPath).getParentFile().getAbsolutePath();
		for (String fileName : fileNamesMatching("kotlin-.*jar", ideLibFolderPath)) {
			configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(new File(ideLibFolderPath + "/" + fileName)));
		}
		
		configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(new File(LIVEPLUGIN_LIBS_PATH)));
		configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(new File(PathManager.getPluginsPath() + "/LivePlugin/classes")));

		// TODO add other plugins jars?

		return configuration;
	}
}
