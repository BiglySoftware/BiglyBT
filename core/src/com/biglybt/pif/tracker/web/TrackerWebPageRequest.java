/*
 * File    : TrackerWebPageRequest.java
 * Created : 08-Dec-2003
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

package com.biglybt.pif.tracker.web;

/**
 * @author parg
 *
 */

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;

import com.biglybt.pif.tracker.Tracker;

public interface
TrackerWebPageRequest
{
	public Tracker
	getTracker();

	public String
	getClientAddress();

	public InetSocketAddress
	getClientAddress2();

	public InetSocketAddress
	getLocalAddress();

	public String
	getUser();

		/**
		 * This gives the relative URL of the request (e.g. /fred.html)
		 * @return
		 */

	public String
	getURL();

	public String
	getHeader();

		/**
		 * Returns a map containing the separate headers. Keys are lowercase
		 * @return
		 * @since 2.3.0.7
		 */

	public Map
	getHeaders();

	public InputStream
	getInputStream();

		/**
		 * Absolute URL including protocol and port e.g. https://a.b.c:1235/fred.html
		 * @return
		 */

	public URL
	getAbsoluteURL();

	public TrackerWebContext
	getContext();
}
