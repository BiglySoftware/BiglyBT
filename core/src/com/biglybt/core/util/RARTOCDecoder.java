/*
 * Created on Oct 8, 2012
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


import java.io.FileInputStream;
import java.io.IOException;

public class
RARTOCDecoder
{
	private final DataProvider		provider;

	public
	RARTOCDecoder(
		DataProvider		_provider )
	{
		provider	= _provider;
	}

	public void
	analyse(
		TOCResultHandler		result_handler )

		throws IOException
	{
		try{
			analyseSupport( result_handler );

			result_handler.complete();

		}catch( Throwable e ){

			IOException ioe;

			if ( e instanceof IOException ){

				ioe = (IOException)e ;

			}else{

				ioe = new IOException( "Analysis failed: " + Debug.getNestedExceptionMessage( e ));
			}

			result_handler.failed( ioe  );

			throw( ioe );
		}
	}

	private void
	analyseSupport(
		TOCResultHandler		result_handler )

		throws IOException
	{
			// http://acritum.com/winrar/rar-format

		byte[]	 header_buffer = new byte[7];	// marker block always 7 bytes

		readFully( header_buffer );

		if ( !new String( header_buffer ).startsWith( "Rar!" )){

			throw( new IOException( "Not a rar file" ));
		}

			// read archive header

		readFully( header_buffer );

		int	archive_header_size	= getShort( header_buffer, 5 );

		if ( archive_header_size > 1024 ){

			throw( new IOException( "Invalid archive header" ));
		}

		provider.skip( archive_header_size - 7 );	// skip over archive header

		while( true ){

				// read next 7 bytes of header record

			int	read = provider.read( header_buffer );

			if ( read < 7 ){

					// seen some short archive, just bail

				break;
			}

			int	block_type	= header_buffer[2]&0xff;

			int	entry_flags	= getShort( header_buffer, 3 );
			int	header_size	= getShort( header_buffer, 5 );

			//System.out.println( "type=" + Integer.toString( block_type, 16 ) + ", flags: " + Integer.toString( entry_flags, 16 ) + ", hs=" + header_size);

			if ( block_type < 0x70 || block_type > 0x90 ){

				throw( new IOException( "invalid header, archive corrupted"));
			}

				// ignore crc in first 2 bytes

			if ( block_type == 0x74 ){

				boolean	password = ( entry_flags & 0x004 ) != 0;

				/* in theory if this is set then there should be an optional 4 byte ADD_SIZE here but it doesn't work out
				if ( ( entry_flags & 0x8000 ) != 0 ){

					provider.skip( 4 );
				}
				*/

				byte[]	buffer = new byte[25];	// read up until potential optional HIGH_PACK entries

				readFully( buffer );

				long	comp_size	= getInteger( buffer, 0 );	// pack size
				long	act_size	= getInteger( buffer, 4 );  // uncompressed size

					// 1 byte host_os
					// 4 bytes crc
					// 4 bytes file time
					// 1 byte unrar version
					// 1 byte method
					// total = 11

				int extended_length = 0;

				if ( ( entry_flags & 0x0100 ) != 0 ){

						// extended size info available

					extended_length = 8;

					byte[]	extended_size_info = new byte[8];

					readFully( extended_size_info );

					comp_size |= getInteger( extended_size_info, 0 ) << 32;
					act_size  |= getInteger( extended_size_info, 4 ) << 32;
				}

					// 19 -  4-comp+4-act+11

				int	name_length = getShort( buffer, 19 );

				if ( name_length > 32*1024 ){


					throw( new IOException( "name length too large: " + name_length ));
				}

					// 4 byte attr

				byte[] name = new byte[name_length];

				readFully( name );

				String	decoded_name;

				if ( ( entry_flags & 0x0200 ) != 0 ){

					int	zero_pos = -1;

					for (int i=0;i<name.length;i++){

						if ( name[i] == 0 ){

							zero_pos = i;

							break;
						}
					}

					if ( zero_pos == -1 ){

						decoded_name =  new String( name, "UTF-8" );

					}else{

						decoded_name =  decodeName( name, zero_pos + 1 );

						if ( decoded_name == null ){

							decoded_name =  new String( name, 0, zero_pos , "UTF-8" );
						}
					}
				}else{

					decoded_name =  new String( name, "UTF-8" );
				}

				if ( ( entry_flags & 0xe0 ) == 0xe0 ){

						// directory

				}else{

					result_handler.entryRead( decoded_name, act_size, password );
				}

				provider.skip( header_size - ( 7 + 25 + extended_length + name_length ) + comp_size );

			}else if ( block_type == 0x7b ){

					// end of archive

				break;

			}else{

				provider.skip( archive_header_size - 7 );

				if ( ( entry_flags & 0x8000 ) != 0 ){

					provider.skip( 4 );
				}
			}
		}
	}

	private String
	decodeName(
		byte[]		b_data,
		int			pos )
	{
		try{
			int Flags		= 0;
			int FlagBits	= 0;

			byte[]	Name 		= b_data;
			byte[] 	EncName 	= b_data;
			int 	EncSize 	= b_data.length;
			int 	MaxDecSize 	= 4096;

			int[] NameW = new int[MaxDecSize];

			int EncPos = pos;
			int DecPos = 0;

			byte HighByte = EncName[EncPos++];

			while ( EncPos<EncSize && DecPos<MaxDecSize ){

				if ( FlagBits ==0 ){

					Flags		= EncName[EncPos++];
					FlagBits	= 8;
				}

				switch((Flags>>6)&0x03){

					case 0:{
						NameW[DecPos++]=EncName[EncPos++]&0xff;

						break;
					}
					case 1:{
						NameW[DecPos++]=(EncName[EncPos++]&0xff)+((HighByte<<8)&0xff00);

						break;
					}
					case 2:{
						NameW[DecPos++]=(EncName[EncPos++]&0xff)+((EncName[EncPos++]<<8)&0xff00);

						break;
					}
					case 3:{
						int Length = EncName[EncPos++]&0xff;

						if ((Length & 0x80) != 0){

							byte Correction = EncName[EncPos++];

							for (Length=(Length&0x7f)+2;Length>0 && DecPos<MaxDecSize;Length--,DecPos++){

								NameW[DecPos]=((Name[DecPos]+Correction)&0xff)+((HighByte<<8)&0xff00);
							}
						}else{
							for (Length+=2;Length>0 && DecPos<MaxDecSize;Length--,DecPos++){

								NameW[DecPos]=Name[DecPos]&0xff;
							}
						}

						break;
					}
				}

				Flags		<<=2;
				FlagBits	-=2;
			}

			byte[] 	temp = new byte[DecPos*2];

			for (int i=0;i<DecPos;i++){

				temp[i*2] 	= (byte)(( NameW[i]>>8 ) & 0xff);
				temp[i*2+1] = (byte)(( NameW[i] ) & 0xff);
			}

			return( new String( temp, "UTF-16BE" ));

		}catch( Throwable e ){

			Debug.outNoStack( "Failed to decode name: " + ByteFormatter.encodeString( b_data ) + " - " + Debug.getNestedExceptionMessage( e ));

			return( null );
		}
	}
	private void
	readFully(
		byte[]	buffer )

		throws IOException
	{
		if ( provider.read( buffer ) != buffer.length ){

			throw( new IOException( "unexpected end-of-file" ));
		}
	}

	public interface
	TOCResultHandler
	{
		public void
		entryRead(
			String		name,
			long		size,
			boolean		password )

			throws IOException;

		public void
		complete();

		public void
		failed(
			IOException error );
	}

	public interface
	DataProvider
	{
		public int
		read(
			byte[]		buffer )

			throws IOException;

		public void
		skip(
			long		bytes )

			throws IOException;
	}

	public static void
	main(
		String[]	args )
	{
		try{
			final FileInputStream fis = new FileInputStream( "C:\\temp\\mp.part6.rar" );

			RARTOCDecoder decoder =
				new RARTOCDecoder(
					new DataProvider()
					{
						@Override
						public int
						read(
							byte[]		buffer )

							throws IOException
						{
							return( fis.read( buffer ));
						}

						@Override
						public void
						skip(
							long		bytes )

							throws IOException
						{
							fis.skip( bytes );
						}
					});

			decoder.analyse(
				new TOCResultHandler()
				{
					@Override
					public void
					entryRead(
						String		name,
						long		size,
						boolean		password )
					{
						System.out.println( name + ": " + size + (password?" protected":""));
					}

					@Override
					public void
					complete()
					{
						System.out.println( "complete" );
					}

					@Override
					public void
					failed(
						IOException error )
					{
						System.out.println( "failed: " + Debug.getNestedExceptionMessage( error ));
					}
				});

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	private static int
	getShort(
		byte[]	buffer,
		int		pos )
	{
		return((( buffer[pos+1]  << 8 ) & 0xff00 ) | ( buffer[pos] & 0xff ));
	}

	private static long
	getInteger(
		byte[]	buffer,
		int		pos )
	{
		return(((( buffer[pos+3]  << 24 ) & 0xff000000 ) |
				(( buffer[pos+2]  << 16 ) & 0xff0000 ) |
				(( buffer[pos+1]  << 8 )  & 0xff00 ) |
				( buffer[pos] & 0xff )) & 0xffffffffL );
	}
}
