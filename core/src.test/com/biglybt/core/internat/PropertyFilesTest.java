package com.biglybt.core.internat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.assertj.core.util.Preconditions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.biglybt.testutil.junit5.DefaultTestCoreConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(DefaultTestCoreConfiguration.class)
public class PropertyFilesTest
{

	@Test
	public void noSplitlinesInPropertyFilesForLocalizations()
			throws Exception {
		Locale[] locales = MessageText.getLocales(false);

		for (Locale locale : locales) {
			String resourcePath = MessageText.BUNDLE_NAME.replace('.', '/');
			if (locale == MessageText.LOCALE_ENGLISH || locale == Locale.ROOT ) {
				resourcePath += ".properties";
			} else {
				resourcePath += resourceBundleSuffixFor(locale);
			}

			try (InputStream is = MessageText.class.getClassLoader().getResourceAsStream(resourcePath)) {
				if (is == null) {
					Preconditions.checkNotNull(is, "Resource not found: " + resourcePath);
				}
				try (LineNumberReader lineReader = new LineNumberReader(
						new InputStreamReader(is, StandardCharsets.ISO_8859_1))) {

					String line;
					while ((line = lineReader.readLine()) != null) {
						assertThat(line)
								.describedAs("{} Line#{}", resourcePath, lineReader.getLineNumber())
								.doesNotEndWith("\\");
					}
				}
			}
		}
	}

	private static String resourceBundleSuffixFor(Locale locale) {
		String suffix = "";
		if (Locale.ROOT != locale) {
			if (!locale.getLanguage().isEmpty()) {
				suffix += "_" + locale.getLanguage();
			}
			if (!locale.getScript().isEmpty()) {
				suffix += "_" + locale.getScript();
			}
			if (!locale.getCountry().isEmpty()) {
				suffix += "_" + locale.getCountry();
			}
			if (!locale.getVariant().isEmpty()) {
				suffix += "_" + locale.getVariant();
			}
		}
		suffix += ".properties";
		return suffix;
	}

}
