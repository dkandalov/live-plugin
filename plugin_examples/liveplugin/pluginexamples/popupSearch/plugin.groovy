import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import groovy.json.JsonSlurper

import static liveplugin.PluginUtil.*

registerAction("GoogleSearchAction", "ctrl shift G") { AnActionEvent event ->
	def itemProvider = { String pattern, ProgressIndicator indicator ->
		googleSuggestionsFor(pattern)
	}
	showPopupSearch("Google quick search", event.project, itemProvider) { String item ->
		BrowserUtil.open("http://www.google.com/search?q=${item}")
	}
}

List<String> googleSuggestionsFor(String text) {
	text = URLEncoder.encode(text, "UTF-8")
	def json = "http://suggestqueries.google.com/complete/search?client=firefox&q=$text".toURL().text
	new JsonSlurper().parseText(json)[1].toList()
}

if (!isIdeStartup) show("Loaded GoogleSearchAction<br/>Use ctrl+shift+G to run it")