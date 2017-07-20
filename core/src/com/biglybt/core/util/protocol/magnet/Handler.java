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

package com.biglybt.core.util.protocol.magnet;

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

import com.biglybt.net.magneturi.MagnetURIHandler;


public class
Handler
	extends URLStreamHandler
{
	@Override
	public URLConnection
	openConnection(URL u)
	{
			// some anti-virus apps blocking loopback connection we initially used
			// in MagnetConnection so created variant based on direct communication to
			// the magnet handler

		return(
			new MagnetConnection2(
				u,
				new MagnetConnection2.MagnetHandler()
				{

					@Override
					public void
					process(
						URL 			magnet,
						OutputStream	os)

						throws IOException
					{
						String	get = "/download/" + magnet.toString().substring( 7 ) + " HTTP/1.0\r\n\r\n";

						MagnetURIHandler.getSingleton().process( get, new ByteArrayInputStream(new byte[0]), os );
					}
				}));
	}
}
