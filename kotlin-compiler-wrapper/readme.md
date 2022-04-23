This is a wrapper for kotlin compiler API to be used inside LivePlugin (see `liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner`).
The main reason for pulling this code out of LivePlugin was that Kotlin has some classes
with **exactly the same fully qualified names** as classes in IntelliJ.

So with this code inside IJ plugin, it was really hard to know which classes
are going to be loaded (and "namespaced" by classloader) IntelliJ class or class from Kotlin jar.
And using the wrong class could lead to subtle classloading errors which are tricky to debug 
(e.g. errors like "expected type kotlin.String but was kotlin.String").
