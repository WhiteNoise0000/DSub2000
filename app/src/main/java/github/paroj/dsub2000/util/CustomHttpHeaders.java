package github.paroj.dsub2000.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Helper for per-server "Custom HTTP Headers".
 *
 * <p>Design goals:
 * <ul>
 *   <li>Per-server storage (isolated by server {@code instance}).</li>
 *   <li>Secure by default: never logs header values and does strict origin matching.</li>
 *   <li>Centralized injection: callers should obtain headers via {@link #getHeadersForRequest(Context, int, String)}.</li>
 * </ul>
 *
 * <p>Security notes:
 * <ul>
 *   <li>Header values may contain secrets (e.g., Cloudflare Access Service Token).</li>
 *   <li>Values are stored encrypted when possible using {@link KeyStoreUtil} (Android Keystore); otherwise plain text
 *       may be stored as a fallback.</li>
 *   <li>Headers are only returned for requests whose origin ({@code scheme/host/port}) exactly matches the configured
 *       external server URL for the selected server instance.</li>
 * </ul>
 */
public final class CustomHttpHeaders {
	private static final Pattern HEADER_NAME_TOKEN =
			Pattern.compile("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$");

	private CustomHttpHeaders() {}

	/**
	 * A single custom header entry as stored in preferences.
	 *
	 * <p>When loaded via {@link #load(Context, int)}, {@link #value} contains the stored representation
	 * (encrypted or plain). When applying to a request via {@link #getHeadersForRequest(Context, int, String)},
	 * the value is decrypted on demand if required.</p>
	 */
	public static final class Header {
		public boolean enabled;
		@NonNull public String name;
		@NonNull public String value;
		public boolean valueEncrypted;

		public Header(boolean enabled, @NonNull String name, @NonNull String value, boolean valueEncrypted) {
			this.enabled = enabled;
			this.name = name;
			this.value = value;
			this.valueEncrypted = valueEncrypted;
		}
	}

	/**
	 * Loads the raw stored entries for the given server instance.
	 *
	 * <p>This method does not decrypt values. Use {@link #getHeadersForRequest(Context, int, String)} to obtain
	 * safe-to-apply headers for a particular URL.</p>
	 */
	@NonNull
	public static List<Header> load(Context context, int instance) {
		SharedPreferences prefs = Util.getPreferences(context);
		String raw = prefs.getString(Constants.PREFERENCES_KEY_SERVER_CUSTOM_HEADERS + instance, null);
		if (raw == null || raw.trim().isEmpty()) {
			return new ArrayList<>();
		}

		try {
			JSONArray array = new JSONArray(raw);
			List<Header> headers = new ArrayList<>(array.length());
			for (int i = 0; i < array.length(); i++) {
				JSONObject obj = array.optJSONObject(i);
				if (obj == null) {
					continue;
				}
				boolean enabled = obj.optBoolean("enabled", true);
				String name = obj.optString("name", "").trim();
				String value = obj.optString("value", "");
				boolean encrypted = obj.optBoolean("valueEncrypted", false);
				headers.add(new Header(enabled, name, value, encrypted));
			}
			return headers;
		} catch (Exception ignored) {
			return new ArrayList<>();
		}
	}

	/**
	 * Saves entries for the given server instance.
	 *
	 * <p>This method writes the values exactly as provided. Callers should ensure values are encrypted (or marked as
	 * plain) as intended.</p>
	 */
	public static void save(Context context, int instance, @NonNull List<Header> headers) {
		JSONArray array = new JSONArray();
		for (Header header : headers) {
			JSONObject obj = new JSONObject();
			try {
				obj.put("enabled", header.enabled);
				obj.put("name", header.name == null ? "" : header.name);
				obj.put("value", header.value == null ? "" : header.value);
				obj.put("valueEncrypted", header.valueEncrypted);
				array.put(obj);
			} catch (Exception ignored) {
			}
		}

		Util.getPreferences(context)
				.edit()
				.putString(Constants.PREFERENCES_KEY_SERVER_CUSTOM_HEADERS + instance, array.toString())
				.apply();
	}

	/**
	 * Returns whether there is at least one enabled entry with a syntactically valid header name.
	 */
	public static boolean hasAnyEnabled(Context context, int instance) {
		for (Header header : load(context, instance)) {
			if (header.enabled && isValidHeaderName(header.name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the headers that should be injected for {@code requestUrl}.
	 *
	 * <p>Headers are only returned when {@code requestUrl}'s origin matches {@link #getAllowedOrigin(Context, int)}.
	 * This prevents secrets from being sent to unrelated hosts (including redirects to other origins).</p>
	 *
	 * <p>If a stored value is encrypted and cannot be decrypted, the header is omitted.</p>
	 */
	@NonNull
	public static Map<String, String> getHeadersForRequest(Context context, int instance, @Nullable String requestUrl) {
		if (requestUrl == null) {
			return Collections.emptyMap();
		}

		Origin allowed = getAllowedOrigin(context, instance);
		if (allowed == null) {
			return Collections.emptyMap();
		}

		Origin actual = Origin.parse(requestUrl);
		if (actual == null || !allowed.equals(actual)) {
			return Collections.emptyMap();
		}

		List<Header> headers = load(context, instance);
		if (headers.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<String, String> out = new LinkedHashMap<>();
		for (Header header : headers) {
			if (!header.enabled) {
				continue;
			}
			String name = header.name == null ? "" : header.name.trim();
			if (!isValidHeaderName(name)) {
				continue;
			}

			String value = header.value == null ? "" : header.value;
			if (header.valueEncrypted && Build.VERSION.SDK_INT >= 23) {
				String decrypted = KeyStoreUtil.decrypt(value);
				if (decrypted != null) {
					value = decrypted;
				} else {
					continue;
				}
			}

			out.put(name, value);
		}
		return out.isEmpty() ? Collections.emptyMap() : out;
	}

	/**
	 * Validates a header name using the RFC 7230 "token" rule (no spaces/colon/control chars).
	 */
	public static boolean isValidHeaderName(@Nullable String name) {
		if (name == null) {
			return false;
		}
		String trimmed = name.trim();
		return !trimmed.isEmpty() && HEADER_NAME_TOKEN.matcher(trimmed).matches();
	}

	/**
	 * Returns the only allowed origin for custom header injection for this server instance.
	 *
	 * <p>We intentionally only use the external server URL here. The internal/LAN URL is meant for local network usage
	 * and does not require Cloudflare Access headers.</p>
	 */
	@Nullable
	public static Origin getAllowedOrigin(Context context, int instance) {
		SharedPreferences prefs = Util.getPreferences(context);
		String serverUrl = prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null);
		return Origin.parse(serverUrl);
	}

	/**
	 * Normalized URL origin ({@code scheme/host/port}) used for strict comparisons.
	 */
	public static final class Origin {
		@NonNull public final String scheme;
		@NonNull public final String host;
		public final int port;

		private Origin(@NonNull String scheme, @NonNull String host, int port) {
			this.scheme = scheme;
			this.host = host;
			this.port = port;
		}

		/**
		 * Parses {@code url} into a normalized origin.
		 *
		 * <p>This uses {@link URI} parsing and normalizes:
		 * <ul>
		 *   <li>scheme and host to lower case</li>
		 *   <li>port to the explicit numeric port (defaulting to 80/443 for http/https)</li>
		 * </ul>
		 */
		@Nullable
		public static Origin parse(@Nullable String url) {
			if (url == null) {
				return null;
			}
			try {
				URI uri = URI.create(url.trim());
				String scheme = uri.getScheme();
				String host = uri.getHost();
				if (scheme == null || host == null) {
					return null;
				}
				int port = uri.getPort();
				if (port == -1) {
					port = defaultPort(scheme);
				}
				return new Origin(scheme.toLowerCase(Locale.US), host.toLowerCase(Locale.US), port);
			} catch (Exception ignored) {
				return null;
			}
		}

		private static int defaultPort(String scheme) {
			if ("https".equalsIgnoreCase(scheme)) {
				return 443;
			}
			if ("http".equalsIgnoreCase(scheme)) {
				return 80;
			}
			return -1;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Origin origin = (Origin) o;
			return port == origin.port && scheme.equals(origin.scheme) && host.equals(origin.host);
		}

		@Override
		public int hashCode() {
			int result = scheme.hashCode();
			result = 31 * result + host.hashCode();
			result = 31 * result + port;
			return result;
		}
	}
}
