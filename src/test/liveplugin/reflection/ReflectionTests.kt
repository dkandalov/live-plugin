package liveplugin.reflection

import liveplugin.getProperty
import liveplugin.invoke
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class KotlinReflectionTests {
    @Test fun `get property of an object declared in super class`() =
        assertCanAccessIntProperty(KotlinClass(), name = "i1", value = 1)

    @Test fun `get property of an object declared in super interface`() =
        assertCanAccessIntProperty(KotlinClass(), name = "i2", value = 2)

    @Test fun `get property of an object`() =
        assertCanAccessIntProperty(KotlinClass(), name = "i3", value = 3)

    @Test fun `get property of a companion object`() =
        assertCanAccessIntProperty(KotlinClass, name = "i4", value = 4)

    @Test fun `invoke function on an object`() {
        assert(KotlinClass().invoke<String>("foo", 123) == "foo-123")
        assert(KotlinClass().invoke<Any>("foo", 123) == "foo-123")

        assertThrows<ClassCastException>("Invoke with wrong return type") {
            KotlinClass().invoke<Int>("foo", 123).toString()
        }
        assertThrows<IllegalArgumentException>("Invoke with missing parameter") {
            KotlinClass().invoke<String>("foo")
        }
        assertThrows<IllegalArgumentException>("Invoke with wrong parameter type") {
            KotlinClass().invoke<Int>("foo", "bar")
        }
    }

    private fun assertCanAccessIntProperty(any: Any, name: String, value: Int) {
        assert(any.getProperty<Int>(name) == value)
        assert(any.getProperty<Number>(name) == value)
        assert(any.getProperty<Any>(name) == value)
        assertThrows<IllegalStateException>("Access property by wrong type") {
            any.getProperty<String>(name)
        }
        assertThrows<IllegalStateException>("Access property by wrong name") {
            any.getProperty("wrongName")
        }
    }
}

@Suppress("unused")
private open class KotlinSuperClass {
    private val i1: Int = 1
}

@Suppress("unused")
private interface KotlinSuperInterface {
    private val i2: Int get() = 2
}

@Suppress("unused")
private class KotlinClass : KotlinSuperClass(), KotlinSuperInterface {
    private val i3: Int = 3

    private fun foo(n: Int) = "foo-$n"

    companion object {
        private val i4: Int = 4
    }
}
