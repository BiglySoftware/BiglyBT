/*
 * Created on 16 May 2006
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

package com.biglybt.plugin.extseed.impl;

import java.util.List;

import com.biglybt.pif.peers.PeerReadRequest;
import com.biglybt.plugin.extseed.ExternalSeedException;
import com.biglybt.plugin.extseed.util.ExternalSeedHTTPDownloaderListener;

public class
ExternalSeedReaderRequest
	implements ExternalSeedHTTPDownloaderListener
{
	private ExternalSeedReaderImpl	reader;

	private List<PeerReadRequest>			requests;

	private int		start_piece_number;
	private int		start_piece_offset;

	private int		length;

	private int					current_request_index = 0;
	private PeerReadRequest		current_request;
	private byte[]				current_buffer;
	private int					current_position;

	protected
	ExternalSeedReaderRequest(
		ExternalSeedReaderImpl		_reader,
		List<PeerReadRequest>		_requests )
	{
		reader		= _reader;
		requests	= _requests;

		for (int i=0;i<requests.size();i++){

			PeerReadRequest	req = (PeerReadRequest)requests.get(i);

			if ( i == 0 ){

				start_piece_number	= req.getPieceNumber();
				start_piece_offset	= req.getOffset();
			}

			length	+= req.getLength();
		}
	}

	public int
	getStartPieceNumber()
	{
		return( start_piece_number );
	}

	public int
	getStartPieceOffset()
	{
		return( start_piece_offset );
	}

	public int
	getLength()
	{
		return( length );
	}

	@Override
	public byte[]
	getBuffer()

		throws ExternalSeedException
	{
		if ( current_request_index >= requests.size()){

			throw( new ExternalSeedException( "Insufficient buffers to satisfy request" ));
		}

		current_request = (PeerReadRequest)requests.get(current_request_index++);

		current_buffer = new byte[ current_request.getLength()];

		current_position	= 0;

		return( current_buffer );
	}

	@Override
	public boolean
	isCancelled()
	{
		for (int i=0;i<requests.size();i++){

			PeerReadRequest	req = requests.get(i);

			if ( req.isCancelled()){

				return( true );
			}
		}

		return( false );
	}

	@Override
	public void
	done()
	{
		reader.informComplete( current_request, current_buffer );
	}

	protected void
	cancel()
	{
		for (int i=0;i<requests.size();i++){

			PeerReadRequest	req = requests.get(i);

			if ( !req.isCancelled()){

				req.cancel();
			}
		}
	}

	public void
	failed()
	{
		for (int i=current_request_index;i<requests.size();i++){

			PeerReadRequest	request = requests.get(i);

			reader.informFailed( request );
		}
	}

	@Override
	public void
	setBufferPosition(
		int	pos )
	{
		current_position	= pos;
	}

	@Override
	public int
	getBufferPosition()
	{
		return( current_position );
	}

	@Override
	public int
	getBufferLength()
	{
		return( current_buffer.length );
	}

	public int[]
	getCurrentMessageProgress()
	{
		PeerReadRequest	req = current_request;

		if ( req == null ){

			return( null );
		}

		return( new int[]{ req.getLength(), current_position });

	}

	@Override
	public int
	getPermittedBytes()

		throws ExternalSeedException
	{
		PeerReadRequest	req = current_request;

		if ( req == null ){

			req = requests.get(0);
		}

		if ( req.isCancelled()){

			throw( new ExternalSeedException( "Request cancelled" ));
		}

		return( reader.getPermittedBytes());
	}

	@Override
	public int
	getPermittedTime()
	{
		return( 0 );	// no timeout
	}

	@Override
	public void
	reportBytesRead(
		int		num )
	{
		reader.reportBytesRead( num );
	}
}
