package liveplugin

inline fun <reified T> Any.get(name: String): T {
    val allFields = javaClass.allSuperClasses().flatMap { it.declaredFields.toList() }
    val fieldClass = T::class.java
    val field = allFields.find { it.name == name && (fieldClass.isAssignableFrom(it.type)) }
        ?: throw IllegalStateException("Didn't find field '${name}' (in class ${fieldClass.canonicalName})")
    field.isAccessible = true

    return field.get(this) as T
}

fun <T> Any.invoke(name: String, vararg args: Any): T {
    val allMethods = javaClass.allSuperClasses().flatMap { it.declaredMethods.toList() }
    val method = (allMethods.find { it.name == name }
        ?: throw IllegalStateException("Didn't find '${name}' (in class ${this.javaClass.canonicalName})"))
    method.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    return method.invoke(this, *args) as T
}

fun Class<Any>.allSuperClasses(): Sequence<Class<Any>> =
    generateSequence(seed = this) { aClass ->
        if (aClass == Object::class.java) null
        else aClass.superclass
    }
