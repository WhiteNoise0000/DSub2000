package github.paroj.serverproxy;

import java.util.Map;

/**
 * Provides extra HTTP headers to be injected into upstream requests made by {@link WebProxy}.
 *
 * <p>Implementations should be careful not to leak sensitive values to unrelated hosts. The {@code url} passed here is
 * the decoded upstream URL that {@link WebProxy} is about to request.</p>
 */
public interface RequestHeadersProvider {
	/**
	 * Returns a map of headers to inject for the given upstream {@code url}.
	 *
	 * <p>Returning {@code null} or an empty map means no additional headers.</p>
	 */
	Map<String, String> getHeadersForUrl(String url);
}

