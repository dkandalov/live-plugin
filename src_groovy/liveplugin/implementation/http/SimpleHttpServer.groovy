package liveplugin.implementation.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import java.util.concurrent.Executors

class SimpleHttpServer {
	final int port
	private final MyHandler handler
	private HttpServer server

	static SimpleHttpServer start(Range<Integer> portRange = (8100..9000), String webRootPath,
	                              Closure handler = {null}, Closure errorListener = {}) {
		for (int port in portRange) {
			try {
				return new SimpleHttpServer(port, webRootPath, handler, errorListener).start()
			} catch (BindException ignore) {
			}
		}
		null
	}

	SimpleHttpServer(int port = 8100, String webRootPath, Closure handler = {null}, Closure errorListener = {}) {
		this.port = port
		this.handler = new MyHandler(webRootPath, handler, errorListener)
	}

	SimpleHttpServer start() {
		server = HttpServer.create(new InetSocketAddress(port), 0)
		server.createContext("/", handler)
		server.executor = Executors.newCachedThreadPool()
		server.start()
		this
	}

	SimpleHttpServer stop() {
		server?.stop(0)
		this
	}

	private static class MyHandler implements HttpHandler {
		private final Closure handler
		private final Closure errorListener
		private final String webRootPath

		MyHandler(String webRootPath, Closure handler, Closure errorListener) {
			this.webRootPath = webRootPath
			this.handler = handler
			this.errorListener = errorListener
		}

		@Override void handle(HttpExchange exchange) {
			new Exchanger(exchange).with {
				try {
					def handlerResponse = this.handler(requestURI)
					if (handlerResponse != null) {
						replyWithText(handlerResponse.toString())
					} else if (requestURI.startsWith("/") && requestURI.size() > 1) {
						def file = new File(this.webRootPath + "${URLDecoder.decode(requestURI.toString(), "UTF-8")}")
						if (!file.exists()) {
							replyNotFound()
						} else {
							replyWithText(file.readLines().join("\n"), contentTypeOf(file))
						}
					} else {
						replyNotFound()
					}
				} catch (Exception e) {
					errorListener.call(e)
					replyWithException(e)
				}
			}
		}

		private static String contentTypeOf(File file) {
			if (file.name.endsWith(".css")) "text/css"
			else if (file.name.endsWith(".js")) "text/javascript"
			else if (file.name.endsWith(".html")) "text/html"
			else "text/plain"
		}

		private static class Exchanger {
			private final HttpExchange exchange

			Exchanger(HttpExchange exchange) {
				this.exchange = exchange
			}

			String getRequestURI() {
				exchange.requestURI.toString()
			}

			void replyWithText(String text, String contentType = "text/plain") {
				exchange.responseHeaders.set("Content-Type", contentType)
				exchange.sendResponseHeaders(200, 0)
				exchange.responseBody.write(text.bytes)
				exchange.responseBody.close()
			}

			void replyWithException(Exception e) {
				exchange.responseHeaders.set("Content-Type", "text/plain")
				exchange.sendResponseHeaders(500, 0)
				e.printStackTrace(new PrintStream(exchange.responseBody))
				exchange.responseBody.close()
			}

			void replyNotFound() {
				exchange.sendResponseHeaders(404, 0)
				exchange.responseBody.close()
			}
		}
	}
}
