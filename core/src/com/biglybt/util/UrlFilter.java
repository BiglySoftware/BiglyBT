/*
 * Created on Dec 9, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.util;

import java.util.Iterator;

import com.biglybt.core.messenger.PlatformMessenger;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;

/**
 * @author TuxPaper
 * @created Dec 9, 2008
 *
 */
public class UrlFilter
{
	private static UrlFilter instance = null;

	//private String RPC_WHITELIST = "AZMSG%3B[0-9]+%3B.*";

	private String DEFAULT_RPC_WHITELIST = "https?://"
			+ Constants.URL_CLIENT_HOME.replaceAll("\\.", "\\\\.") + ":?[0-9]*/" + ".*";

	private CopyOnWriteList<String>  	listUrlBlacklist = new CopyOnWriteList<>();

	private CopyOnWriteList<String> 	listUrlWhitelist = new CopyOnWriteList<>();

	private AEMonitor mon = new AEMonitor("UrlFilter");

	public static UrlFilter getInstance() {
		synchronized (UrlFilter.class) {
			if (instance == null) {
				instance = new UrlFilter();
			}
			return instance;
		}
	}

	public UrlFilter() {
		addUrlWhitelist(DEFAULT_RPC_WHITELIST);
		addUrlWhitelist("https?://([^.]+.?)?biglybt.com:?[0-9]*/.*");
		addUrlWhitelist("https?://([^.]+.?)?vuze.com:?[0-9]*/.*");
		addUrlWhitelist("https?://192\\.168\\.0\\.*:?[0-9]*/.*");
		addUrlWhitelist("https?://localhost:?[0-9]*/.*");
		// for +1 button
		addUrlWhitelist("https?://plusone\\.google\\.com/.*");
		addUrlWhitelist("https?://clients[0-9]\\.google\\.com/.*");
	}

	public void addUrlWhitelist(String string) {
		addUrlWhitelistSupport( string );

		if ( string.contains( "://localhost" )){

			addUrlWhitelistSupport( string.replace( "://localhost", "://127.0.0.1" ));
		}
	}

	private void addUrlWhitelistSupport(String string) {
		mon.enter();
		try {
			if (!listUrlWhitelist.contains(string)) {
				PlatformMessenger.debug("add whitelist of " + string);
				listUrlWhitelist.add(string);
			} else {
				PlatformMessenger.debug("whitelist already exists: " + string);
			}
		} finally {
			mon.exit();
		}
	}

	public void addUrlBlacklist(String string) {
		mon.enter();
		try {
			if (!listUrlBlacklist.contains(string)) {
				PlatformMessenger.debug("add blacklist of " + string);
				listUrlBlacklist.add(string);
			}
		} finally {
			mon.exit();
		}
	}

	public String[] getUrlWhitelist() {

		return listUrlWhitelist.toArray(new String[0]);
	}

	public boolean
	isWhitelisted(
		String		url )
	{
		Iterator<String> it = listUrlWhitelist.iterator();

		while( it.hasNext()){
			if (url.matches(it.next())) {
				return true;
			}
		}
		return( false );
	}

	public boolean urlCanRPC(String url) {
		return urlCanRPC(url, false );//Constants.isCVSVersion());
	}

	public boolean urlCanRPC(String url,boolean showDebug) {
		if (url == null) {
			Debug.out("URL null and should be blocked");
			return false;
		}

		if (Constants.isCVSVersion() && url.startsWith("file://")) {
			return true;
		}

		if ( isWhitelisted( url )){

			return( true );
		}

		if(showDebug) {
			Debug.out("urlCanRPC: URL '" + url + "' " + " does not match one of the "
					+ listUrlWhitelist.size() + " whitelist entries");
		}
		return false;
	}

	public boolean urlIsBlocked(String url) {
		if (url == null) {
			Debug.out("URL null and should be blocked");
			return true;
		}

		for (Iterator<String> iter = listUrlBlacklist.iterator(); iter.hasNext();) {
			String blackListed = iter.next();
			if (url.matches(blackListed)) {
				Debug.out("URL '" + url + "' " + " is blocked by " + blackListed);
				return true;
			}
		}
		return false;
	}

}
