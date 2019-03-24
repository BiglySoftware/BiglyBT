/*
 * File    : ExternalIPCheckerServiceDiscoveryVIP.java
 * Created : 10-Nov-2003
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

package com.biglybt.core.ipchecker.extipchecker.impl;

import java.net.URL;

import com.biglybt.core.internat.MessageText;

/**
 * @author parg
 *
 */
public class
ExternalIPCheckerServiceDiscoveryVIP
	extends ExternalIPCheckerServiceSimple
{
	protected static final URL CHECKER_URL = url("http://ip.discoveryvip.com/ip.asp");

	protected static final String SERVICE_NAME = "Discoveryvip";

	protected static final String SERVICE_URL = "http://ip.discoveryvip.com/";

	ExternalIPCheckerServiceDiscoveryVIP()
	{
		super(CHECKER_URL, SERVICE_NAME, SERVICE_URL, MessageText.getStringProvider(
				"IPChecker.external.service.discoveryvip.description"));
	}
}
