package intellijeval
import com.intellij.openapi.actionSystem.KeyboardShortcut
import org.junit.Test

import javax.swing.*

import static intellijeval.PluginUtil.asKeyboardShortcut
import static intellijeval.PluginUtil.asString
import static org.junit.Assert.fail

class PluginUtilTest {
	@Test void "should convert valid keystrokes from string into KeyboardShortcut object"() {
		assert asKeyboardShortcut("") == null
		assert asKeyboardShortcut("A") == new KeyboardShortcut(KeyStroke.getKeyStroke("A"), null)
		assert asKeyboardShortcut("alt A") == new KeyboardShortcut(KeyStroke.getKeyStroke("alt A"), null)
		assert asKeyboardShortcut("alt COMMA") == new KeyboardShortcut(KeyStroke.getKeyStroke("alt COMMA"), null)
		assert asKeyboardShortcut("alt A, B") == new KeyboardShortcut(KeyStroke.getKeyStroke("alt A"), KeyStroke.getKeyStroke("B"))
	}

	@Test void "should explicitly fail if keystroke cannot be converted in KeyboardShortcut object"() {
		try {
			asKeyboardShortcut("alt")
			fail()
		} catch (IllegalStateException e) {
			assert e.message == "Invalid keystroke 'alt'"
		}
	}

	@Test void "asString() should convert to string values of any type"() {
		assert asString(null) == "null"

		assert asString(1) == "1"

		assert asString([] as Integer[]) == "[]"
		assert asString([1] as Integer[]) == "[1]"

		assert asString([]) == "[]"
		assert asString([1, 2, 3]) == "[1, 2, 3]"

		assert asString([:]) == "{}"
		assert asString([a: 1]) == "{a=1}"
		assert asString([:].withDefault { 0 }) == "{}"
		assert asString([a: 1].withDefault { 0 }) == "{a=1}"

		asString(new IllegalStateException("message")).split(/\n/).toList().with {
			assert get(0) == "java.lang.IllegalStateException: message"
			assert get(1).contains("at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)")
			assert get(2).contains("at sun.reflect.NativeConstructorAccessorImpl.newInstance")
			assert get(3).contains("at sun.reflect.DelegatingConstructorAccessorImpl.newInstance")
		}
	}
}
