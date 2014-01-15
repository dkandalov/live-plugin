package liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.api;
/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.gson.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.net.HttpConfigurable;
import liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.exceptions.*;
import liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.util.GithubAuthData;
import liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.util.GithubSettings;
import liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.util.GithubSslSupport;
import liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.util.GithubUrlUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
public class GithubApiUtil {

	public static final String DEFAULT_GITHUB_HOST = "github.com";

	private static final Logger LOG = Logger.getInstance(GithubApiUtil.class);

	private static final Header ACCEPT_V3_JSON = new Header("Accept", "application/vnd.github.v3+json");

	@NotNull private static final Gson gson = initGson();

	private static Gson initGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
		return builder.create();
	}

	private enum HttpVerb {
		GET, POST, DELETE, HEAD
	}

	@Nullable
	private static JsonElement getRequest(@NotNull GithubAuthData auth, @NotNull String path, @NotNull Header... headers) throws IOException {
		return request(auth, path, null, Arrays.asList(headers), HttpVerb.GET).getJsonElement();
	}

	@NotNull
	private static ResponsePage request(@NotNull GithubAuthData auth,
	                                    @NotNull String path,
	                                    @Nullable String requestBody,
	                                    @NotNull Collection<Header> headers,
	                                    @NotNull HttpVerb verb) throws IOException {
		HttpMethod method = null;
		try {
			String uri = GithubUrlUtil.getApiUrl(auth.getHost()) + path;
			method = doREST(auth, uri, requestBody, headers, verb);

			checkStatusCode(method);

			InputStream resp = method.getResponseBodyAsStream();
			if (resp == null) {
				return new ResponsePage();
			}

			JsonElement ret = parseResponse(resp);
			if (ret.isJsonNull()) {
				return new ResponsePage();
			}

			Header header = method.getResponseHeader("Link");
			if (header != null) {
				String value = header.getValue();
				int end = value.indexOf(">; rel=\"next\"");
				int begin = value.lastIndexOf('<', end);
				if (begin >= 0 && end >= 0) {
					String newPath = GithubUrlUtil.removeProtocolPrefix(value.substring(begin + 1, end));
					int index = newPath.indexOf('/');

					return new ResponsePage(ret, newPath.substring(index));
				}
			}

			return new ResponsePage(ret);
		}
		finally {
			if (method != null) {
				method.releaseConnection();
			}
		}
	}

	@NotNull
	private static HttpMethod doREST(@NotNull final GithubAuthData auth,
	                                 @NotNull final String uri,
	                                 @Nullable final String requestBody,
	                                 @NotNull final Collection<Header> headers,
	                                 @NotNull final HttpVerb verb) throws IOException {
		HttpClient client = getHttpClient(auth.getBasicAuth(), auth.isUseProxy());
		return GithubSslSupport.getInstance()
				.executeSelfSignedCertificateAwareRequest(client, uri, new ThrowableConvertor<String, HttpMethod, IOException>() {
					@Override
					public HttpMethod convert(String uri) throws IOException {
						HttpMethod method;
						switch (verb) {
							case POST:
								method = new PostMethod(uri);
								if (requestBody != null) {
									((PostMethod)method).setRequestEntity(new StringRequestEntity(requestBody, "application/json", "UTF-8"));
								}
								break;
							case GET:
								method = new GetMethod(uri);
								break;
							case DELETE:
								method = new DeleteMethod(uri);
								break;
							case HEAD:
								method = new HeadMethod(uri);
								break;
							default:
								throw new IllegalStateException("Wrong HttpVerb: unknown method: " + verb.toString());
						}
						GithubAuthData.TokenAuth tokenAuth = auth.getTokenAuth();
						if (tokenAuth != null) {
							method.addRequestHeader("Authorization", "token " + tokenAuth.getToken());
						}
						for (Header header : headers) {
							method.addRequestHeader(header);
						}
						return method;
					}
				});
	}

	@NotNull
	private static HttpClient getHttpClient(@Nullable GithubAuthData.BasicAuth basicAuth, boolean useProxy) {
		int timeout = GithubSettings.getInstance().getConnectionTimeout();
		final HttpClient client = new HttpClient();
		HttpConnectionManagerParams params = client.getHttpConnectionManager().getParams();
		params.setConnectionTimeout(timeout); //set connection timeout (how long it takes to connect to remote host)
		params.setSoTimeout(timeout); //set socket timeout (how long it takes to retrieve data from remote host)

		client.getParams().setContentCharset("UTF-8");
		// Configure proxySettings if it is required
		final HttpConfigurable proxySettings = HttpConfigurable.getInstance();
		if (useProxy && proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST)) {
			client.getHostConfiguration().setProxy(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
			if (proxySettings.PROXY_AUTHENTICATION) {
				client.getState().setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN,
						proxySettings.getPlainProxyPassword()));
			}
		}
		if (basicAuth != null) {
			client.getParams().setCredentialCharset("UTF-8");
			client.getParams().setAuthenticationPreemptive(true);
			client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(basicAuth.getLogin(), basicAuth.getPassword()));
		}
		return client;
	}

	private static void checkStatusCode(@NotNull HttpMethod method) throws IOException {
		int code = method.getStatusCode();
		switch (code) {
			case HttpStatus.SC_OK:
			case HttpStatus.SC_CREATED:
			case HttpStatus.SC_ACCEPTED:
			case HttpStatus.SC_NO_CONTENT:
				return;
			case HttpStatus.SC_BAD_REQUEST:
			case HttpStatus.SC_UNAUTHORIZED:
			case HttpStatus.SC_PAYMENT_REQUIRED:
			case HttpStatus.SC_FORBIDDEN:
				String message = getErrorMessage(method);
				if (message.contains("API rate limit exceeded")) {
					throw new GithubRateLimitExceededException(message);
				}
				throw new GithubAuthenticationException("Request response: " + message);
			default:
				throw new GithubStatusCodeException(code + ": " + getErrorMessage(method), code);
		}
	}

	@NotNull
	private static String getErrorMessage(@NotNull HttpMethod method) {
		try {
			InputStream resp = method.getResponseBodyAsStream();
			if (resp != null) {
				GithubErrorMessageRaw error = fromJson(parseResponse(resp), GithubErrorMessageRaw.class);
				return method.getStatusText() + " - " + error.getMessage();
			}
		}
		catch (IOException e) {
			LOG.info(e);
		}
		return method.getStatusText();
	}

	@NotNull
	private static JsonElement parseResponse(@NotNull InputStream githubResponse) throws IOException {
		Reader reader = new InputStreamReader(githubResponse, "UTF-8");
		try {
			return new JsonParser().parse(reader);
		}
		catch (JsonSyntaxException jse) {
			throw new GithubJsonException("Couldn't parse GitHub response", jse);
		}
		finally {
			reader.close();
		}
	}

	private static class ResponsePage {
		@Nullable private final JsonElement response;
		@Nullable private final String nextPage;

		public ResponsePage() {
			this(null, null);
		}

		public ResponsePage(@Nullable JsonElement response) {
			this(response, null);
		}

		public ResponsePage(@Nullable JsonElement response, @Nullable String next) {
			this.response = response;
			this.nextPage = next;
		}

		@Nullable
		public JsonElement getJsonElement() {
			return response;
		}

		@Nullable
		public String getNextPage() {
			return nextPage;
		}
	}

   /*
   * Json API
   */

	static <Raw extends DataConstructor, Result> Result createDataFromRaw(@NotNull Raw rawObject, @NotNull Class<Result> resultClass)
			throws GithubJsonException {
		try {
			return rawObject.create(resultClass);
		}
		catch (Exception e) {
			throw new GithubJsonException("Json parse error", e);
		}
	}


	@NotNull
	private static <T> T fromJson(@Nullable JsonElement json, @NotNull Class<T> classT) throws IOException {
		if (json == null) {
			throw new GithubJsonException("Unexpected empty response");
		}

		T res;
		try {
			//cast as workaround for early java 1.6 bug
			//noinspection RedundantCast
			res = (T)gson.fromJson(json, classT);
		}
		catch (ClassCastException e) {
			throw new GithubJsonException("Parse exception while converting JSON to object " + classT.toString(), e);
		}
		catch (JsonParseException e) {
			throw new GithubJsonException("Parse exception while converting JSON to object " + classT.toString(), e);
		}
		if (res == null) {
			throw new GithubJsonException("Empty Json response");
		}
		return res;
	}

   /*
   * Github API
   */

	@NotNull
	public static GithubGist getGist(@NotNull GithubAuthData auth, @NotNull String id) throws IOException {
		try {
			String path = "/gists/" + id;
			JsonElement result = getRequest(auth, path, ACCEPT_V3_JSON);

			return createDataFromRaw(fromJson(result, GithubGistRaw.class), GithubGist.class);
		}
		catch (GithubConfusingException e) {
			e.setDetails("Can't get gist info: id " + id);
			throw e;
		}
	}

}