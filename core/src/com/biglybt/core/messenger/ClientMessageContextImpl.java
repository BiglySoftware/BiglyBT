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

package com.biglybt.core.messenger;

import com.biglybt.core.messenger.browser.BrowserMessageDispatcher;
import com.biglybt.core.messenger.browser.listeners.BrowserMessageListener;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.AEDiagnosticsLogger;
import com.biglybt.core.util.Debug;
import com.biglybt.util.ConstantsVuze;

/**
 * @author TuxPaper
 * @created Oct 9, 2006
 *
 */
public abstract class ClientMessageContextImpl
	implements ClientMessageContext
{
	private String id;

	private BrowserMessageDispatcher dispatcher;

	public ClientMessageContextImpl(String id, BrowserMessageDispatcher dispatcher) {
		this.id = id;
		this.dispatcher = dispatcher;
	}

	@Override
	public void addMessageListener(BrowserMessageListener listener) {
		if (dispatcher != null) {
			dispatcher.addListener(listener);
		} else {
			debug("No dispatcher when trying to add MessageListener "
					+ listener.getId() + ";" + Debug.getCompressedStackTrace());
		}
	}

	@Override
	public void debug(String message) {
		AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger("v3.CMsgr");
		diag_logger.log("[" + id + "] " + message);
		if (ConstantsVuze.DIAG_TO_STDOUT) {
			System.out.println("[" + id + "] " + message);
		}
	}

	@Override
	public void debug(String message, Throwable t) {
		AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger("v3.CMsgr");
		diag_logger.log("[" + id + "] " + message);
		diag_logger.log(t);
		if (ConstantsVuze.DIAG_TO_STDOUT) {
			System.err.println("[" + id + "] " + message);
			t.printStackTrace();
		}
	}

	@Override
	public void removeMessageListener(String listenerId) {
		if (dispatcher != null) {
			dispatcher.removeListener(listenerId);
		} else {
			debug("No dispatcher when trying to remove MessageListener "
					+ listenerId + ";" + Debug.getCompressedStackTrace());
		}
	}

	@Override
	public void removeMessageListener(BrowserMessageListener listener) {
		if (dispatcher != null) {
			dispatcher.removeListener(listener);
		} else {
			debug("No dispatcher when trying to remove MessageListener "
					+ listener.getId() + ";" + Debug.getCompressedStackTrace());
		}
	}

	@Override
	public BrowserMessageDispatcher getDispatcher() {
		return dispatcher;
	}

	public String getID() {
		return id;
	}

	@Override
	public void setMessageDispatcher(BrowserMessageDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}
}
