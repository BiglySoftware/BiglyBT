package com.biglybt.core.internat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.biglybt.core.util.Constants;
import com.biglybt.testutil.junit5.DefaultTestCoreConfiguration;

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

	@Test
	public void languageCodeLocalizationsAreLinked() {
		Locale[] locales = MessageText.getLocales(false);
		assertThat(locales)
				.describedAs("property files with only language code")
				.contains(new Locale("eu")); //Basque
	}

	@Test
	public void languageCodeWithVariantIsLinked() {
		Locale[] locales = MessageText.getLocales(false);
		assertThat(locales)
				.describedAs("property files with only language code and optional variant")
				.contains(new Locale.Builder().setLanguage("sr").build(), //Serbian
						new Locale.Builder().setLanguage("sr").setScript("Latn").build()); //Serbian latin script
	}

	@Test
	public void mixedTextDirections() {
		MessageText.changeLocale(new Locale("ar", "SA"));

		assertThat(MessageText.getString("iconBar.start.tooltip"))
				.isEqualTo("\u0628\u062F\u0621 \u062A\u0634\u063A\u064A\u0644 torrent(s) \u0627\u0644\u0645\u062D\u062F\u062F");
	}

	@Test
	public void mixedTextDirectionsAndSubstitutions() {
		MessageText.changeLocale(new Locale("ar", "SA"));

		assertThat(MessageText.getString("MainWindow.dialog.exitconfirmation.title"))
				.isEqualTo("\u0625\u0646\u0647\u0627\u0621 BiglyBT");
	}

	/**
	 * Internal string representation is stored in the same order you read the sentence (including arabic)
	 * <blockquote>
	 *   Java SE stores text in memory in logical order, which is the order in which characters and words are read and written.
	 *   The logical order is not necessarily the same as the visual order, which is the order in which the corresponding glyphs are displayed.
	 * </blockquote>
	 * Source: https://docs.oracle.com/javase/tutorial/2d/text/textlayoutbidirectionaltext.html#ordering_text
	 */
	@Test
	public void arabicTextIsStoredTheSameWayYouReadArabic() {
		MessageText.changeLocale(new Locale("ar", "SA"));

		String arabicString = MessageText.getString("MainWindow.dialog.exitconfirmation.title");
		assertThat(arabicString).isEqualTo("\u0625\u0646\u0647\u0627\u0621 BiglyBT");

		//utf16 for simplicity as most arabic characters falls in the extended two bytes range of utf8
		byte[] internalBytes = arabicString.getBytes(StandardCharsets.UTF_16);
		assertThat(internalBytes)
				.inHexadecimal()
				.containsExactly(
						0xFE, 0xFF, //BOM (the utf16 conversion likes to add those two bytes)
						0x06, 0x25,
						0x06, 0x46,
						0x06, 0x47,
						0x06, 0x27,
						0x06, 0x21,
						0x00, (int) ' ', //0x20 (single bytes in utf8)
						0x00, (int) 'B',
						0x00, (int) 'i',
						0x00, (int) 'g',
						0x00, (int) 'l',
						0x00, (int) 'y',
						0x00, (int) 'B',
						0x00, (int) 'T');
	}

	/**
	 * Demonstration of use of line breaks and text flow.
	 *
	 * <blockquote>
	 * <p>
	 * Arabic, Hebrew and a few other languages run the inline direction from right to left.
	 * This is commonly known as RTL.
	 * <p>
	 * Note that the inline direction still runs horizontally.
	 * The block direction runs from top to bottom. And the characters are upright.
	 * </blockquote>
	 *
	 * Source: https://24ways.org/2016/css-writing-modes/
	 * <br>
	 * <br>
	 * Note: Visual text representation is not illustrated in this example.
	 */
	@Test
	public void arabicTextLinesReadsFromTopToBottom() {
		MessageText.changeLocale(new Locale("ar", "SA"));

		String twoArabicLetters = "\u0625\u0646";
		StringBuilder stringBuilder = new StringBuilder(twoArabicLetters);
		stringBuilder.insert(1, '\n'); //inserting newline
		String twoLinesOfArabicLetters = stringBuilder.toString();

		assertThat(twoLinesOfArabicLetters)
				.isEqualTo("\u0625\n\u0646");

		assertThat(twoLinesOfArabicLetters.replace("\n", ""))
				.isEqualTo(twoArabicLetters);
	}

	@Test
	public void defaultKeyIsRegistered() {
		assertThat(MessageText.getString("base.product.name"))
				.isEqualTo(Constants.APP_NAME);
	}

	@ExtendWith(DefaultTestCoreConfiguration.class)
	static class SubstitutionAlgorithmTests
	{
		static final String APP_NAME = Constants.APP_NAME;

		@Test
		public void singleSubstitution() {
			String expandableString = "{base.product.name}";
			assertThat(MessageText.expandValue(expandableString))
					.isEqualTo(APP_NAME);
		}

		@Test
		public void multipleSubstitutions() {
			String expandableString = "{base.product.name}}{{base.product.name}";
			assertThat(MessageText.expandValue(expandableString))
					.isEqualTo(APP_NAME + "}{" + APP_NAME);
		}

		@Test
		public void unknownSubstitutionKeyIsLeftUnaltered() {
			String expandableString = "{no.key.defined}";
			assertThat(MessageText.expandValue(expandableString))
					.isEqualTo("{no.key.defined}");
		}



	}



}
