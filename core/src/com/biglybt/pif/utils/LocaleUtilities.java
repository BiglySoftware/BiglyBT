/*
 * File    : LocaleUtilities.java
 * Created : 30-Mar-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pif.utils;

import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * @author parg
 *
 */
public interface
LocaleUtilities
{
	/**
	 * Allows programatic registration of plugin messages, as opposed to using the
	 * plugin.langfile property in plugin.properties
	 * If you message base file is, say, a.b.c.Messages.properties, pass a.b.c.Messages
	 * @param resource_bundle_prefix
	 */

	public void
	integrateLocalisedMessageBundle(
		String		resource_bundle_prefix );

	/**
	 * Allows plugins to dynamically pass in a resource bundle to be used
	 * for message text translation.
	 *
	 * @since 3.0.2.3
	 */
	public void integrateLocalisedMessageBundle(ResourceBundle rb);

	/**
	 * Allows plugins to dynamically pass in a properties object to be used
	 * for message text translation.
	 *
	 * @since 3.0.2.3
	 */
	public void integrateLocalisedMessageBundle(Properties p);

	public String
	getLocalisedMessageText(
		String		key );

	public String
	getLocalisedMessageText(
		String		key,
		String[]	params );

	/**
	 * Returns <tt>true</tt> if there exists a message string
	 * with the given key name.
	 *
	 * @since 3.0.5.3
	 */
	public boolean hasLocalisedMessageText(String key);

	/**
	 * An alias for {@link #getLocalisedMessageText(String)} which returns
	 * <tt>null</tt> if there is no message string definition for the given
	 * key.
	 *
	 * @since 3.0.5.3
	 */
	public String localise(String key);

	public LocaleDecoder[]
	getDecoders();

	public void
	addListener(
		LocaleListener		l );

	public void
	removeListener(
		LocaleListener		l );

	/**
	 * Returns the current locale being used.
	 *
	 * @since 3.0.0.9
	 */
	public Locale getCurrentLocale();
}
