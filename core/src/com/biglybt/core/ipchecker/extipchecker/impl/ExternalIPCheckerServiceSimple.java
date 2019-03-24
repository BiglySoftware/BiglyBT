/*
 * File    : ExternalIPCheckerServiceSimple.java
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

import com.biglybt.core.internat.StringSupplier;

/**
 * @author parg
 *
 */
public class
ExternalIPCheckerServiceSimple
	extends ExternalIPCheckerServiceImpl
{
	protected final URL url;

	protected
	ExternalIPCheckerServiceSimple(URL checkerUrl, String serviceName, String serviceUrl, StringSupplier description) {
		super(serviceName, serviceUrl, description);
		url = checkerUrl;
	}

	@Override
	public boolean
	supportsCheck()
	{
		return( true  );
	}

	@Override
	protected void
	initiateCheckSupport()
	{
		reportProgress( "IPChecker.external.loadingwebpage", url);

		String	page = loadPage( url );

		if ( page != null ){

			reportProgress( "IPChecker.external.analysingresponse" );

			String	IP = extractIPAddress( page );

			if ( IP != null ){

				reportProgress( "IPChecker.external.addressextracted", IP );

				informSuccess( IP );
			}
		}
	}
}
