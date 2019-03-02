package com.biglybt.ui.none.internat;

import com.biglybt.core.util.FileUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Using keys in the default (english) language, fills non-default 'none' messagebundle packages.
 * <p>
 * Created by TuxPaper on 1/30/18.
 */

public class BuildMessageBundleNone {
	private static final String zeros = "000";

	public static final boolean doUTF8 = false;

	public static String toEscape(String str) {

		StringBuffer sb = new StringBuffer();
		char[] charArr = str.toCharArray();
		for (int i = 0; i < charArr.length; i++) {
			if (charArr[i] == '\n')
				sb.append("\\n");
			else if (charArr[i] == '\t')
				sb.append("\\t");
			else if (charArr[i] >= 0 && charArr[i] < 128 && charArr[i] != '\\')
				sb.append(charArr[i]);
			else {
				if (doUTF8) {
					sb.append(charArr[i]);
				} else {
					sb.append(toEscape(charArr[i]));
				}
			}
		}
		return sb.toString();
	}

	public static String toEscape(char c) {
		int n = (int) c;
		String body = Integer.toHexString(n);
		return ("\\u" + zeros.substring(0, 4 - body.length()) + body);
	}

	private static final Pattern PAT_PARAM_ALPHA = Pattern.compile("\\{([^0-9].+?)\\}");

	public static String expandValue(String value, Properties properties) {
		// Replace {*} with a lookup of *
		if (value != null && value.indexOf('}') > 0) {
			Matcher matcher = PAT_PARAM_ALPHA.matcher(value);
			while (matcher.find()) {
				String key = matcher.group(1);
				try {
					String text = properties.getProperty(key);
					if (text == null) {
						return null;
					}

					if (text != null) {
						value = value.replaceAll("\\Q{" + key + "}\\E", text);
					}
				} catch (MissingResourceException e) {
					// ignore error
				}
			}
		}
		return value;
	}

	public static void main(String[] args) throws IOException, URISyntaxException {
		File dirRoot = new File("").getAbsoluteFile();

		File dirNone = new File(dirRoot, "uis/src/com/biglybt/ui/none/internat");
		System.out.println(dirNone);
		System.out.println(dirRoot);
		File dirFullMB = new File(dirRoot, "core/src/com/biglybt/internat");
		System.out.println(dirFullMB);

		File fileNoneDefault = new File(dirNone, "MessagesBundle.properties");
		File fileFullDefault = new File(dirFullMB, "MessagesBundle.properties");

		FileInputStream fisFullDefault = new FileInputStream(fileFullDefault);
		Properties defaultFullProperties = new Properties();
		defaultFullProperties.load(fisFullDefault);
		fisFullDefault.close();

		String s = FileUtil.readFileAsString(fileNoneDefault, -1);
		String[] lines = s.split("\r?\n");
		s = "";
		File[] files = dirFullMB.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(".properties");
			}
		});

		for (File file : files) {

			FileInputStream fisFullMB = new FileInputStream(file);
			Properties fullCurrentProperties = new Properties();
			fullCurrentProperties.load(fisFullMB);
			fisFullMB.close();

			File fileOut = new File(dirNone, file.getName());
			boolean isCurrentFileDefaultLang = !file.getName().contains("_");
			System.out.println("Process " + file.getName() + " to " + fileOut);

			BufferedWriter bw = new BufferedWriter(new FileWriter(fileOut));

			for (String line : lines) {
				String[] key_val = line.split("=", 2);

				if (key_val.length != 2 || line.startsWith("#") || line.length() == 0) {
					bw.write(line);
					bw.newLine();
				} else {
					String fullCurrentVal = fullCurrentProperties.getProperty(key_val[0]);
					if (fullCurrentVal == null) {
						// no key in the full messagebundle for this language, maybe the
						// default language's value expands to another key?
						String defaultFullVal = defaultFullProperties.getProperty(key_val[0]);
						if (defaultFullVal == null || !defaultFullVal.contains("}")) {
							bw.write(isCurrentFileDefaultLang ? line : "#" + line);
						} else {
							String expandedValue = expandValue(defaultFullVal, fullCurrentProperties);
							if (expandedValue == null) {
								bw.write("# No expansion for " + defaultFullVal);
								bw.newLine();
								bw.write(isCurrentFileDefaultLang ? line : "#" + line);
							} else {
								bw.write(key_val[0] + "=" + toEscape(expandedValue));
							}
						}
					} else {
						String expandedValue = expandValue(fullCurrentVal, fullCurrentProperties);
						if (expandedValue == null) {
							bw.write("# No expansion for " + fullCurrentVal);
							bw.newLine();
							bw.write(isCurrentFileDefaultLang ? line : "#" + line);
						} else {
							bw.write(key_val[0] + "=" + toEscape(expandedValue));
						}
					}
					bw.newLine();
				}
			}
			bw.close();
		}

	}

}
