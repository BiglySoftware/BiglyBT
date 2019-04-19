package com.biglybt.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
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
import org.junit.platform.commons.support.ModifierSupport;

import com.biglybt.core.util.UrlUtils;
import com.biglybt.core.util.Wiki;
import com.biglybt.testutil.junit5.DefaultTestCoreConfiguration;

@ExtendWith(DefaultTestCoreConfiguration.class)
public class WikiTest
{

	/**
	 * Asserts all URLs exists.
	 * If they have a #ref, then the page is scanned to see if bookmark exists.
	 *
	 * Prints WARNING* when no topic found.
	 * Prints WARNING* when no anchor found.
	 */
	@Test
	public void allWikiUrlsHasAnExistingPage()
			throws Exception {

		Map<String, String> wikiUrlStrings = getWikiUrlStrings();

		for (Map.Entry<String, String> entry : wikiUrlStrings.entrySet()) {
			String wikiName = entry.getKey();
			String wikiUrlString = entry.getValue();
			URL wikiUrl = UrlUtils.getRawURL(wikiUrlString);

			assertThat(wikiUrl)
					.describedAs("%s url=%s", wikiName, wikiUrl)
					.isNotNull();


			System.out.print("asserting url exits " + wikiUrlString + " ... ");

			HttpURLConnection urlConnection = (HttpURLConnection) wikiUrl.openConnection();
			urlConnection.connect();

			assertThat(urlConnection.getResponseCode())
					.describedAs("Checking if url=%s exists for HTTP_OK", wikiUrl, wikiName)
					.isEqualTo(HttpURLConnection.HTTP_OK);

			String response = new BufferedReader(new InputStreamReader(urlConnection.getInputStream())).lines().collect(
					Collectors.joining(" ")).toLowerCase(Locale.US);

			System.out.print(urlConnection.getResponseMessage());

			if (response.contains("not have a wiki page for this topic")) {
				System.out.print('*');
				System.err.println("WARNING: no wiki on topic " + wikiName);
			}

			if (wikiUrl.getRef() != null) {
				boolean anchorFound = response.contains("id=\"" + wikiUrl.getRef() + "\"")
						|| response.contains("href=\"#" + wikiUrl.getRef() + '"');

				if (!anchorFound) {
					System.out.print('*');
					System.err.println("WARNING: no anchor found #" + wikiUrl.getRef());
				}
			}

			System.out.println();

			urlConnection.disconnect();
		}

	}

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
}
