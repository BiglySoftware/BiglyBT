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
package com.biglybt.ui.swt.browser.msg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

import com.biglybt.core.messenger.ClientMessageContext;
import com.biglybt.core.messenger.browser.BrowserMessage;
import com.biglybt.core.messenger.browser.BrowserMessageDispatcher;
import com.biglybt.core.messenger.browser.listeners.BrowserMessageListener;

import com.biglybt.ui.swt.BrowserWrapper;

import com.biglybt.util.JSONUtils;
import com.biglybt.util.UrlFilter;

/**
 * Dispatches messages to listeners registered with unique IDs.
 */
public class MessageDispatcherSWT
	implements BrowserMessageDispatcher
{
	private ClientMessageContext context;

	private Map<String, BrowserMessageListener> listeners = new HashMap<>();

	private BrowserWrapper browser;

	private BrowserWrapper.BrowserFunction browserFunction;

	/**
	 * Registers itself as a listener to receive sequence number reset message.
	 */
	public MessageDispatcherSWT(ClientMessageContext context) {
		this.context = context;
	}

	public void registerBrowser(final BrowserWrapper browser) {
		this.browser = browser;

		try {
	  		browserFunction = browser.addBrowserFunction(
	  			"sendMessageToAZ",
	  			new BrowserWrapper.BrowserFunction()
	  			{
		  			@Override
					  public Object function(Object[] args) {
		  				if (args == null) {
		  					context.debug("sendMessageToAZ: arguments null on " + browser.getUrl());
		  					return null;
		  				}
		  				if (args.length != 3 && args.length != 2) {
		  					context.debug("sendMessageToAZ: # arguments not 2 or 3 (" + args.length + ") on " + browser.getUrl());
		  					return null;
		  				}

		  				if (!(args[0] instanceof String)) {
		  					context.debug("sendMessageToAZ: Param 1 not String");
		  					return null;
		  				}
		  				if (!(args[1] instanceof String)) {
		  					context.debug("sendMessageToAZ: Param 2 not String");
		  					return null;
		  				}
		  				Map<?, ?> params = Collections.EMPTY_MAP;
		  				if (args.length == 3) {
		    				if (!(args[2] instanceof String)) {
		    					context.debug("sendMessageToAZ: Param 3 not String");
		    					return null;
		    				}

		    				params = JSONUtils.decodeJSON((String)args[2]);
		  				}


		  				BrowserMessage message = new BrowserMessage((String) args[0], (String) args[1], params);
		  				message.setReferer(browser.getUrl());
		  				dispatch(message);
		  				return null;
		  			}
		  		});

		} catch (Throwable t) {
			Debug.out(t);
		}
	}

	/**
	 * Detaches this dispatcher from the given {@link Browser}.
	 * This dispatcher listens for dispose events from the browser
	 * and calls this method in response.
	 *
	 * @param browser {@link Browser} which will no longer send messages
	 */
	public void deregisterBrowser(BrowserWrapper browser) {
		if (browserFunction != null && !browserFunction.isDisposed()) {
			browserFunction.dispose();
		}
	}

	/**
	 * Registers the given listener for the given ID.
	 *
	 * @param id unique identifier used when dispatching messages
	 * @param listener receives messages targeted at the given ID
	 *
	 * @throws IllegalStateException
	 *              if another listener is already registered under the same ID
	 */
	@Override
	public synchronized void addListener(BrowserMessageListener listener) {
		String id = listener.getId();
		BrowserMessageListener registered = listeners.get(id);
		if (registered != null) {
			if (registered != listener) {
				throw new IllegalStateException("Listener "
						+ registered.getClass().getName() + " already registered for ID "
						+ id);
			}
		} else {
			listener.setContext(context);
			listeners.put(id, listener);
		}
	}

	/**
	 * Deregisters the listener with the given ID.
	 *
	 * @param id unique identifier of the listener to be removed
	 */
	@Override
	public synchronized void removeListener(BrowserMessageListener listener) {
		removeListener(listener.getId());
	}

	/**
	 * Deregisters the listener with the given ID.
	 *
	 * @param id unique identifier of the listener to be removed
	 */
	@Override
	public synchronized void removeListener(String id) {
		BrowserMessageListener removed;

		synchronized( this ){
			removed = listeners.remove(id);
		}

		if (removed == null) {
			//            throw new IllegalStateException("No listener is registered for ID " + id);
		} else {
			removed.setContext(null);
		}
	}

	// @see com.biglybt.ui.swt.browser.msg.MessageDispatcher#getListener(java.lang.String)
	@Override
	public BrowserMessageListener getListener(String id) {
		synchronized( this ){
			return listeners.get(id);
		}
	}

	// @see com.biglybt.ui.swt.browser.msg.MessageDispatcher#dispatch(com.biglybt.core.messenger.browser.BrowserMessage)
	@Override
	public void dispatch(final BrowserMessage message) {
		if (message == null) {
			return;
		}
		String referer = message.getReferer();
		if (referer != null && !UrlFilter.getInstance().urlCanRPC(referer)) {
			context.debug("blocked " + message + "\n  " + referer);
			return;
		}


		context.debug("Received " + message);
		if (browser != null && !browser.isDisposed() && Utils.isThisThreadSWT()) {
			context.debug("   browser url: " + browser.getUrl());
		}

		// handle messages for dispatcher and context regardless of sequence number
		String listenerId = message.getListenerId();
		if ("lightbox-browser".equals(listenerId)) {
			listenerId = "display";
		}

		final BrowserMessageListener listener = getListener(listenerId);
		if (listener == null) {
			context.debug("No listener registered with ID " + listenerId);
		} else {
			new AEThread2("dispatch for " + listenerId, true) {
				@Override
				public void run() {
					listener.handleMessage(message);
					message.complete(true, true, null);
				}
			}.start();
		}
	}
}
