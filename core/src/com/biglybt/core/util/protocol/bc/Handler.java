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

package com.biglybt.core.util.protocol.bc;

/**
 * @author parg
 *
 */

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;

public class
Handler
	extends URLStreamHandler
{
	@Override
	public URLConnection
	openConnection(URL u)
	{
		URL magnet_url;

		try{
			String str = UrlUtils.parseTextForMagnets( u.toExternalForm());

			if ( str == null ){

				Debug.out( "Failed to transform bc url '" + u + "'" );

				return( null );
			}else{

				magnet_url = new URL( str );
			}
		}catch( Throwable e ){

			Debug.out( "Failed to transform bc url '" + u + "'", e );

			return( null );
		}

			//	System.out.println( "Transformed " + u + " -> " + magnet_url );

		try{
			return( magnet_url.openConnection());

		}catch( MalformedURLException e ){

			Debug.printStackTrace(e);

			return( null );

		}catch( IOException  e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

}
