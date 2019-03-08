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

package com.biglybt.core.util.protocol.chat;

/**
 * @author parg
 *
 */

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Locale;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.core.util.protocol.URLConnectionExt;


public class
Handler
	extends URLStreamHandler
{
	@Override
	public URLConnection
	openConnection(
		URL 		u )

		throws IOException
	{
		return( new Connection( u ));
	}

	@Override
	public URLConnection
	openConnection(
		URL 		u,
		Proxy		proxy )

		throws IOException
	{
		return( new Connection( u ));
	}
	
	private class
	Connection
		extends HttpURLConnection
		implements URLConnectionExt
	{
		Connection(
			URL 	u )
		{
			super(u);

			url		= u;
		}
		
		@Override
		public String 
		getFriendlyName()
		{
			String url_str = url.toExternalForm();
			
			String lc_url_str = url_str.toLowerCase( Locale.US );
			
			if ( lc_url_str.startsWith( "chat:" )){
				
				boolean anon = lc_url_str.startsWith( "chat:anon:" );
				
				String rem = url_str.substring( anon?10:5 );
				
				if ( rem.startsWith( "?" )){
					
					rem = rem.substring( 1 );
				}
				
				rem = UrlUtils.decode( rem );
					
				int pos = rem.indexOf( '[' );
					
				if ( pos != -1 ){
						
					rem = rem.substring( 0,  pos );
				}
					
					
				return( MessageText.getString( anon?"label.anon.chat":"label.public.chat") + " - " + rem );
			}
			
			return( url_str );
		}
		
		@Override
		public void 
		connect() 
			throws IOException
		{
			throw( new IOException( "chat: URIs can't be used directly" ));
		}
		
		@Override
		public void 
		disconnect()
		{
		}
		
		@Override
		public boolean 
		usingProxy()
		{
			return false;
		}		
	}
}
