package com.biglybt.core.internat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.biglybt.testutil.junit5.DefaultTestCoreConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@ExtendWith(DefaultTestCoreConfiguration.class)
public class MessageTextTest
{

	@Test
	public void canChangeLocale() {
		MessageText.changeLocale(Locale.UK);
		assertThat(MessageText.getCurrentLocale()).isSameAs(Locale.UK);
	}

	@Test
	public void defaultLocaleForTestIsSet() {
		assertThat(MessageText.getCurrentLocale())
				.isSameAs(Locale.getDefault())
				.isSameAs(Locale.ENGLISH);
	}

	@Test
	public void localizedTextChangesWhenChangingLocale() {
		assertThat(tuple(
				MessageText.getCurrentLocale(), MessageText.getString("ConfigView.label.startminimized"))
		).isEqualTo(tuple(
				MessageText.LOCALE_ENGLISH, "Start minimized"));

		MessageText.changeLocale(Locale.UK);

		assertThat(tuple(
				MessageText.getCurrentLocale(), MessageText.getString("ConfigView.label.startminimized"))
		).isEqualTo(tuple(
				Locale.UK, "Start minimised"));
	}

	@Test
	public void defaultLocaleStringReturnsTextForRootLocale() {
		MessageText.changeLocale(Locale.UK);

		assertThat(MessageText.getString("ConfigView.label.startminimized"))
				.describedAs("Negative test should return localized string")
				.isEqualTo("Start minimised");

		assertThat(MessageText.getDefaultLocaleString("ConfigView.label.startminimized"))
				.isEqualTo("Start minimized");
	}

}
