/*
 * Created on 4 Oct 2006
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

package com.biglybt.core.networkmanager.impl.http;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.util.Debug;

public class
HTTPNetworkConnectionWebSeed
	extends HTTPNetworkConnection
{
	private boolean	switching;

	protected
	HTTPNetworkConnectionWebSeed(
		HTTPNetworkManager		_manager,
		NetworkConnection		_connection,
		PEPeerTransport			_peer )
	{
		super( _manager, _connection, _peer );
	}

	@Override
	protected void
	decodeHeader(
		final HTTPMessageDecoder	decoder,
		final String				header )

		throws IOException
	{
		if ( switching ){

			Debug.out( "new header received while paused" );

			throw( new IOException( "Bork" ));
		}

		if ( !isSeed()){

			return;
		}

		PEPeerControl	control = getPeerControl();

		try{
			int	pos = header.indexOf( NL );

			String	line = header.substring(4,pos);

			pos = line.lastIndexOf( ' ' );

			String	url = line.substring( 0, pos ).trim();

			pos = url.indexOf( '?' );

			if ( pos != -1 ){

				url = url.substring( pos+1 );
			}

			StringTokenizer	tok = new StringTokenizer( url, "&" );

			int			piece 	= -1;
			List<int[]>	ranges 	= new ArrayList<>();

			while( tok.hasMoreElements()){

				String	token = tok.nextToken();

				pos = token.indexOf('=');

				if ( pos != -1 ){

					String	lhs = token.substring(0,pos).toLowerCase( MessageText.LOCALE_ENGLISH );
					String	rhs = token.substring(pos+1);

					if ( lhs.equals( "info_hash" )){

	    				final byte[]	old_hash = control.getHash();

	    				final byte[]	new_hash = URLDecoder.decode( rhs, "ISO-8859-1" ).getBytes( "ISO-8859-1" );

						if ( !Arrays.equals( new_hash, old_hash )){

							switching		= true;

							decoder.pauseInternally();

							flushRequests(
								new flushListener()
								{
									private boolean triggered;

									@Override
									public void
									flushed()
									{
										synchronized( this ){

											if ( triggered ){

												return;
											}

											triggered = true;
										}

										getManager().reRoute(
												HTTPNetworkConnectionWebSeed.this,
												old_hash, new_hash, header );
									}
								});

							return;
						}
					}else if ( lhs.equals( "piece" )){

						try{
							piece = Integer.parseInt( rhs );

						}catch( Throwable e ){

							throw( new IOException( "Invalid piece number '" + rhs +"'" ));
						}
					}else if ( lhs.equals( "ranges" )){

						StringTokenizer	range_tok = new StringTokenizer( rhs, "," );

						while( range_tok.hasMoreTokens()){

							String	range = range_tok.nextToken();

							int	sep = range.indexOf( '-' );

							if ( sep == -1 ){

								throw( new IOException( "Invalid range specification '" + rhs + "'" ));
							}

							try{
								ranges.add(
										new int[]{
											Integer.parseInt( range.substring(0,sep)),
											Integer.parseInt( range.substring( sep+1 ))});

							}catch( Throwable e ){

								throw( new IOException( "Invalid range specification '" + rhs + "'" ));
							}
						}
					}
				}
			}

			if ( piece == -1 ){

				throw( new IOException( "Piece number not specified" ));
			}

			boolean	keep_alive = header.toLowerCase(MessageText.LOCALE_ENGLISH)
				.contains("keep-alive");

			int	this_piece_size = control.getPieceLength( piece );

			if ( ranges.size() == 0 ){

				ranges.add( new int[]{ 0, this_piece_size-1});
			}

			long[]	offsets	= new long[ranges.size()];
			long[]	lengths	= new long[ranges.size()];

			long	piece_offset = piece * (long)control.getPieceLength(0);

			for (int i=0;i<ranges.size();i++){

				int[]	range = ranges.get(i);

				int	start 	= range[0];
				int end		= range[1];

				if ( 	start < 0 || start >= this_piece_size ||
						end < 0 || end >= this_piece_size ||
						start > end ){

					throw( new IOException( "Invalid range specification '" + start + "-" + end + "'" ));
				}

				offsets[i] 	= piece_offset + start;
				lengths[i]	= ( end - start ) + 1;
			}

			addRequest( new httpRequest( offsets, lengths, 0, false, keep_alive ));

		}catch( Throwable e ){

			Debug.outNoStack( "Decode of '" + (header.length()>128?(header.substring(0,128) + "..."):header) + "' - " + Debug.getNestedExceptionMessageAndStack(e));

			if ( e instanceof IOException ){

				throw((IOException)e);

			}else{

				throw( new IOException( "Decode failed: " + Debug.getNestedExceptionMessage(e)));
			}
		}
	}
}
