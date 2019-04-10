/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui;


/**
 * @author TuxPaper
 * @created Mar 18, 2007
 *
 */
public interface UIFunctionsUserPrompter
{
	String ICON_WARNING = "warning";
	String ICON_INFO = "info";
	String ICON_ERROR = "error";

	/**
	 * Returns the number milliseconds the prompt will wait around until it auto
	 * closes.  Timer starts after the user is prompted (when {@link #open()} is
	 * called), and typically does not decrease while the user is viewing the
	 * prompt (certain implementations may operate differently)
	 *
	 * @return MS before prompt auto-closes, 0 for no auto-close
	 *
	 * @since 3.0.0.9
	 */
	int getAutoCloseInMS();

	/**
	 * Returns the HTML that will be displayed along with the prompt
	 * <p>
	 * TODO: Create a boolean canHandleHTML()
	 *
	 * @return
	 *
	 * @since 3.0.0.9
	 */
	String getHtml();

	/**
	 * Retrieves the Remember ID associated with this prompt
	 *
	 * @return Remember ID
	 *
	 * @since 3.0.0.9
	 */
	String getRememberID();

	/**
	 * Retrieves the text to be displayed by the "Remember this Action" checkbox
	 *
	 * @return Remember text
	 *
	 * @since 3.0.0.9
	 */
	String getRememberText();

	/**
	 * Opens the prompt.  returns when user has chosen an action, or auto-close
	 *
	 * @since 3.0.0.9
	 */
	void open(UserPrompterResultListener l);

	int waitUntilClosed();

	/**
	 * Sets the # of milliseconds before auto closing.
	 * Timer starts after the user is prompted (when {@link #open()} is
	 * called), and typically does not decrease while the user is viewing the
	 * prompt (certain implementations may operate differently)
	 * @param autoCloseInMS
	 *
	 * @since 3.0.0.9
	 */
	void setAutoCloseInMS(int autoCloseInMS);

	/**
	 * @param html
	 *
	 * @since 3.0.0.9
	 */
	void setHtml(String html);

	/**
	 * @param rememberID
	 * @param rememberByDefault
	 *
	 * @since 4.2.0.9
	 */
	void setRemember(String rememberID, boolean rememberByDefault, String rememberText);

	/**
	 * @param rememberText
	 *
	 * @since 3.0.0.9
	 */
	void setRememberText(String rememberText);

	/**
	 * @since 5601
	 * @param button
	 */
	void setRememberOnlyIfButton( int button );

	/**
	 * @param url
	 *
	 * @since 3.0.0.9
	 */
	void setUrl(String url);

	/**
	 * Determines if the prompt was auto closed after {@link #open()} was
	 * called, or if the user chose an option.
	 *
	 * @return true: auto-closed after timeout<br>
	 *         false: user chose an option
	 *
	 * @since 3.0.0.9
	 */
	boolean isAutoClosed();

	/**
	 * @since 3.0.4.3
	 * @param resource image repository resource name (e.g. "error", "warning", "info")
	 */

	void setIconResource( String resource );

	void setRelatedObjects(Object[] relatedObjects);

	/**
	 * @param relatedObject
	 *
	 * @since 3.0.0.9
	 */
	void setRelatedObject(Object relatedObject);

	/**
	 * Prevent more than one dialog of instanceID from showing up at once
	 *
	 * @param instanceID
	 */
	void setOneInstanceOf(String instanceID);
}
