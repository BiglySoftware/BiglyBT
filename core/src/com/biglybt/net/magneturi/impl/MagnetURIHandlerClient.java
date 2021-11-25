/*
 * Created on 11-Sep-2006
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.net.magneturi.impl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class
MagnetURIHandlerClient
{
	protected static final String	NL			= "\015\012";

	private byte[]
	load(
		String	url,
		int		max_millis_to_wait )
	{
			// limit the subset here as we're looping waiting for something to be alive and we can't afford to take ages getting back to the start

		long	start = System.currentTimeMillis();

		while( true ){

outer:
			for (int i=45100;i<=45108;i++){

				long	now = System.currentTimeMillis();

				if ( now < start ){

					start  = now;
				}

				if ( now - start > max_millis_to_wait ){

					return( null );
				}

				Socket	sock = null;

				try{
					sock = new Socket();

					sock.connect( new InetSocketAddress( "127.0.0.1", i ), 500 );

					sock.setSoTimeout( 5000 );

					PrintWriter	pw = new PrintWriter( sock.getOutputStream());

					pw.println( "GET " + url + " HTTP/1.1" + NL + NL );

					pw.flush();

					InputStream	is = sock.getInputStream();

					String	header = "";

					byte[]	buffer = new byte[1];

					while( true ){

						int	len = is.read( buffer );

						if ( len <= 0 ){

							break outer;
						}

						header += new String( buffer, 0, len );

						if ( header.endsWith( NL + NL )){

							break;
						}
					}

					int	pos = header.indexOf( NL );

					String	first_line = header.substring( 0, pos );

					if (!first_line.contains("200")){

						continue;
					}

					ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

					buffer = new byte[2048];

					while( true ){

						int	len = is.read( buffer );

						if ( len <= 0 ){

							break;
						}

						baos.write( buffer, 0, len );

						if ( baos.size() > 512*1024 ){

							break outer;
						}
					}

					return( baos.toByteArray());

				}catch( Throwable e ){

				}finally{

					if ( sock != null ){

						try{
							sock.close();

						}catch( Throwable e ){
						}
					}
				}
			}
		}
	}

	public boolean
	sendSetValue(
		String		name,
		String		value,
		int			max_millis )
	{
		String msg = "/setinfo?name=" + name + "&value=" + value;

		byte[] response = load( msg, max_millis );

		if ( response == null ){

			return( false );
		}

			// 40 x 40 image is encoded as 134 bytes...

		boolean	success = response.length == 134;

		System.out.println( name+"="+value + " -> " + success );

		return( success );
	}
}
