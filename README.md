[![Build Status](https://github.com/dkandalov/live-plugin/workflows/CI/badge.svg)](https://github.com/dkandalov/live-plugin/actions)

# LivePlugin
Plugin for [IntelliJ](https://github.com/JetBrains/intellij-community)-based IDEs to create plugins at runtime
using [Kotlin](http://kotlinlang.org) and [Groovy](https://groovy-lang.org).
To install search for "LivePlugin" in `IDE Settings -> Plugins -> Marketplace`
or use the "Install" button on the [Plugin Marketplace website](http://plugins.jetbrains.com/plugin/7282).


## Table of Contents
- [Why?](#why)
- [Examples](#examples)
- [Getting started](#getting-started)
- [How does LivePlugin work?](#how-does-liveplugin-work)
- [Some practical use cases](#some-practical-use-cases)
- [More examples](#more-examples)
- [Similar plugins](#similar-plugins)
- [Contributing](#contributing)


## Why?
 - **Minimal setup** — no need to set up a separate project for plugin development
 - **Fast feedback loop** — plugins are (re)loaded in the same JVM instance as IDE without restart
 - **Usable IDE API** — LivePlugin has a small API with entry points for common IDE APIs


## Examples
Hello world in Groovy:
```groovy
import static liveplugin.PluginUtil.show
show("Hello world") // Shows balloon notification popup with "Hello world" text
```
Insert New Line Above action in Kotlin:
```kotlin
import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.*

// Action to insert a new line above the current line.
// Based on this post https://martinfowler.com/bliki/InternalReprogrammability.html
// Note that there is also built-in "Start New Line Before Current" action (ctrl+alt+enter).
registerAction(id = "Insert New Line Above", keyStroke = "ctrl alt shift ENTER") { event: AnActionEvent ->
    val project = event.project ?: return@registerAction
    val editor = event.editor ?: return@registerAction
    executeCommand(editor.document, project) { document ->
        val caretModel = editor.caretModel
        val lineStartOffset = document.getLineStartOffset(caretModel.logicalPosition.line)
        document.insertString(lineStartOffset, "\n")
        caretModel.moveToOffset(caretModel.offset + 1)
    }
}
show("Loaded 'Insert New Line Above' action<br/>Use 'ctrl+alt+shift+Enter' to run it")
```


## Getting started
Make sure "hello world" works fine:
- In the `Live Plugins` tool window select "hello-world" plugin and click "Run" button to execute the plugin 
  (`Run Plugin` action with `ctrl+shift+L` or `alt+C, alt+E` shortcut). It should display a message.
- Make a small modification in `plugin.groovy`/`plugin.kts` and rerun the plugin. 
  On the second run, the previous version of the plugin will be unloaded before the code is evaluated again.
- Modify `plugin.groovy`/`plugin.kts` file so that it fails to compile/run.
  You should see an error message in the `Run` tool window.
- Note that plugins are just folders with `plugin.groovy` or `plugin.kts` scripts as entry points. 
  This means that you can, for example, copy the path to the plugin folder using the `Copy Path` action (`ctrl/cmd+alt+C` shortcut).

Try bundled examples:
- In the `Live Plugins` tool window click the "Plus" button (`Add Plugin` action) to add Kotlin or Groovy examples. 
- It might be useful to install [Kotlin](https://plugins.jetbrains.com/plugin/6954-kotlin) or 
[Groovy](http://plugins.jetbrains.com/plugin/1524?pr=idea) plugin if your IDE supports them. 

Take a look at settings in the `Live Plugins` tool window:
- `Run Plugins on IDE Start` — run all plugins on IDE start.
- `Run Project Specific Plugins` — run all plugins in `.live-plugins` project directory when 
the project is opened and unload them when the project is closed.
- `Add LivePlugin and IDE Jars to Project` — add jars from LivePlugin and IDE to the current project as a library (see `Project Settings -> Modules`).
Adding unrelated jars to your project is a bit of a hack, but it can be useful for Groovy plugins to get auto-completion and code navigation in the plugin code.
Kotlin plugins with a single `plugin.kts` should have auto-completion and code navigation without it.
Multiple Kotlin files are not highlighted at the moment (see [this YouTrack issue](https://github.com/dkandalov/live-plugin/issues/105)) but should compile and run.

Learn more about IntelliJ API:
- Read (or at least skim) [plugin development fundamentals](https://plugins.jetbrains.com/docs/intellij/fundamentals.html).
- Explore [IntelliJ source code](https://github.com/JetBrains/intellij-community) by cloning or browsing it on GitHub.
  One useful strategy is to search for text you can see in IDE UI and then figure out 
  how it's connected to the code which does the actual work.
- [PluginUtil](https://github.com/dkandalov/live-plugin/blob/master/src/plugin-api-groovy/liveplugin/PluginUtil.groovy) class
  and [liveplugin](https://github.com/dkandalov/live-plugin/tree/master/src/plugin-api-kotlin/liveplugin) package
  might have some good starting points to explore IntelliJ API.

Once your plugin has grown, you can move it to a proper plugin project 
[still using live plugin for reloading](https://github.com/dkandalov/live-plugin/wiki/Liveplugin-as-an-entry-point-for-standard-plugins)
and maybe then convert it to become a [dynamic plugin](https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html).

If something doesn't work or doesn't make sense, 
please feel free to [report an issue](https://github.com/dkandalov/live-plugin/issues) 
(it's ok to report an issue even if it's just a question).


## How does LivePlugin work?
Overall, the idea is just to load and run plugin Groovy or Kotlin classes in the IDE JVM at runtime.
More specifically the steps are:
- if there is an instance of `pluginDisposable` from previous execution, then dispose it (on EDT)
- create a new classloader with dependencies on other plugins and jars (on a background thread)
- compile code if necessary and load classes in the classloader (on a background thread)
- run the plugin code (on EDT)

This means that plugin code can use any internal IDE API and observe/change IDE state.
There are some limitations of course, such as `final` fields and IDE APIs which are not designed to be re-initialized. 

Most IntelliJ-based IDEs come with a bundled Groovy jar which is used for loading and running live plugins
(otherwise, the groovy-all jar will be downloaded). LivePlugin uses its own version of Kotlin stdlib and compiler because
the version bundled with IDEs changes quite often and seems to be harder to rely on.


## Some practical use cases
- project-specific workflow automation
- integrating shell scripts with IDE
- prototyping plugins, experimenting with IntelliJ API


## More examples
 - [intellij-emacs](https://github.com/kenfox/intellij-emacs) - macros for making IntelliJ more friendly to emacs users (see also [blog post](http://spin.atomicobject.com/2014/08/07/intellij-emacs/))
 - [Simplistic "compile and run haskell" action](https://gist.github.com/dkandalov/11051113) - this can also be done for other languages/environments
 - [Google quick search popup](https://gist.github.com/dkandalov/277800d12ecbfc533fcd) - prototype of Google popup search mini-plugin
 - [Scripting a macros](https://github.com/dkandalov/live-plugin/wiki/Scripting-a-macros) - example of finding and invoking built-in actions
 - [Console filter/transform example](https://github.com/dkandalov/live-plugin/wiki/Console-filtering) - example of filtering and changing console output
 - [VCS update listener example](https://gist.github.com/dkandalov/8840509) - example of adding callback on VCS update
 - [Find class dependencies](https://gist.github.com/dkandalov/6976133) - simple action to find all class dependencies within current project
 - [Module transitive dependencies](https://gist.github.com/dkandalov/80d8d4f71bef54290a71) - finds all transitive dependencies for modules in IDEA project
 - [Show text diff](https://gist.github.com/dkandalov/6728950) - really lame example of opening IntelliJ text diff window (please don't use it!)
 - [Find all recursive methods in project (for Java)](https://gist.github.com/dkandalov/7248184) - quick plugin as a follow-up for this [talk](http://skillsmatter.com/podcast/nosql/using-graphs-for-source-code-analysis)
 - [Watching projects open/close events](https://gist.github.com/dkandalov/6427087) - an example of reloadable project listener
 - [Minimalistic view for java code](https://gist.github.com/dkandalov/708664109a37c3c0ff15) - collapses most of Java keywords and types leaving only variable names
 - [Symbolize keywords](https://gist.github.com/dkandalov/5553999) - collapses Java keywords into shorter symbols
 - [Change List Size Watchdog](https://gist.github.com/dkandalov/5004622) - micro-plugin to show warning when change list size exceeds threshold (see also [Limited WIP plugin](https://github.com/dkandalov/limited-wip))
 - [Template completion on "Tab"](https://gist.github.com/dkandalov/5222759) - simplistic prototype for auto-completion on tab key (in case built-in live templates are not enough)
 - [Completion contributor example](https://gist.github.com/dkandalov/5977888) - only gives an idea which part of IntelliJ API to use
 - [Google auto-completion contributor example](https://github.com/dkandalov/live-plugin/wiki/Google-auto-complete) - same as above but with Google search plugged in
 - [Add custom search example](https://gist.github.com/dkandalov/5956923) - only gives an idea which part of IntelliJ API to use
 - [Get files from last commits example](https://gist.github.com/dkandalov/5984577) - gets VirtualFiles from several last commits
 - [Show PSI view dialog](https://gist.github.com/dkandalov/5979943) - one-liner to show PSI viewer dialog. Normally it's only enabled in plugin projects.
 - [Simplistic "generify return type"](https://gist.github.com/dkandalov/5992191) - attempt to pattern-match PSI tree
 - [No copy-paste](https://gist.github.com/dkandalov/5430282) - disables copy/paste actions
 - [Text munging actions](https://gist.github.com/dkandalov/34daca651fb3fbb9b33f) - simple actions on text (sort, unique, keep/delete lines)
 - [Wrap selection](https://gist.github.com/dkandalov/5129543) - micro-plugin to wrap long lines with separator
 - [Wrap selected text to column width](https://gist.github.com/dkandalov/5557393) - copy of this plugin https://github.com/abrookins/WrapToColumn
 - [Create .jar patch file for current change list](https://gist.github.com/dkandalov/5502872) - that's what it does
 - [Create .jar patch file for specified favorites list](https://gist.github.com/chanshuikay/9850327817fbedceba75) - similar to the above mini-plugin
 - [Remove getters/setters](https://gist.github.com/dkandalov/5476562) - removes all setters or getters in a class
 - [ISO DateTime / Epoch timestamp converter](https://gist.github.com/xhanin/4948901) - converts Epoch time to/from ISO format
 - [Make cursor move in circle](https://gist.github.com/dkandalov/11326385) - definitely not practical but gives an idea about threading
 - [Word Cloud](https://github.com/dkandalov/intellij-wordcloud) - shows world cloud for the selected item (file/package/folder)
 - [Project TreeMap View](https://github.com/dkandalov/project-treemap) - shows project structure (packages/classes) as treemap based on size of classes
 - [Method History](https://github.com/dkandalov/history-slider-plugin) - combines built-in method history based on selection and method history based on method name
 - [Evaluate selection as Groovy](https://gist.github.com/dkandalov/5024580) - that's exactly what it does
 - [Taskbar Icon Changer](https://gist.github.com/markusmo3/ee46e5fe81d4dacea7110134f4ca953f) - Changes the Windows Taskbar icon depending on the project name to provide a better overview when working with multiple projects
 - [Refocus Pinned Find Window](https://gist.github.com/fc1943s/411540e9e29a1296650bcaa8f9a27eec) - Shortcut to refocus the 'Find in Path' dialog with the Pin option enabled after selecting a result entry


## Similar plugins
The idea of running code inside IntelliJ is not original. 
There are/were similar plugins:
 - [Flora](http://plugins.intellij.net/plugin?id=17669) - a similar plugin from JetBrains Hackathon
 - [IDE Scripting Console](https://youtrack.jetbrains.com/issue/IDEA-138252) - experimental feature bundled since IntelliJ 14.1
 - [Script Monkey](http://plugins.intellij.net/plugin?pr=idea&pluginId=3674) (out-of-date)
 - [PMIP - Poor Mans IDE Plugin](http://plugins.intellij.net/plugin/?idea&pluginId=4571) (no longer available)
 - [Remote Groovy Console](http://plugins.intellij.net/plugin/?id=5373) (out-of-date)
 - [Groovy Console Plugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=4660) (out-of-date)
 - [HotPlugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=1020) (out-of-date)


## Contributing
Please see [CONTRIBUTING.md](https://github.com/dkandalov/live-plugin/blob/master/CONTRIBUTING.md).
