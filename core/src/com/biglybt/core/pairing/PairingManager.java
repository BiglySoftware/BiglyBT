/*
 * Created on Oct 5, 2009
 * Created by Paul Gardner
 *
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


package com.biglybt.core.pairing;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;

public interface
PairingManager
{
	public static String CONFIG_SECTION_ID = "Pairing";

	public boolean
	isEnabled();

	public boolean
	isSRPEnabled();

	public URL
	getServiceURL();
	
	public URL
	getWebRemoteURL();
	
	public String
	getTunnelServer();
	
	public void
	setGroup(
		String		group );

	public String
	getGroup();

	public List<PairedNode>
	listGroup()

		throws PairingException;

	public List<PairedService>
	lookupServices(
		String		access_code )

		throws PairingException;

	public String
	getAccessCode()

		throws PairingException;

	public String
	peekAccessCode();

	public String
	getReplacementAccessCode()

		throws PairingException;

	public File
	getQRCode();

	public void
	setSRPPassword(
		char[]		password );

	public PairedService
	addService(
		String							sid,
		PairedServiceRequestHandler		handler );

	public PairedService
	getService(
		String		sid );

	public void
	setEnabled(
		boolean enabled );

	public void
	setSRPEnabled(
		boolean enabled );

	public String
	getStatus();

	public String
	getSRPStatus();

	public String
	getLastServerError();

	public boolean
	hasActionOutstanding();

	public PairingTest
	testService(
		String					sid,
		PairingTestListener		listener )

		throws PairingException;

	public boolean
	handleLocalTunnel(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException;

	public void
	recordRequest(
		String		name,
		String		ip,
		boolean		good );

	public void
	addListener(
		PairingManagerListener	l );

	public void
	removeListener(
		PairingManagerListener	l );

}
