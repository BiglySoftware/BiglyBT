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
public interface BrowserMessageListener
{
	/**
	 * Returns the context for this listener.
	 *
	 * @return listener's context
	 */
	public ClientMessageContext getContext();

	/**
	 * Returns the unique ID for this listener.
	 *
	 * @return listener's unique ID
	 */
	public String getId();

	/**
	 * Handles the given message, usually by parsing the parameters
	 * and calling the appropriate operation.
	 *
	 * @param message holds all message information
	 */
	public void handleMessage(BrowserMessage message);

	/**
	 * Sets the context for this listener. Called by its dispatcher when attached.
	 *
	 * @param context the new context for this listener
	 */
	public void setContext(ClientMessageContext context);
}
