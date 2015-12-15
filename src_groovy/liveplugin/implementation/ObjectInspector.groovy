package liveplugin.implementation

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopupFactory

import java.lang.reflect.Field
import java.lang.reflect.Modifier

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES

class FieldWithValue {
	final Object object
	final Field field

	FieldWithValue(Object object, Field field) {
		this.object = object
		this.field = field
	}
}

class ObjectInspector implements TreeUI.TreeNode<FieldWithValue> {
	static final Map defaultConfig = [
			showStatic: false,
			showModifiers: false,
			showHashCode: false,
			shortClassNames: true
	]
	private final FieldWithValue context
	private final Map config

	static void inspect(Object object) {
		def component = TreeUI.createTree(new ObjectInspector(new FieldWithValue(object, null)))

		JBPopupFactory.instance.createComponentPopupBuilder(component, null)
				.setTitle("Object inspector")
				.setResizable(true)
				.setMovable(true)
				.setCancelOnClickOutside(false)
				.setCancelButton(new IconButton("Close", AllIcons.Actions.Close))
				.createPopup()
				.showInFocusCenter()
	}

	ObjectInspector(FieldWithValue fieldWithValue, Map config = defaultConfig) {
		this.context = fieldWithValue
		this.config = config
	}

	@Override Collection<ObjectInspector> children() {
		if (context?.object?.class == null || context?.field?.type?.primitive) return []
		sortByModifiersAndName(context.object.class.declaredFields.toList())
				.findAll { config.showStatic ? it : !Modifier.isStatic(it.modifiers) }
				.collect{ new ObjectInspector(new FieldWithValue(fieldValue(it, context.object), it)) }
	}

	@Override PresentationData presentation() {
		def data = new PresentationData()
		data.addText(presentationOf(context), REGULAR_ATTRIBUTES)
		data
	}

	private String presentationOf(FieldWithValue context) {
		if (context.field == null && context.object == null) return "null"
		if (context.field == null) return context.object?.class?.name + " = " + context.object?.toString()

		def modifiers = config.showModifiers ? Modifier.toString(context.field.modifiers) : ""
		def typeInfo = typeInfoOf(context)
		def hashCode = config.showHashCode ? hashCodeOf(context) : ""
		def value = context.object.toString().take(100)

		postfix(modifiers, " ") + typeInfo + prefix("@", hashCode) + " " + context.field.name + " = " + value
	}

	private String typeInfoOf(FieldWithValue context) {
		def s = getTypeName(context.field.type)
		config.shortClassNames ? s.substring(s.lastIndexOf(".") + 1) : s
	}

	private static String hashCodeOf(FieldWithValue context) {
		if (context.field.type.primitive) ""
		else context.object == null ? "" : context.object.hashCode()
	}

	private static String prefix(String prefix, String s) {
		if (s == null || s.empty) s
		else prefix + s
	}

	private static String postfix(String s, String postfixString) {
		if (s == null || s.empty) s
		else s + postfixString
	}

	private static Object fieldValue(Field field, Object object) {
		field.accessible = true
		field.get(object)
	}

	private static List<Field> sortByModifiersAndName(List<Field> fields) {
		def closure = { Field field -> modifiersGroup(field.modifiers) }
		def closure1 = { entry -> entry.value.sort({ Field field -> field.name }) }
		fields.groupBy(closure).sort().collectMany(closure1)
	}

	private static int modifiersGroup(int modifiers) {
		def _final = [{Modifier.isFinal(modifiers)}, -1]
		def _public = [{Modifier.isPublic(modifiers)}, -30]
		def _protected = [{Modifier.isProtected(modifiers)}, -10]
		def _private = [{Modifier.isPrivate(modifiers)}, 0]
		def _pack_private = [{!_public[0]() && !_protected[0]() && !_private[0]()}, -20]
		def _static = [{Modifier.isStatic(modifiers)}, -100]

		[_final, _public, _protected, _pack_private, _private, _static].inject(0) { result, entry ->
			result + (entry[0]() ? entry[1] : 0)
		}
	}

	// copied from jdk
	private static String getTypeName(Class type) {
		if (type.isArray()) {
			try {
				Class cl = type;
				int dimensions = 0;
				while (cl.isArray()) {
					dimensions++;
					cl = cl.getComponentType();
				}
				StringBuffer sb = new StringBuffer();
				sb.append(cl.getName());
				for (int i = 0; i < dimensions; i++) {
					sb.append("[]");
				}
				return sb.toString();
			} catch (Throwable ignored) { /*FALLTHRU*/ }
		}
		return type.getName();
	}
}
