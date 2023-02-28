/*
 * File    : TrackerWebPageRequestImpl.java
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

package com.biglybt.pifimpl.local.tracker;

/**
 * @author parg
 *
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.server.TRTrackerServerListener2;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.AsyncController;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.web.TrackerWebContext;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;

public class
TrackerWebPageRequestImpl
	implements TrackerWebPageRequest
{
	private Tracker				tracker;
	private TrackerWebContext	context;

	private TRTrackerServerListener2.ExternalRequest	request;


	protected
	TrackerWebPageRequestImpl(
		Tracker										_tracker,
		TrackerWebContext							_context,
		TRTrackerServerListener2.ExternalRequest	_request )
	{
		tracker		= _tracker;
		context		= _context;
		request		= _request;
	}

	@Override
	public Tracker
	getTracker()
	{
		return( tracker );
	}

	@Override
	public TrackerWebContext
	getContext()
	{
		return( context );
	}

	@Override
	public String
	getURL()
	{
		return( request.getURL());
	}

	@Override
	public URL
	getAbsoluteURL()
	{
		return( request.getAbsoluteURL());
	}

	@Override
	public String
	getClientAddress()
	{
		return( AddressUtils.getHostAddress(request.getClientAddress()));
	}

	@Override
	public InetSocketAddress
	getClientAddress2()
	{
		return( request.getClientAddress());
	}

	@Override
	public InetSocketAddress
	getLocalAddress()
	{
		return( request.getLocalAddress());
	}

	@Override
	public String
	getUser()
	{
		return( request.getUser());
	}

	@Override
	public InputStream
	getInputStream()
	{
		return( request.getInputStream());
	}

	protected OutputStream
	getOutputStream()
	{
		return( request.getOutputStream());
	}

	protected boolean
	isActive()
	{
		return( request.isActive());
	}

	protected AsyncController
	getAsyncController()
	{
		return( request.getAsyncController());
	}

	public boolean
	canKeepAlive()
	{
		return( request.canKeepAlive());
	}

	public void
	setKeepAlive(
		boolean	ka )
	{
		request.setKeepAlive( ka );
	}

	@Override
	public String
	getHeader()
	{
		return( request.getHeader());
	}

	@Override
	public Map
	getHeaders()
	{
        Map headers = new HashMap();

        String[] header_parts = request.getHeader().split("\r\n");

        headers.put("status", header_parts[0].trim());

        for (int i = 1;i<header_parts.length;i++) {

        	String[] key_value = header_parts[i].split(":",2);

            headers.put(key_value[0].trim().toLowerCase( MessageText.LOCALE_ENGLISH ), key_value[1].trim());
        }

        return headers;
	}
}
