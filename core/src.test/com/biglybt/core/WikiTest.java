package com.biglybt.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.platform.commons.support.ModifierSupport;

import com.biglybt.core.util.UrlUtils;
import com.biglybt.core.util.Wiki;
import com.biglybt.testutil.junit5.DefaultTestCoreConfiguration;

@ExtendWith(DefaultTestCoreConfiguration.class)
public class WikiTest
{

	/**
	 * Asserts a given Wiki page exists.
	 * If the URL has a #ref, then the page is scanned to see if bookmark exists.
	 *
	 * Prints WARNING when no topic found.
	 * Prints WARNING when no anchor found.
	 * Passes if URL loads, regardless of WARNINGs (missing pages/anchors).
	 */
	@ParameterizedTest
	@ArgumentsSource(WikiArgumentsProvider.class)
	@Execution(ExecutionMode.CONCURRENT)
	public void wikiUrlHasAnExistingPage(Map.Entry<String, String> entry)
			throws Exception {

		String wikiName = entry.getKey();
		String wikiUrlString = entry.getValue();
		URL wikiUrl = UrlUtils.getRawURL(wikiUrlString);

		assertThat(wikiUrl)
				.describedAs("%s url=%s", wikiName, wikiUrl)
				.isNotNull();

		HttpURLConnection urlConnection = (HttpURLConnection) wikiUrl.openConnection();
		urlConnection.connect();

		assertThat(urlConnection.getResponseCode())
				.describedAs("Checking if url=%s exists for HTTP_OK", wikiUrl)
				.isEqualTo(HttpURLConnection.HTTP_OK);

		InputStream stream_in = urlConnection.getInputStream();
		String response = new BufferedReader(
			new InputStreamReader(stream_in)).lines().collect(
				Collectors.joining(" ")).toLowerCase(Locale.US);

		if (response.contains("not have a wiki page for this topic")) {
			System.err.println("WARNING: no wiki on topic " + wikiName);
		} else if (wikiUrl.getRef() != null) {
			boolean anchorFound = response.contains("id=\"" + wikiUrl.getRef() + "\"")
					|| response.contains("href=\"#" + wikiUrl.getRef() + '"');

			if (!anchorFound) {
				System.err.println("WARNING: no anchor found #" + wikiUrl.getRef());
			}
		}
		stream_in.close();
	}

	static class WikiArgumentsProvider implements ArgumentsProvider {
		static Map<String, String> getWikiUrlStrings() {
			return Stream.of(Wiki.class.getDeclaredFields())
					.filter(ModifierSupport::isStatic)
					.collect(Collectors.toMap(Field::getName, field -> {
						try {
							return (String) field.get(null);
						} catch (IllegalAccessException e) {
							return null;
						}
					}));
		}

		@Override
		public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
			return getWikiUrlStrings().entrySet().stream()
				.map(Arguments::of);
		}
	}
}
