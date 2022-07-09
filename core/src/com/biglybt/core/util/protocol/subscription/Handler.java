/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

package com.biglybt.core.util.protocol.subscription;



import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import com.biglybt.core.util.Constants;



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
	{
		Connection(
			URL 	u )
		{
			super(u);

			url		= u;
		}
		
		@Override
		public void 
		connect() 
			throws IOException
		{
		}
		
		@Override
		public InputStream
		getInputStream()

			throws IOException
		{
			String result = 
				"<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
				"<rss version=\"2.0\" " + Constants.XMLNS_VUZE + ">" +
				"<channel>" +
				"<title>Template</title>" +
				"</channel>" +
				"</rss>";
			
			return( new ByteArrayInputStream( result.getBytes( Constants.UTF_8 )));
		}

		@Override
		public int
		getResponseCode()
		{
			return( HTTP_OK );
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
