package liveplugin

import liveplugin.implementation.common.Result.Failure
import liveplugin.implementation.common.Result.Success
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.isAccessible

inline fun <reified T> Any.getProperty(name: String): T {
    val propertyClass = T::class
    val allProperties = (listOf(this::class) + this::class.allSuperclasses).flatMap { it.declaredMemberProperties }
    val property = allProperties.find { it.name == name && propertyClass.isSuperclassOf(it.returnType.classifier as KClass<*>) }
        ?: throw IllegalStateException("Can't find property '$name: ${propertyClass.qualifiedName}' in ${this::class.qualifiedName}")
    property.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    return (property as KProperty1<Any, T>).get(this)
}

inline fun <reified T> Any.getField(name: String): T =
    internal_getField(this, javaClass, name)

@Suppress("UNCHECKED_CAST")
inline fun <reified T> Class<*>.getStaticField(name: String): T =
    internal_getField(null, this as Class<Any>, name)

/*private*/ inline fun <reified T> internal_getField(obj: Any?, objectClass: Class<Any>, name: String): T {
    val propertyClass = T::class.java
    val allFields = objectClass.allSuperClasses().flatMap { it.declaredFields.toList() }
    val field = allFields.filter { it.name == name }
        .map {
            try {
                it.isAccessible = true
                Success(it.get(obj) as T)
            } catch (e: ClassCastException) {
                Failure(e)
            }
        }
        .find { it is Success }
        ?: throw IllegalStateException("Can't find field '$name: ${propertyClass.canonicalName}' (in class ${objectClass.canonicalName})")
    return (field as Success).value
}

fun <T> Any.invoke(name: String, vararg args: Any): T {
    val allMethods = javaClass.allSuperClasses().flatMap { it.declaredMethods.toList() }
    val method = (allMethods.find { it.name == name }
        ?: throw IllegalStateException("Can't find '${name}' (in class ${javaClass.canonicalName})"))
    method.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    return method.invoke(this, *args) as T
}

fun Class<Any>.allSuperClasses(): Sequence<Class<Any>> =
    generateSequence(seed = this) { aClass ->
        if (aClass == Object::class.java) null else aClass.superclass
    }
