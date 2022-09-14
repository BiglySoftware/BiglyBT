/*
 * Created on 28-Sep-2005
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

package com.biglybt.core.diskmanager.file.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Locale;

import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.util.*;

public class
FMFileAccessLinear
	implements FMFileAccess
{
	private static final int 	WRITE_RETRY_LIMIT		= 10;
	private static final int	WRITE_RETRY_DELAY		= 100;

	private static final int 	READ_RETRY_LIMIT		= 10;
	private static final int	READ_RETRY_DELAY		= 100;

	private static final boolean	DEBUG			= true;
	private static final boolean	DEBUG_VERBOSE	= false;

	private static final boolean USE_MMAP = System.getProperty(SystemProperties.SYSPROP_IO_USEMMAP,"false") == "true";

	private final FMFileImpl		owner;

	protected
	FMFileAccessLinear(
		FMFileImpl		_owner )
	{
		owner	= _owner;
	}

	@Override
	public void
	aboutToOpen()

		throws FMFileManagerException
	{
	}

	@Override
	public long
	getLength(
		FileAccessor		fa )

		throws FMFileManagerException
	{
		try{
			AEThread2.setDebug( owner );

			return( fa.getLength());

		}catch( Throwable e ){

			throw( new FMFileManagerException( "getLength fails", e ));
		}
	}

	@Override
	public void
	setLength(
		FileAccessor			fa,
		long					length )

		throws FMFileManagerException
	{
		try{
			AEThread2.setDebug( owner );

			try{
				fa.setLength( length );

			}catch( IOException e ){

				if ( Constants.isAndroid ){

						// can't handle > Integer.MAX_VALUE, however try and fix for all fails just in case

					if ( !Debug.getNestedExceptionMessage( e ).toUpperCase( Locale.US ).contains( "EINVAL")){

							// doesn't look like the bug

						throw( e );
					}

					long	required 	= length - fa.getLength();

					if ( required > 0 ){

						if ( FileUtil.getUsableSpaceSupported()){

							long	usable		= FileUtil.getUsableSpace( owner.getLinkedFile().getParentFile());

								// usable is -1 if something went wrong

							if (  usable >= 0 && usable < required ){

								// 	looks like a valid error

								throw( e );
							}
						}
					}

					if ( required > 0 ){

						long	old_pos = fa.getPosition();

						try{
							fa.setPosition( length-1 );

							fa.write( 0 );

						}catch( IOException f ){

							throw( e );

						}finally{

							try{
								fa.setPosition( old_pos );

							}catch( Throwable f ){
							}
						}
					}else{

						// can't truncate :( - just allow things to continue
					}
				}else{

					throw( e );
				}
			}

		}catch( Throwable e ){

			throw( new FMFileManagerException( "setLength fails", e ));
		}
	}

	@Override
	public boolean
	isPieceCompleteProcessingNeeded(
		int					piece_number )
	{
		return( false );
	}

	@Override
	public void
	setPieceComplete(
		FileAccessor		fa,
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException
	{
	}

	public void
	read(
		FileAccessor		fa,
		DirectByteBuffer	buffer,
		long				offset )

		throws FMFileManagerException
	{
		if ( fa == null){

			throw new FMFileManagerException( "read failed: accessor is null" );
		}

		FileChannel fc = fa.getChannel();

		if ( !fc.isOpen()){

			Debug.out("FileChannel is closed: " + owner.getName());

			throw( new FMFileManagerException( "read failed: file is closed"));
		}

		AEThread2.setDebug( owner );

		try{
			if(USE_MMAP)
			{
				long remainingInFile = fc.size()-offset;
				long remainingInTargetBuffer = buffer.remaining(DirectByteBuffer.SS_FILE);
				MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, offset, Math.min(remainingInFile,remainingInTargetBuffer));
				buffer.put(DirectByteBuffer.SS_FILE, buf);
			} else {
				fc.position(offset);
				while (fc.position() < fc.size() && buffer.hasRemaining(DirectByteBuffer.SS_FILE))
					buffer.read(DirectByteBuffer.SS_FILE,fc);
			}



		}catch ( Exception e ){

			Debug.printStackTrace( e );

			throw( new FMFileManagerException( "read failed", e ));
		}
	}

	@Override
	public void
	read(
		FileAccessor		fa,
		DirectByteBuffer[]	buffers,
		long				offset )

		throws FMFileManagerException
	{
		if ( fa == null ){

			throw new FMFileManagerException( "read failed: accessor is null" );
		}

		FileChannel fc = fa.getChannel();

		if ( !fc.isOpen()){

			Debug.out("FileChannel is closed: " + owner.getName());

			throw( new FMFileManagerException( "read failed: file is closed"));
		}

		AEThread2.setDebug( owner );

		int[]	original_positions = new int[buffers.length];

		long read_start = SystemTime.getHighPrecisionCounter();

		try{
			if(USE_MMAP)
			{

				long size = 0;
				for(int i=0;i<buffers.length;i++)
				{
					size += buffers[i].remaining(DirectByteBuffer.SS_FILE);
					original_positions[i] = buffers[i].position(DirectByteBuffer.SS_FILE);
				}

				size = Math.min(size, fc.size()-offset);
				MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, offset, size);
				for(DirectByteBuffer b : buffers)
				{
					buf.limit(buf.position()+b.remaining(DirectByteBuffer.SS_FILE));
					b.put(DirectByteBuffer.SS_FILE, buf);
				}


			} else {

				fc.position(offset);
				ByteBuffer[]	bbs = new ByteBuffer[buffers.length];

				ByteBuffer	last_bb	= null;
				for (int i=0;i<bbs.length;i++){
					ByteBuffer bb = bbs[i] = buffers[i].getBuffer(DirectByteBuffer.SS_FILE);
					int	pos = original_positions[i] = bb.position();
					if ( pos != bb.limit()){
						last_bb	= bbs[i];
					}
				}

				if ( last_bb != null ){
					int		loop			= 0;

						// we sometimes read off the end of the file (when rechecking) so
						// bail out if we've completed the read or got to file end
						// a "better" fix would be to prevent the over-read in the first
						// place, but hey, we're just about to release and there may be other
						// instances of this...

						// nasty Android bug here regarding it incorrectly setting a buffer's position to
						// be the amount read as opposed to incrementing it by the amount read

					if ( Constants.isAndroid ){

						int	bbs_index = 0;

						while ( fc.position() < fc.size() && last_bb.hasRemaining()){

							ByteBuffer current_bb = bbs[bbs_index];

							if ( !current_bb.hasRemaining()){

								bbs_index++;

							}else{

								long	read = fc.read( current_bb );

								if ( read > 0 ){

									loop	= 0;

								}else{

									loop++;

									if ( loop == READ_RETRY_LIMIT ){
										Debug.out( "FMFile::read: zero length read - abandoning" );
										throw( new FMFileManagerException( "read fails: retry limit exceeded"));
									}

									if ( DEBUG_VERBOSE )
										Debug.out( "FMFile::read: zero length read - retrying" );

									try{
										Thread.sleep( READ_RETRY_DELAY*loop );
									}catch( InterruptedException e ){
										throw( new FMFileManagerException( "read fails: interrupted" ));
									}

								}
							}
						}
					}else{
						while ( fc.position() < fc.size() && last_bb.hasRemaining()){
							long	read = fc.read( bbs );
							if ( read > 0 ){
								loop	= 0;
							}else{
								loop++;
								if ( loop == READ_RETRY_LIMIT ){
									Debug.out( "FMFile::read: zero length read - abandoning" );
									throw( new FMFileManagerException( "read fails: retry limit exceeded"));
								}
								if ( DEBUG_VERBOSE )
									Debug.out( "FMFile::read: zero length read - retrying" );

								try{
									Thread.sleep( READ_RETRY_DELAY*loop );
								}catch( InterruptedException e ){
									throw( new FMFileManagerException( "read fails: interrupted" ));
								}

							}
						}
					}
				}
			}
		}catch ( Throwable e ){

			try{
				Debug.out( "Read failed: " + owner.getString() + ": raf open=" + fa.getChannel().isOpen() + ", len=" + fa.getLength() + ",off=" + offset );

			}catch( IOException f ){
			}

			Debug.printStackTrace( e );

			if ( original_positions != null ){

				try{
					for (int i=0;i<original_positions.length;i++){

						buffers[i].position( DirectByteBuffer.SS_FILE, original_positions[i] );

					}
				}catch( Throwable e2 ){

					Debug.out( e2 );
				}
			}

			throw( new FMFileManagerException( "read failed", e ));

		}finally{

			long elapsed_millis = ( SystemTime.getHighPrecisionCounter() - read_start )/1000000;

			if ( elapsed_millis > 10*1000 ){

				System.out.println( "read took " + elapsed_millis + " for " + owner.getString());
			}
		}
	}

	@Override
	public void
	write(
		FileAccessor			fa,
		DirectByteBuffer[]		buffers,
		long					position )

		throws FMFileManagerException
	{
		if ( fa == null){
			
			throw( new FMFileManagerException( "write failed: accessor is null" ));
		}

		FileChannel fc = fa.getChannel();

		if ( !fc.isOpen()){

			Debug.out("FileChannel is closed: " + owner.getName());

			throw( new FMFileManagerException( "write failed: file is closed"));
		}

		AEThread2.setDebug( owner );

		int[]	original_positions = new int[buffers.length];

		try{

			if(USE_MMAP) {
				long size = 0;
				for(int i=0;i<buffers.length;i++)
				{
					size += buffers[i].remaining(DirectByteBuffer.SS_FILE);
					original_positions[i] = buffers[i].position(DirectByteBuffer.SS_FILE);
				}

				if(position+size > fc.size())
				{
					fc.position(position+size-1);
					fc.write(ByteBuffer.allocate(1));
					fc.force(true);
				}

				MappedByteBuffer buf = fc.map(MapMode.READ_WRITE, position, size);
				for(DirectByteBuffer b : buffers)
					buf.put(b.getBuffer(DirectByteBuffer.SS_FILE));
				buf.force();
			} else {
				long	expected_write 	= 0;
				long	actual_write	= 0;
				boolean	partial_write	= false;

				if ( DEBUG ){
					for (int i=0;i<buffers.length;i++){
						expected_write += buffers[i].limit(DirectByteBuffer.SS_FILE) - buffers[i].position(DirectByteBuffer.SS_FILE);
					}
				}

				fc.position( position );
				ByteBuffer[]	bbs = new ByteBuffer[buffers.length];


				ByteBuffer	last_bb	= null;
				for (int i=0;i<bbs.length;i++){
					ByteBuffer bb = bbs[i] = buffers[i].getBuffer(DirectByteBuffer.SS_FILE);
					int	pos = original_positions[i] = bb.position();
					if ( pos != bb.limit()){
						last_bb	= bbs[i];
					}
				}

				if ( last_bb != null ){
					int loop = 0;

					while( last_bb.position() != last_bb.limit()){
						long written = fc.write( bbs );
						actual_write += written;

						if ( written > 0 ){
							loop	= 0;
							if ( DEBUG ){
								if ( last_bb.position() != last_bb.limit()){
									partial_write	= true;
									if ( DEBUG_VERBOSE ){
										Debug.out( "FMFile::write: **** partial write **** this = " + written + ", total = " + actual_write + ", target = " + expected_write );
									}
								}
							}

						}else{
							loop++;
							if ( loop == WRITE_RETRY_LIMIT ){
								Debug.out( "FMFile::write: zero length write - abandoning" );
								throw( new FMFileManagerException( "write fails: retry limit exceeded"));
							}

							if ( DEBUG_VERBOSE )
								Debug.out( "FMFile::write: zero length write - retrying" );

							try{
								Thread.sleep( WRITE_RETRY_DELAY*loop );
							}catch( InterruptedException e ){
								throw( new FMFileManagerException( "write fails: interrupted" ));
							}
						}
					}
				}

				if ( DEBUG ){
					if ( expected_write != actual_write ){
						Debug.out( "FMFile::write: **** partial write **** failed: expected = " + expected_write + ", actual = " + actual_write );
						throw( new FMFileManagerException( "write fails: expected write/actual write mismatch" ));
					}
					if ( partial_write && DEBUG_VERBOSE )
						Debug.out( "FMFile::write: **** partial write **** completed ok" );
				}

			}


		}catch ( Throwable e ){

			if ( original_positions != null ){

				try{
					for (int i=0;i<original_positions.length;i++){

						buffers[i].position( DirectByteBuffer.SS_FILE, original_positions[i] );

					}
				}catch( Throwable e2 ){

					Debug.out( e2 );
				}
			}

			throw( new FMFileManagerException( "write failed", e ));
		}
	}

	@Override
	public void
	flush()

		throws FMFileManagerException
	{
		// no state to flush
	}

	@Override
	public FMFileImpl
	getFile()
	{
		return( owner );
	}

	@Override
	public String
	getString()
	{
		return( "linear" );
	}
}
