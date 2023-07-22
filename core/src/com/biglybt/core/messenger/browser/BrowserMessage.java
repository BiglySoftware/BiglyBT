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
package com.biglybt.core.messenger.browser;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.messenger.browser.listeners.BrowserMessageListener;
import com.biglybt.core.messenger.browser.listeners.MessageCompletionListener;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.AEDiagnosticsLogger;
import com.biglybt.core.util.Debug;
import com.biglybt.util.ConstantsVuze;

/**
 * Holds a message being dispatched to a {@link BrowserMessageListener}.
 *
 * @author dharkness
 * @created Jul 18, 2006
 */
public class BrowserMessage
{
	/** All messages must start with this prefix. */
	public static final String MESSAGE_PREFIX = "AZMSG";

	/** Separates prefix and listener ID from rest of message. */
	public static final String MESSAGE_DELIM = ";";

	public static String MESSAGE_DELIM_ENCODED;

	/** There were no parameters passed with the message. */
	public static final int NO_PARAM = 0;

	/** Parameters were an encoded JSONObject. */
	public static final int OBJECT_PARAM = 1;

	/** Parameters were an encoded JSONArray. */
	public static final int ARRAY_PARAM = 2;

	static {
		try {
			MESSAGE_DELIM_ENCODED = URLEncoder.encode(";", "UTF-8");
		} catch (UnsupportedEncodingException e) {
			MESSAGE_DELIM_ENCODED = MESSAGE_DELIM;
		}
	}

	private String listenerId;

	private String operationId;

	private Map decodedParams;

	private ArrayList completionListeners = new ArrayList();

	private boolean completed;

	private boolean completeDelayed;

	private String referer;


	public BrowserMessage(String listenerId, String operationId, Map<?, ?> params) {
		this.listenerId = listenerId;
		this.operationId = operationId;
		decodedParams = params;
	}

	public void addCompletionListener(MessageCompletionListener l) {
		completionListeners.add(l);
	}

	/**
	 * Sets the message complete and fires of the listeners who are waiting
	 * for a response.
	 *
	 * @param bOnlyNonDelayed Only mark complete if this message does not have a delayed response
	 * @param success Success level of the message
	 * @param data Any data the message results wants to send
	 */
	public void complete(boolean bOnlyNonDelayed, boolean success, Object data) {
		//System.out.println("complete called with " + bOnlyNonDelayed);
		if (completed || (bOnlyNonDelayed && completeDelayed)) {
			//System.out.println("exit early" + completed);
			return;
		}
		triggerCompletionListeners(success, data);
		completed = true;
	}

	public void debug(String message) {
		debug(message, null);
	}

	public void debug(String message, Throwable t) {
		try {
			AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger("v3.CMsgr");
			String out = "[" + getListenerId() + ":" + getOperationId() + "] "
					+ message;
			diag_logger.log(out);
			if (t != null) {
				diag_logger.log(t);
			}
			if (ConstantsVuze.DIAG_TO_STDOUT) {
				System.out.println(out);
				if (t != null) {
					t.printStackTrace();
				}
			}
		} catch (Throwable t2) {
			Debug.out(t2);
		}
	}

	public Map getDecodedMap() {
		return decodedParams == null ? Collections.EMPTY_MAP : decodedParams;
	}

	public String getListenerId() {
		return listenerId;
	}

	public String getOperationId() {
		return operationId;
	}

	public String getReferer() {
		return referer;
	}

	public void removeCompletionListener(MessageCompletionListener l) {
		completionListeners.remove(l);
	}

	public void setCompleteDelayed(boolean bCompleteDelayed) {
		completeDelayed = bCompleteDelayed;
	}

	public void setReferer(String referer) {
		this.referer = referer;
	}

	public String toString() {
		return listenerId + "." + operationId + "("	+ decodedParams + ")";
	}

	private void triggerCompletionListeners(boolean success, Object data) {
		for (Iterator iterator = completionListeners.iterator(); iterator.hasNext();) {
			MessageCompletionListener l = (MessageCompletionListener) iterator.next();
			try {
				l.completed(success, data);
			} catch (Throwable e) {
				Debug.out(e);
			}
		}
	}
}
