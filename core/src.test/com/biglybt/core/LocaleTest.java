package com.biglybt.core;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.biglybt.core.util.Constants;
import com.biglybt.testutil.junit5.DefaultTestCoreConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(DefaultTestCoreConfiguration.class)
public class LocaleTest
{

	@Test
	public void defaultLocaleIsEnglish() {
		assertThat(Locale.getDefault())
				.describedAs("Locale in test context")
				.isEqualTo(Locale.ENGLISH);

		assertThat(Constants.LOCALE_ENGLISH)
				.isSameAs(Locale.ENGLISH)
				.isEqualTo(new Locale("en", ""));
	}

	@Test
	public void emptyLocaleBuilderReturnsRootLocale() {
		assertThat(new Locale.Builder().build())
				.isSameAs(Locale.ROOT);
	}
}
