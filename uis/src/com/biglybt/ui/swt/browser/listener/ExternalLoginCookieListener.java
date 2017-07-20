/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.browser.listener;

import java.net.URLDecoder;


import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.browser.StatusTextListener;

import com.biglybt.ui.swt.BrowserWrapper;
import com.biglybt.ui.swt.browser.CookiesListener;

public class ExternalLoginCookieListener
implements StatusTextListener,LocationListener,ProgressListener
{

	private static final String AZCOOKIEMSG = "AZCOOKIEMSG;";

	private CookiesListener listener;

	private BrowserWrapper browser;

	private final static String getCookiesCode =
		//"{" +
		"try {" +
		"var cookies = encodeURIComponent(document.cookie);" +
		"window.status = '" + AZCOOKIEMSG + "' + cookies;" +
		"//alert(window.status);\n" +
		"window.status = '';" +
		"} catch(e) {" +
		"}" ;
		//"}";

	public ExternalLoginCookieListener(CookiesListener _listener,BrowserWrapper browser) {
		this.listener = _listener;
		this.browser = browser;
		browser.addStatusTextListener(this);
	}


	@Override
	public void changed(StatusTextEvent event) {
		if(event.text.startsWith(AZCOOKIEMSG)) {
			String uriEncodedCookies =event.text.substring(AZCOOKIEMSG.length());
			try {
				String cookies = URLDecoder.decode(uriEncodedCookies, "UTF-8");

				if(listener != null) {
					listener.cookiesFound(cookies);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void getCookies() {
		if(browser != null) {
			browser.execute(getCookiesCode);
		}
	}

	public void stopListening() {
		browser.removeStatusTextListener(this);
	}

	public void hookOnPageLoaded() {
		browser.addProgressListener(this);
	}

	public void hookOnPageChanged() {
		browser.addLocationListener(this);
	}

	public void hook() {
		hookOnPageChanged();
		hookOnPageLoaded();
	}

	public void unHook() {

	}

	@Override
	public void changed(ProgressEvent arg0) {

	}

	@Override
	public void completed(ProgressEvent arg0) {
		getCookies();
	}

	@Override
	public void changed(LocationEvent arg0) {
		getCookies();
	}

	@Override
	public void changing(LocationEvent arg0) {

	}

}
