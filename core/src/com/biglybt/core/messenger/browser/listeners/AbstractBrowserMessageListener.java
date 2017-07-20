/*
 * Created on Jun 29, 2006 10:16:26 PM
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.core.messenger.browser.listeners;

import com.biglybt.core.messenger.ClientMessageContext;
import com.biglybt.core.messenger.browser.BrowserMessage;
import com.biglybt.core.messenger.browser.BrowserMessageDispatcher;

/**
 * Accepts and handles messages dispatched from {@link BrowserMessageDispatcher}.
 * Subclasses should use the message's operation ID and parameters to perform
 * the requested operation.
 *
 * @author dharkness
 * @created Jul 18, 2006
 */
public abstract class AbstractBrowserMessageListener
	implements BrowserMessageListener
{
	protected ClientMessageContext context = null;

	private String id;

	/**
	 * Stores the given context for accessing the browser and its services.
	 *
	 * @param context used to access the browser
	 */
	public AbstractBrowserMessageListener(String id) {
		this.id = id;
	}

	/**
	 * Displays a debug message tagged with the listener ID.
	 *
	 * @param message sent to the debug log
	 */
	protected void debug(String message) {
		context.debug("[" + id + "] " + message);
	}

	/**
	 * Displays a debug message and exception tagged with the listener ID.
	 *
	 * @param message sent to the debug log
	 * @param t exception to log with message
	 */
	public void debug(String message, Throwable t) {
		context.debug("[" + id + "] " + message, t);
	}

	/**
	 * Returns the context for this listener.
	 *
	 * @return listener's context
	 */
	@Override
	public ClientMessageContext getContext() {
		return context;
	}

	/**
	 * Returns the unique ID for this listener.
	 *
	 * @return listener's unique ID
	 */
	@Override
	public String getId() {
		return id;
	}

	/**
	 * Handles the given message, usually by parsing the parameters
	 * and calling the appropriate operation.
	 *
	 * @param message holds all message information
	 */
	@Override
	public abstract void handleMessage(BrowserMessage message);

	/**
	 * Sets the context for this listener. Called by its dispatcher when attached.
	 *
	 * @param context the new context for this listener
	 */
	@Override
	public void setContext(ClientMessageContext context) {
		if (this.context == null) {
			this.context = context;
		}
	}
}
