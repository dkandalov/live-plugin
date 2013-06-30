package liveplugin.pluginrunner

import clojure.lang.Namespace
import clojure.lang.RT
import clojure.lang.Symbol
import clojure.lang.Var
import com.intellij.openapi.util.io.FileUtil
import liveplugin.MyFileUtil

def scriptFile = MyFileUtil.findSingleFileIn(pathToPluginFolder, ClojurePluginRunner.MAIN_SCRIPT)
assert scriptFile != null

// need this to avoid "java.lang.IllegalStateException: Attempting to call unbound fn: #'clojure.core/refer"
// see https://groups.google.com/forum/#!topic/clojure/F3ERon6Fye0
Thread.currentThread().setContextClassLoader(ClojurePluginRunner.class.classLoader)

// need to initialize RT before Compiler, otherwise Compiler initialization fails with NPE
RT.init() 

Var.pushThreadBindings(Var.threadBindings.assoc(createKey("*event*"), event))
Var.pushThreadBindings(Var.threadBindings.assoc(createKey("*project*"), project))
Var.pushThreadBindings(Var.threadBindings.assoc(createKey("*is-ide-startup*"), isIdeStartup))
Var.pushThreadBindings(Var.threadBindings.assoc(createKey("*plugin-path*"), pluginPath))

clojure.lang.Compiler.load(new StringReader(FileUtil.loadFile(scriptFile))) // throws exception if evaluation failed


def createKey(String name) {
	Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")), Symbol.intern(name), "no_" + name).setDynamic()
}