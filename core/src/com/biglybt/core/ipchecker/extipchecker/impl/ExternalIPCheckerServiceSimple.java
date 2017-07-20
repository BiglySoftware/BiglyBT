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

/**
 * @author parg
 *
 */
public class
ExternalIPCheckerServiceSimple
	extends ExternalIPCheckerServiceImpl
{
	protected final String	url;

	protected
	ExternalIPCheckerServiceSimple(
		String		_key,
		String		_url )
	{
		super( _key );

		url		= _url;
	}

	@Override
	public boolean
	supportsCheck()
	{
		return( true  );
	}

	@Override
	public void
	initiateCheck(
		long		timeout )
	{
		super.initiateCheck( timeout );
	}

	@Override
	protected void
	initiateCheckSupport()
	{
		reportProgress( "loadingwebpage", url );

		String	page = loadPage( url );

		if ( page != null ){

			reportProgress( "analysingresponse" );

			String	IP = extractIPAddress( page );

			if ( IP != null ){

				reportProgress( "addressextracted", IP );

				informSuccess( IP );
			}
		}
	}
}
