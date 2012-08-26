This is IntelliJ IDEA plugin for writing simple plugins in Groovy (or running Groovy code inside IntelliJ).

How to use
===========
 - open "Plugins" tool window on the right side
 - edit one of "plugin.groovy" files (these are entry points for plugins)
 - use "ctrl + C, ctrl + E" to execute current plugin

Plugins are stored on application level.
(Note that IDE support for editing plugins is very limited.)


To get full IDE support it can be useful to set up a project with all plugins and IntelliJ JDK attached.
You can set up a project for all plugins at the following folder:
 - $HOME/.$INTELLIJ_VERSION/config/intellij-eval-plugins/
 - (on Mac) $HOME/Library/Application Support/IntelliJIdea12/intellij-eval-plugins/


Why
===
 - useful for simple or throw-away plugins
 - plugins should be easy to change
 - plugins with immediately available source code
 - it's a good way to quickly try IntelliJ API


Similar plugins
===============
The idea of running code inside IntelliJ is not original. There are similar plugins:
 - [PMIP - Poor Mans IDE Plugin](http://plugins.intellij.net/plugin/?idea&pluginId=4571) (conceptually quite similar plugin)
 - [Remote Groovy Console](http://plugins.intellij.net/plugin/?id=5373)
 - [Script Monkey](http://plugins.intellij.net/plugin?pr=idea&pluginId=3674)
 - [Groovy Console Plugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=4660)
 - [HotPlugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=1020) (probably outdated)
