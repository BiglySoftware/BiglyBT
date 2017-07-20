/*
 * Created on Jan 19, 2011
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


package com.biglybt.core.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Set;

/*
 * The original code by Mike Melanson (melanson@pcisys.net) was placed in the
 * public domain and so is this Java port of it.
 */


public class
QTFastStartRAF
{
	private static final Set<String>	supported_extensions = new HashSet<>();

	static{
		supported_extensions.add( "mov" );
		supported_extensions.add( "qt" );
		supported_extensions.add( "mp4" );
	}

	private static final Set<String>	tested = new HashSet<>();

	public static boolean
	isSupportedExtension(
		String		extension )
	{
		return( supported_extensions.contains( extension.toLowerCase()));
	}

	private static final String ATOM_FREE = "free";
	private static final String ATOM_JUNK = "junk";
	private static final String ATOM_MDAT = "mdat";
	private static final String ATOM_MOOV = "moov";
	private static final String ATOM_PNOT = "pnot";
	private static final String ATOM_SKIP = "skip";
	private static final String ATOM_WIDE = "wide";
	private static final String ATOM_PICT = "PICT";
	private static final String ATOM_FTYP = "ftyp";

	private static final String ATOM_CMOV = "cmov";
	private static final String ATOM_STCO = "stco";
	private static final String ATOM_CO64 = "co64";

	private static final String[] VALID_TOPLEVEL_ATOMS = {
		ATOM_FREE, ATOM_JUNK, ATOM_MDAT, ATOM_MOOV,
		ATOM_PNOT, ATOM_SKIP, ATOM_WIDE, ATOM_PICT,
		ATOM_FTYP };

	private final FileAccessor	input;

	private boolean		transparent;

	private byte[]		header;
	private long		body_start;
	private long		body_end;

	private long		seek_position;

	public
	QTFastStartRAF(
		File		file,
		boolean		enable )

		throws IOException
	{
		this( new RAFAccessor( file ), enable );
	}

	public
	QTFastStartRAF(
		FileAccessor	accessor,
		boolean			enable )

		throws IOException
	{
		input = accessor;

		if ( enable ){

			String	name = accessor.getName();

			boolean	log;
			String	fail	= null;

			synchronized( tested ){

				log = !tested.contains( name );

				if ( log ){

					tested.add( name );
				}
			}

			try{
				Atom ah = null;
				Atom ftypAtom = null;

				boolean gotFtyp = false;
				boolean gotMdat = false;
				boolean justCopy = false;

				while (input.getFilePointer() < input.length()) {

					ah = new Atom(input);

					// System.out.println( "got " + ah.type +", size=" + ah.size );

					if (!isValidTopLevelAtom(ah)) {
						throw new IOException("Non top level QT atom found (" + ah.type + "). File invalid?");
					}

					if (gotFtyp && !gotMdat && ah.type.equalsIgnoreCase(ATOM_MOOV)) {
						justCopy = true;
						break;
					}

					// store ftyp atom to buffer
					if (ah.type.equalsIgnoreCase(ATOM_FTYP)) {
						ftypAtom = ah;
						ftypAtom.fillBuffer(input);
						gotFtyp = true;
					} else if (ah.type.equalsIgnoreCase(ATOM_MDAT)) {
						gotMdat = true;
						input.skipBytes((int)ah.size);
					} else {
						input.skipBytes((int)ah.size);
					}
				}

				if ( justCopy ){

					transparent	= true;

					return;
				}

				if ( ftypAtom == null ){

					throw new IOException("No FTYP atom found");
				}

				if ( ah == null || !ah.type.equalsIgnoreCase(ATOM_MOOV)){

					throw new IOException("Last QT atom was not the MOOV atom.");
				}

				input.seek(ah.offset);

				Atom moovAtom = ah;

				moovAtom.fillBuffer(input);

				if (isCompressedMoovAtom(moovAtom)){

					throw new IOException("Compressed MOOV qt atoms are not supported");
				}

				patchMoovAtom(moovAtom);

				body_start 	= ftypAtom.offset+ftypAtom.size;
				body_end	= moovAtom.offset;

				header = new byte[ftypAtom.buffer.length + moovAtom.buffer.length];

				System.arraycopy( ftypAtom.buffer, 0, header, 0, ftypAtom.buffer.length );
				System.arraycopy( moovAtom.buffer, 0, header, ftypAtom.buffer.length, moovAtom.buffer.length );

				if ( accessor.length() != header.length + ( body_end - body_start )){

					throw( new IOException( "Inconsistent: file size has changed" ));
				}

			}catch( Throwable e ){

				//e.printStackTrace();

				fail = Debug.getNestedExceptionMessage( e );

				transparent	= true;

			}finally{

				input.seek( 0 );

				if ( log ){

					String	message;

					if ( fail == null ){

						message = transparent?"Not required":"Required";

					}else{

						message = "Failed - " + fail;
					}

					Debug.outNoStack( "MOOV relocation for " + accessor.getName() + ": " + message );
				}
			}
		}else{

			transparent = true;
		}
	}

	private boolean isCompressedMoovAtom(Atom moovAtom) {

		byte[] cmovBuffer = copyOfRange(moovAtom.buffer, 12, 15);

		if (new String(cmovBuffer).equalsIgnoreCase(ATOM_CMOV)) {
			return true;
		}

		return false;
	}

	private boolean isValidTopLevelAtom(Atom ah) {

		for (String validAtom: VALID_TOPLEVEL_ATOMS) {
			if (validAtom.equalsIgnoreCase(ah.type)) {
				return true;
			}
		}
		return false;

	}

	private void patchMoovAtom(Atom moovAtom) {

		int idx = 0;
		for (idx = 4; idx < moovAtom.size-4; idx++) {
			byte[] buffer = copyOfRange(moovAtom.buffer, idx, idx+4);
			if (new String(buffer).equalsIgnoreCase(ATOM_STCO)) {
				int stcoSize = patchStcoAtom(moovAtom, idx);
				idx += stcoSize - 4;
			} else if (new String(buffer).equalsIgnoreCase(ATOM_CO64)) {
				int co64Size = patchCo64Atom(moovAtom, idx);
				idx += co64Size - 4;
			}
		}

	}

	private int patchStcoAtom(Atom ah, int idx) {
		int stcoSize = (int)bytesToLong(copyOfRange(ah.buffer, idx-4, idx));

		int offsetCount = (int)bytesToLong(copyOfRange(ah.buffer, idx + 8, idx+12));
		for (int j = 0; j < offsetCount; j++) {
			int currentOffset = (int)bytesToLong(copyOfRange(ah.buffer, idx + 12 + j * 4, (idx + 12 + j * 4)+4));
			currentOffset += ah.size;
			int offsetIdx = idx + 12 + j * 4;
			ah.buffer[offsetIdx + 0] = (byte)((currentOffset >> 24) & 0xFF);
			ah.buffer[offsetIdx + 1] = (byte)((currentOffset >> 16) & 0xFF);
			ah.buffer[offsetIdx + 2] = (byte)((currentOffset >>  8) & 0xFF);
			ah.buffer[offsetIdx + 3] = (byte)((currentOffset >>  0) & 0xFF);
		}

		return stcoSize;
	}

	private int patchCo64Atom(Atom ah, int idx) {
		int co64Size = (int)bytesToLong(copyOfRange(ah.buffer, idx-4, idx));

		int offsetCount = (int)bytesToLong(copyOfRange(ah.buffer, idx + 8, idx+12));
		for (int j = 0; j < offsetCount; j++) {
			long currentOffset = bytesToLong(copyOfRange(ah.buffer, idx + 12 + j * 8, (idx + 12 + j * 8)+8));
			currentOffset += ah.size;
			int offsetIdx = idx + 12 + j * 8;
			ah.buffer[offsetIdx + 0] = (byte)((currentOffset >> 56) & 0xFF);
			ah.buffer[offsetIdx + 1] = (byte)((currentOffset >> 48) & 0xFF);
			ah.buffer[offsetIdx + 2] = (byte)((currentOffset >> 40) & 0xFF);
			ah.buffer[offsetIdx + 3] = (byte)((currentOffset >> 32) & 0xFF);
			ah.buffer[offsetIdx + 4] = (byte)((currentOffset >> 24) & 0xFF);
			ah.buffer[offsetIdx + 5] = (byte)((currentOffset >> 16) & 0xFF);
			ah.buffer[offsetIdx + 6] = (byte)((currentOffset >>  8) & 0xFF);
			ah.buffer[offsetIdx + 7] = (byte)((currentOffset >>  0) & 0xFF);
		}

		return co64Size;
	}

	public static byte[] copyOfRange(byte[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0)
			throw new IllegalArgumentException(from + " > " + to);
		byte[] copy = new byte[newLength];
		System.arraycopy(original, from, copy, 0,
				Math.min(original.length - from, newLength));
		return copy;
	}

	private long bytesToLong(byte[] buffer) {

		long retVal = 0;

		for ( int i = 0; i < buffer.length; i++ ) {
			retVal += ((buffer[i] & 0x00000000000000FF) << 8*(buffer.length-i-1)) ;
		}

		return retVal;

	}

	public void
	seek(
		long		pos )

		throws IOException
	{
		if ( transparent ){

			input.seek( pos );

		}else{

			seek_position = pos;
		}
	}

	public int
	read(
		byte[]			buffer,
		int				pos,
		int				len )

		throws IOException
	{
		if ( transparent ){

			return( input.read( buffer, pos, len ));
		}

			// [header]
			// file [body-start -> body-end]

		long	start_pos	= seek_position;
		int		start_len 	= len;

		if ( seek_position < header.length ){

			int	rem = (int)( header.length - seek_position );

			if ( rem > len ){

				rem = len;
			}

			System.arraycopy( header, (int)seek_position, buffer, pos, rem );

			pos += rem;
			len	-= rem;

			seek_position += rem;
		}

		if ( len > 0 ){

			long	file_position = body_start + seek_position - header.length;

			long	rem = body_end - file_position;

			if ( len < rem ){

				rem = len;
			}

			input.seek( file_position );

			int	temp = input.read( buffer, pos, (int)rem );

			pos += temp;
			len	-= temp;

			seek_position += temp;
		}

		int	read = start_len - len;

		seek_position = start_pos + read;

		return( read );
	}

	public long
	length()

		throws IOException
	{
		return( input.length());
	}

	public void
	close()

		throws IOException
	{
		input.close();
	}

	private static class
	Atom
	{

		public long offset;
		public long size;
		public String type;
		public byte[] buffer = null;

		public Atom(FileAccessor input) throws IOException {
			offset = input.getFilePointer();
			// get atom size
			size = input.readInt();
			// get atom type
			byte[] atomTypeFCC = new byte[4];
			input.readFully(atomTypeFCC);
			type = new String(atomTypeFCC);
			if (size == 1) {
				// 64 bit size. Read new size from body and store it
				size = input.readLong();
			}
			// skip back to atom start
			input.seek(offset);
		}

		public void fillBuffer(FileAccessor input) throws IOException {
			buffer = new byte[(int)size];
			input.readFully(buffer);
		}
	}

	private static class
	RAFAccessor
		implements FileAccessor
	{
		private final File					file;
		private RandomAccessFile		raf;

		private
		RAFAccessor(
			File			_file )

			throws IOException
		{
			file	= _file;
			raf 	= new RandomAccessFile( file, "r" );
		}

		@Override
		public String
		getName()
		{
			return( file.getAbsolutePath());
		}

		@Override
		public long
		getFilePointer()

			throws IOException
		{
			return( raf.getFilePointer());
		}

		@Override
		public void
		seek(
			long		pos )

			throws IOException
		{
			raf.seek( pos );
		}

		@Override
		public void
		skipBytes(
			int		num )

			throws IOException
		{
			raf.skipBytes( num );
		}

		@Override
		public long
		length()

			throws IOException
		{
			return( raf.length());
		}

		@Override
		public int
		read(
			byte[]	buffer,
			int		pos,
			int		len )

			throws IOException
		{
			return( raf.read(buffer,pos,len));
		}

		@Override
		public int
		readInt()

			throws IOException
		{
			return( raf.readInt());
		}

		@Override
		public long
		readLong()

			throws IOException
		{
			return( raf.readLong());
		}

		@Override
		public void
		readFully(
			byte[]	buffer )

			throws IOException
		{
			raf.readFully( buffer );
		}

		@Override
		public void
		close()

			throws IOException
		{
			raf.close();
		}
	}

	public interface
	FileAccessor
	{
		public String
		getName();

		public long
		getFilePointer()

			throws IOException;

		public void
		seek(
			long		pos )

			throws IOException;

		public void
		skipBytes(
			int		num )

			throws IOException;

		public long
		length()

			throws IOException;

		public int
		read(
			byte[]	buffer,
			int		pos,
			int		len )

			throws IOException;

		public int
		readInt()

			throws IOException;

		public long
		readLong()

			throws IOException;

		public void
		readFully(
			byte[]	buffer )

			throws IOException;

		public void
		close()

			throws IOException;
	}
	/*
	public static void
	main(
		String[]		args )
	{
		try{
			QTFastStartRAF	raf = new QTFastStartRAF( new File( "C:\\temp\\spork.mp4" ), true );

			long	len = raf.length();

			byte[]	buffer = new byte[43];

			long	total = 0;

			FileOutputStream fos = new FileOutputStream(new File( "C:\\temp\\qtfs_out.mp4" ));

			while( true ){

				int	read = raf.read( buffer, 0, buffer.length );

				if ( read <= 0 ){

					break;
				}

				fos.write( buffer, 0, read );

				total += read;
			}

			if ( total != len ){

				System.out.println( "bork" );
			}

			raf.close();

			fos.close();

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
	*/
}
