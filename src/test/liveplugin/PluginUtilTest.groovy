package liveplugin

import com.intellij.openapi.actionSystem.KeyboardShortcut
import org.junit.Test

import javax.swing.*

import static liveplugin.implementation.Actions.asKeyboardShortcut
import static liveplugin.implementation.Misc.asString
import static org.junit.Assert.fail

class PluginUtilTest {
	@Test void "should convert valid keystrokes from string into KeyboardShortcut object"() {
		assert asKeyboardShortcut("") == null
		assert asKeyboardShortcut("A") == new KeyboardShortcut(KeyStroke.getKeyStroke("A"), null)
		assert asKeyboardShortcut("alt A") == new KeyboardShortcut(KeyStroke.getKeyStroke("alt A"), null)
		assert asKeyboardShortcut("alt COMMA") == new KeyboardShortcut(KeyStroke.getKeyStroke("alt COMMA"), null)
		assert asKeyboardShortcut("alt A, B") == new KeyboardShortcut(KeyStroke.getKeyStroke("alt A"), KeyStroke.getKeyStroke("B"))
	}

	@Test void "should explicitly fail if keystroke cannot be converted into KeyboardShortcut object"() {
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
			assert get(1).startsWith("\tat java.base/jdk.internal.reflect.DirectConstructorHandleAccessor.newInstance")
		}
	}
}
