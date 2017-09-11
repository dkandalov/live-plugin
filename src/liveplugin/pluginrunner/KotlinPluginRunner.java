package liveplugin.pluginrunner;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import kotlin.jvm.internal.Reflection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.impl.JavaSdkUtil;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler;
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot;
import org.jetbrains.kotlin.codegen.CompilationException;
import org.jetbrains.kotlin.codegen.GeneratedClassLoader;
import org.jetbrains.kotlin.codegen.state.GenerationState;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.KotlinSourceRoot;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtScript;
import org.jetbrains.kotlin.script.KotlinScriptDefinition;

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
			String pluginFolderUrl = "file:///" + pathToPluginFolder + "/"; // prefix with "file:///" so that unix-like paths work on windows
			pathsToAdd.add(pluginFolderUrl);

			CompilerConfiguration configuration = createCompilerConfiguration(pathToPluginFolder, pluginId, dependentPlugins, errorReporter);

			environment.put("PLUGIN_PATH", pathToPluginFolder);

			KotlinCoreEnvironment environment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, JVM_CONFIG_FILES);
			GenerationState state = KotlinToJVMBytecodeCompiler.INSTANCE.analyzeAndGenerate(environment);
			if (state == null) throw new CompilationException("Compiler returned empty state.", null, null);

			ClassLoader classLoader = createClassLoaderWithDependencies(pathsToAdd, dependentPlugins, mainScriptUrl, pluginId, errorReporter);
			GeneratedClassLoader generatedClassLoader = new GeneratedClassLoader(state.getFactory(), classLoader);

			for (KtFile ktFile : environment.getSourceFiles()) {
				if (ktFile.getName().equals(MAIN_SCRIPT)) {
					KtScript ktScript = ktFile.getScript();
					assert ktScript != null;
					Class<?> aClass = generatedClassLoader.loadClass(ktScript.getFqName().asString());
					runOnEDTCallback.fun(() -> {
						try {
							// Arguments below must match constructor of liveplugin.pluginrunner.KotlinScriptTemplate class.
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

	@NotNull private static CompilerConfiguration createCompilerConfiguration(String pathToPluginFolder, String pluginId,
	                                                                          List<String> dependentPlugins, ErrorReporter errorReporter) {
		CompilerConfiguration configuration = new CompilerConfiguration();
		configuration.put(MODULE_NAME, "LivePluginScript");
		configuration.put(MESSAGE_COLLECTOR_KEY, newMessageCollector(pluginId, errorReporter));
		configuration.put(RETAIN_OUTPUT_IN_MEMORY, true);
		configuration.add(SCRIPT_DEFINITIONS, new KotlinScriptDefinition(Reflection.createKotlinClass(KotlinScriptTemplate.class)));

		configuration.add(CONTENT_ROOTS, new KotlinSourceRoot(pathToPluginFolder));

		for (File file : ideJdkClassesRoots()) {
			configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(file));
		}
		for (File file : listFilesIn(ideLibFolder())) {
			configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(file));
		}
		for (File file : listFilesIn(new File(LIVEPLUGIN_LIBS_PATH))) {
			configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(file));
		}
		for (File file : jarFilesOf(dependentPlugins)) {
			configuration.add(CONTENT_ROOTS, new JvmClasspathRoot(file));
		}

		// TODO add jars from "add-to-classpath"

		// It might be worth using:
		//	    configuration.put(JVMConfigurationKeys.OUTPUT_DIRECTORY, saveClassesDir)
		// But compilation performance doesn't seem to be the biggest problem right now.

		return configuration;
	}

	private static List<File> jarFilesOf(List<String> dependentPlugins) {
		List<IdeaPluginDescriptor> pluginDescriptors = pluginDescriptorsOf(dependentPlugins, it -> {
			throw new IllegalStateException("Failed to find jar for dependent plugin '" + it + "'.");
		});
		return ContainerUtil.map(pluginDescriptors, it -> it.getPath());
	}

	private static List<File> ideJdkClassesRoots() {
		return JavaSdkUtil.getJdkClassesRoots(new File(System.getProperty("java.home")), true);
	}

	private static File ideLibFolder() {
		String ideJarPath = PathManager.getJarPathForClass(IntelliJLaf.class);
		if (ideJarPath == null) throw new IllegalStateException("Failed to find IDE lib folder.");
		return new File(ideJarPath).getParentFile();
	}

	@NotNull private static MessageCollector newMessageCollector(String pluginId, ErrorReporter errorReporter) {
		return new MessageCollector() {
			boolean hasErrors = false;
			@Override public void report(@NotNull CompilerMessageSeverity severity, @NotNull String message, CompilerMessageLocation location) {
				if (severity == ERROR || severity == EXCEPTION) {
					errorReporter.addLoadingError(pluginId, PLAIN_FULL_PATHS.render(severity, message, location));
					hasErrors = true;
				}
			}
			@Override public boolean hasErrors() { return hasErrors; }
			@Override public void clear() {}
		};
	}
}
