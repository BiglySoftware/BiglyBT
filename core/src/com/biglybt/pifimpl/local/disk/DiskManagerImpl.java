/*
 * File    : DiskManagerImpl.java
 * Created : 22-Mar-2004
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

package com.biglybt.pifimpl.local.disk;

/**
 * @author parg
 *
 */


import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.pif.disk.*;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.utils.PooledByteBufferImpl;

public class
DiskManagerImpl
	implements DiskManager
{
	private com.biglybt.core.disk.DiskManager		disk_manager;

	public
	DiskManagerImpl(
		com.biglybt.core.disk.DiskManager		_disk_manager )
	{
		disk_manager	= _disk_manager;
	}

	public com.biglybt.core.disk.DiskManager
	getDiskmanager()
	{
		return( disk_manager );
	}

	@Override
	public DiskManagerReadRequest
	read(
		int 									piece_number,
		int 									offset,
		int 									length,
		final DiskManagerReadRequestListener	listener )

		throws DiskManagerException
	{
		if ( !disk_manager.checkBlockConsistencyForRead( "plugin", false, piece_number, offset, length )){

			throw( new DiskManagerException( "read invalid - parameters incorrect or piece incomplete" ));
		}

		final DMRR request = new DMRR( disk_manager.createReadRequest( piece_number, offset, length ));

		disk_manager.enqueueReadRequest(
			request.getDelegate(),
			new com.biglybt.core.disk.DiskManagerReadRequestListener()
			{
				@Override
				public void
				readCompleted(
					com.biglybt.core.disk.DiskManagerReadRequest 	_request,
					DirectByteBuffer 										_data )
				{
					listener.complete( request, new PooledByteBufferImpl( _data ));
				}

				@Override
				public void
				readFailed(
					com.biglybt.core.disk.DiskManagerReadRequest 	_request,
					Throwable		 										_cause )
				{
					listener.failed( request, new DiskManagerException( "read failed", _cause ));
				}

				@Override
				public int
				getPriority()
				{
					return( 0 );
				}

				@Override
				public void
				requestExecuted(
					long 	bytes )
				{
				}
			});

		return( request );
	}

	@Override
	public DiskManagerWriteRequest
	write(
		final int								piece_number,
		final int								offset,
		PooledByteBuffer						data,
		final DiskManagerWriteRequestListener	listener )

		throws DiskManagerException
	{
		DirectByteBuffer buffer = ((PooledByteBufferImpl)data).getBuffer();

		if ( !disk_manager.checkBlockConsistencyForWrite( "plugin", piece_number, offset, buffer )){

			throw( new DiskManagerException( "write invalid - parameters incorrect" ));
		}

		final int	length = buffer.remaining( DirectByteBuffer.SS_EXTERNAL );

		final DMWR request = new DMWR( disk_manager.createWriteRequest( piece_number, offset, buffer, null ),length );

		disk_manager.enqueueWriteRequest(
			request.getDelegate(),
			new com.biglybt.core.disk.DiskManagerWriteRequestListener()
			{
				@Override
				public void
				writeCompleted(
					com.biglybt.core.disk.DiskManagerWriteRequest 	_request )
				{
					DiskManagerPiece[]	dm_pieces = disk_manager.getPieces();

					DiskManagerPiece	dm_piece = dm_pieces[piece_number];

					if (!dm_piece.isDone()){

						int	current_offset = offset;

						for ( int i=0;i<length;i+=DiskManager.BLOCK_SIZE ){

							dm_piece.setWritten( current_offset / DiskManager.BLOCK_SIZE );

							current_offset += DiskManager.BLOCK_SIZE;
						}
					}

					listener.complete( request );
				}

				@Override
				public void
				writeFailed(
					com.biglybt.core.disk.DiskManagerWriteRequest 	_request,
					Throwable		 										_cause )
				{
					listener.failed( request, new DiskManagerException( "read failed", _cause ));
				}
			});

		return( request );
	}

	private static class
	DMRR
		implements com.biglybt.pif.disk.DiskManagerReadRequest
	{
		private com.biglybt.core.disk.DiskManagerReadRequest		request;

		private
		DMRR(
			com.biglybt.core.disk.DiskManagerReadRequest	_request )
		{
			request = _request;
		}

		private com.biglybt.core.disk.DiskManagerReadRequest
		getDelegate()
		{
			return( request );
		}

		@Override
		public int
		getPieceNumber()
		{
			return( request.getPieceNumber());
		}

		@Override
		public int
		getOffset()
		{
			return( request.getOffset());
		}

		@Override
		public int
		getLength()
		{
			return( request.getLength());
		}
	}

	private static class
	DMWR
		implements com.biglybt.pif.disk.DiskManagerWriteRequest
	{
		private com.biglybt.core.disk.DiskManagerWriteRequest		request;
		private int															length;

		private
		DMWR(
			com.biglybt.core.disk.DiskManagerWriteRequest	_request,
			int														_length )
		{
			request = _request;
		}

		private com.biglybt.core.disk.DiskManagerWriteRequest
		getDelegate()
		{
			return( request );
		}

		@Override
		public int
		getPieceNumber()
		{
			return( request.getPieceNumber());
		}

		@Override
		public int
		getOffset()
		{
			return( request.getOffset());
		}

		@Override
		public int
		getLength()
		{
			return( length );
		}
	}
}
