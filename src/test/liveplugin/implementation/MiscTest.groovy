package liveplugin.implementation

import org.junit.Test

import static liveplugin.implementation.Misc.accessField
import static org.junit.Assert.fail

class MiscTest {
	@Test void "access to private fields"() {
		def o = new AClass()

		assert accessField(o, "i") == 123
		assert accessField(o, "i", Integer) == 123
		assertException { accessField(o, "i", String) }
		assertException { accessField(o, "j") == null }

		assert accessField(o, ["i"]) == 123
		assert accessField(o, ["a", "b", "i"]) == 123
		assert accessField(o, ["a", "b", "i"], Integer) == 123
		assertException { accessField(o, ["a", "b", "i"], String) }
		assertException { accessField(o, ["a", "b"]) }
	}

	private static assertException(Closure closure) {
		try {
			closure()
			fail("Expected exception")
		} catch (Exception ignored) {
		}
	}

	private static class AClass {
		@SuppressWarnings("GroovyUnusedDeclaration")
		private final Integer i = 123
	}
}
