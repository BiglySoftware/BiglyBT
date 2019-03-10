package com.biglybt.testutil.junit5;

import java.util.Locale;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.SystemProperties;

/**
 * Default setup for running JUnit5 tests in BiglyBT core.
 * The following enables this extension:
 *
 * <pre>
 * {@code
 *   @ExtendWith(DefaultTestCoreConfiguration.class)
 *   public class MyTestTest
 *   {
 *     ...
 *   }</pre>
 *
 */
public class DefaultTestCoreConfiguration
		implements BeforeAllCallback, BeforeEachCallback
{

	@Override
	public void beforeAll(ExtensionContext context)
			throws Exception {

		if (!CoreFactory.isCoreAvailable()) {
			CoreFactory.create();
		}
	}

	@Override
	public void beforeEach(ExtensionContext context)
			throws Exception {

		Locale.setDefault(Locale.ENGLISH);
		MessageText.changeLocale(Locale.ENGLISH);

		//avoids errors on System.exit() in SecurityManagerImpl
		System.setProperty(SystemProperties.SYSPROP_SECURITY_MANAGER_PERMITEXIT, "true");
	}

}
