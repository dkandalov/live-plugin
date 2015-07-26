package liveplugin.implementation.http
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import liveplugin.implementation.GlobalVars

class Http {
	private static final Logger log = Logger.getInstance(Http.class)

	static String loadIntoHttpServer(String serverId, String html, String fileName) {
		def tempDir = FileUtil.createTempDirectory(serverId + "_", "")
		new File("$tempDir.absolutePath/$fileName").write(html)

		log.info("Going to register http server for: " + tempDir.absolutePath + "/" + fileName)

		def noRequestHandler = { null }
		def loggingErrorHandler = { log.warn("Error handling http request: " + it.toString()) }
		def server = restartHttpServer(serverId, tempDir.absolutePath, noRequestHandler, loggingErrorHandler)

		"http://localhost:${server.port}/${fileName}"
	}

	static SimpleHttpServer restartHttpServer(String serverId, String webRootPath,
	                                          Closure handler = {null}, Closure errorListener = {}) {
		GlobalVars.changeGlobalVar(serverId) { previousServer ->
			if (previousServer != null) previousServer.stop()

			def server = SimpleHttpServer.start(8100..9000, webRootPath, handler, errorListener)
			if (server == null) throw new IllegalStateException("Failed to start server '${serverId}'")
			server
		}
	}
}
