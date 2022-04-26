/*
 * File    : FMFileUnlimited.java
 * Created : 12-Feb-2004
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

package com.biglybt.core.diskmanager.file.impl;

/**
 * @author parg
 *
 */

import java.io.File;

import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.diskmanager.file.FMFileOwner;
import com.biglybt.core.util.DirectByteBuffer;

public class
FMFileUnlimited
	extends FMFileImpl
{
	protected
	FMFileUnlimited(
		FMFileOwner			owner,
		FMFileManagerImpl	manager,
		File				file,
		int					type,
		boolean				force )

		throws FMFileManagerException
	{
		super( owner, manager, file, type, force );
	}

	protected
	FMFileUnlimited(
		FMFileUnlimited	basis )

		throws FMFileManagerException
	{
		super( basis );
	}

	@Override
	public FMFile
	createClone()

		throws FMFileManagerException
	{
		return( new FMFileUnlimited( this ));
	}

	@Override
	public void
	setAccessMode(
		int		mode )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			if ( mode == getAccessMode() && isOpen()){

				return;
			}

			setAccessModeSupport( mode );

			if ( isOpen()){

				closeSupport( false );
			}

			openSupport( "FMFileUnlimited:setAccessMode" );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public long
	getLength()

		throws FMFileManagerException
	{
		boolean got_mon = false;
		
		try{
			do{	
				long length_cache = getLengthCache();
				
				if ( length_cache >= 0 ){
					
					return( length_cache );
				}
			}while( !this_mon.enter(250));

			got_mon = true;
			
				// if the file doesn't exist then we don't want to ensure it is open to get the length
				// as this will create the file
			
			if ( !exists()){
				
				return( 0 );
				
			}else{
				
				ensureOpen( "FMFileUnlimited:getLength" );
	
				return( getLengthSupport());
			}
		}finally{

			if ( got_mon ){
			
				this_mon.exit();
			}
		}
	}

	@Override
	public void
	setLength(
		long		length )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:setLength" );

			setLengthSupport( length );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	setPieceComplete(
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			if ( isPieceCompleteProcessingNeeded( piece_number )){

				ensureOpen( "FMFileUnlimited:setPieceComplete" );

				boolean	switched_mode = false;

				if ( getAccessMode() != FM_WRITE ){

					setAccessMode( FM_WRITE );

					switched_mode = true;

						// switching mode closes the file...

					ensureOpen( "FMFileUnlimited:setPieceComplete2" );
				}

				try{

					setPieceCompleteSupport( piece_number, piece_data );

				}finally{

					if ( switched_mode ){

						setAccessMode( FM_READ );
					}
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	read(
		DirectByteBuffer	buffer,
		long				offset )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:read" );

			readSupport( buffer, offset );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	read(
		DirectByteBuffer[]	buffers,
		long				offset )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:read" );

			readSupport( buffers, offset );

		}finally{

			this_mon.exit();
		}
	}


	@Override
	public void
	write(
		DirectByteBuffer	buffer,
		long		position )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:write" );

			writeSupport( buffer, position );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	write(
		DirectByteBuffer[]	buffers,
		long				position )

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			ensureOpen( "FMFileUnlimited:write" );

			writeSupport( buffers, position );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	close()

		throws FMFileManagerException
	{
		try{
			this_mon.enter();

			closeSupport( true );

		}finally{

			this_mon.exit();
		}
	}
}
