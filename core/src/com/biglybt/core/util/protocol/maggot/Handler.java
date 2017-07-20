/*
 * File    : Handler.java
 * Created : 19-Jan-2004
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

package com.biglybt.core.util.protocol.maggot;

/**
 * @author parg
 *
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import com.biglybt.core.util.Base32;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.protocol.magnet.MagnetConnection2;
import com.biglybt.net.magneturi.MagnetURIHandler;


public class
Handler
	extends URLStreamHandler
{
	@Override
	public URLConnection
	openConnection(URL u)
	{
		return(
			new MagnetConnection2(
				u,
				new MagnetConnection2.MagnetHandler()
				{

					@Override
					public void
					process(
						URL 			maggot,
						OutputStream	os)

						throws IOException
					{
							// convert into magnet lookalike

						String maggot_str = maggot.toExternalForm();

						int pos = maggot_str.indexOf( '?' );

						String rem;

						if ( pos == -1 ){

							rem = "";

						}else{

							rem = "&" + maggot_str.substring( pos+1 );

							maggot_str = maggot_str.substring( 0, pos );
						}

						pos = maggot_str.lastIndexOf( "/" );

						maggot_str = maggot_str.substring( pos+1 );

						String[] bits = maggot_str.split( ":" );

						String btih_str = bits[0];
						String sha1_str	= bits[1];

						String	magnet_str = "magnet:?xt=urn:btih:" + Base32.encode( ByteFormatter.decodeString( btih_str ));

						magnet_str += rem + "&maggot_sha1=" + sha1_str;

						URL magnet = new URL( magnet_str );

						String	get = "/download/" + magnet.toString().substring( 7 ) + " HTTP/1.0\r\n\r\n";

						MagnetURIHandler.getSingleton().process( get, new ByteArrayInputStream(new byte[0]), os );
					}
				}));
	}

	 @Override
	 protected void
	 parseURL(
		URL 		u,
		String 		spec,
		int 		start,
		int 		limit )
	 {
		 	// need to override this as the <ih>:<sha1> format of maggot URIs isn't compatible with the
		 	// usual <host>:<port>.... - turn it all into the host

		 spec = spec.substring( start );

		 while( spec.length() > 0 && "/?".indexOf( spec.charAt(0)) != -1 ){

			 spec = spec.substring(1);
		 }

		 setURL(u, "maggot", spec, -1, spec, null, "", null, null );
	 }
}
