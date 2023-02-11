package liveplugin.reflection

import liveplugin.getField
import liveplugin.getStaticField
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JavaReflectionTests {
    @Test fun `get field`() {
        assertCanAccessIntField(JavaClass(), name = "i1", value = 1)
        assertCanAccessIntField(JavaClass(), name = "i2", value = 2)
        assertCanAccessIntField(JavaClass(), name = "i3", value = 3)
    }

    private fun assertCanAccessIntField(any: Any, name: String, value: Int) {
        assert(any.getField<Int>(name) == value)
        assert(any.getField<Number>(name) == value)
        assert(any.getField<Any>(name) == value)
        assertThrows<IllegalStateException>("Access property by wrong type") {
            any.getField<JavaReflectionTests>(name)
        }
        assertThrows<IllegalStateException>("Access property by wrong name") {
            any.getField("wrongName")
        }
    }

    @Test fun `get static field`() {
        assert(JavaClass::class.java.getStaticField<Int>("i4") == 4)
        assert(JavaClass::class.java.getStaticField<Number>("i4") == 4)
        assert(JavaClass::class.java.getStaticField<Any>("i4") == 4)
        assertThrows<IllegalStateException>("Access property by wrong type") {
            JavaClass::class.java.getStaticField<String>("i4")
        }
        assertThrows<IllegalStateException>("Access property by wrong name") {
            JavaClass::class.java.getStaticField<String>("wrongName")
        }
    }
}