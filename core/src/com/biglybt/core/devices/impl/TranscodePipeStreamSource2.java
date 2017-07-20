/*
 * Created on Feb 9, 2009
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


package com.biglybt.core.devices.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;


public class
TranscodePipeStreamSource2
	extends TranscodePipe
{
	private streamListener		adapter;

	protected
	TranscodePipeStreamSource2(
		streamListener	_adapter )

		throws IOException
	{
		super( null );

		adapter	= _adapter;
	}


	@Override
	protected void
	handleSocket(
		Socket		socket1 )
	{
		synchronized( this ){

			if ( destroyed ){

				try{
					socket1.close();

				}catch( Throwable e ){
				}

				return;
			}

			sockets.add( socket1 );
		}

		try{

			adapter.gotStream( socket1.getInputStream());

		}catch( Throwable e ){

			synchronized( this ){

				try{
					socket1.close();

				}catch( Throwable f ){
				}


				sockets.remove( socket1 );
			}
		}
	}

	interface
	streamListener
	{
		public void
		gotStream(
			InputStream		is );
	}
}
