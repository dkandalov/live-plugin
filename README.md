<img src="https://raw.github.com/dkandalov/intellij_eval/master/toolwindow.png" alt="toolwindow" title="toolwindow" align="right" />

What is this?
=============

This is IntelliJ IDEA plugin for writing simple plugins in Groovy<br/>
(or running Groovy code inside IntelliJ).


Why?
====
 - plugins should be easy to change
 - plugins source code should be easily available
 - it's useful for experimental and throw-away plugins
 - it's a good way to quickly try IntelliJ API


How to use
===========
 - open "Plugins" tool window on the right side
 - edit one of "plugin.groovy" files (these are entry points for plugins)
 - use "ctrl + C, ctrl + E" to execute current plugin

Plugins are stored on application level.
(Note that because in "usual" project IntelliJ SDK is not set up, some classes will be read while editing plugins.
If you don't have Groovy plugin installed, plugins text will appear as plain text without syntax highlighting.)


To get full IDE support it can be useful to set up a project with all plugins and IntelliJ JDK attached.
You can set up a project for all plugins in:
 - $HOME/.$INTELLIJ_VERSION/config/intellij-eval-plugins/
 - (on Mac) $HOME/Library/Application Support/IntelliJIdea12/intellij-eval-plugins/


Technical details
=================
 - this is essentially a host plugin for other plugins
 - each plugin is evaluated by its own classloader using GroovyScriptEngine
 - plugin uses Groovy libraries bundled with IntelliJ (this is to reduce plugin size); please check IntelliJ libs if you need to know Groovy version
 - there is currently no easy way to have dependencies between plugins


Similar plugins
===============
The idea of running code inside IntelliJ is not original. There are similar plugins:
 - [PMIP - Poor Mans IDE Plugin](http://plugins.intellij.net/plugin/?idea&pluginId=4571) (conceptually quite similar plugin)
 - [Remote Groovy Console](http://plugins.intellij.net/plugin/?id=5373)
 - [Script Monkey](http://plugins.intellij.net/plugin?pr=idea&pluginId=3674)
 - [Groovy Console Plugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=4660)
 - [HotPlugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=1020) (probably outdated)
