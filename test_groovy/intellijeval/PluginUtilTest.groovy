package intellijeval

import org.junit.Test

import static intellijeval.PluginUtil.asString


class PluginUtilTest {
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
