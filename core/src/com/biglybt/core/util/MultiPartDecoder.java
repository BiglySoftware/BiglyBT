/*
 * Created on 23-Jun-2004
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
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.biglybt.core.util;

/**
 * @author parg
 *
 */

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class
MultiPartDecoder
{
	public FormField[]
	decode(
		String			boundary,
		InputStream		is )

		throws IOException
	{
		// -----------------------------7d4f2a310bca
		//Content-Disposition: form-data; name="upfile"; filename="C:\Temp\banana_custard.torrent"
		//Content-Type: application/octet-stream
		//
		// <data>
		// -----------------------------7d4f2a310bca
		// Content-Disposition: form-data; name="category"
		//
		//Music
		// -----------------------------7d4f2a310bca--

		byte[]	header_end_bytes	= "\r\n\r\n".getBytes( "ISO-8859-1" );

		byte[]	boundary_bytes = (  "\r\n--" + boundary ).getBytes( "ISO-8859-1" );

		int	boundary_len = boundary_bytes.length;

		byte[]	buffer 		= new byte[65536];
		int		buffer_pos	= 0;

		boolean	in_header	= true;

		byte[]	current_target 			= header_end_bytes;
		int		current_target_length	= 4;

		FormField	current_field = null;

		List	fields = new ArrayList();

		while( true ){

			int	buffer_pos_start = buffer_pos;

			int	len = is.read( buffer, buffer_pos, buffer.length - buffer_pos );

			if ( len < 0 ){

				len	= 0;
			}

			buffer_pos += len;

			boolean found_target = false;

			for (int i=0;i<=buffer_pos-current_target_length;i++){

				if ( buffer[i] == current_target[0] ){

					found_target	= true;

					for (int j=1;j<current_target_length;j++){

						if ( buffer[i+j] != current_target[j]){

							found_target = false;

							break;
						}
					}

					if ( found_target ){

						if ( in_header ){

							if ( current_field != null ){

								current_field.complete();
							}

							String	header = new String( buffer, 0, i+4 );

							int	cdl_pos = header.toLowerCase().indexOf("content-disposition");

							if ( cdl_pos == -1 ){

								throw( new IOException( "invalid header '" + header + "'" ));
							}

							int	cd_nl = header.indexOf( "\r\n", cdl_pos  );

							String	cd_line = header.substring( cdl_pos, cd_nl );

							int	cd_pos = 0;

							Map	attributes = new HashMap();

							while(true){

								int	p1 = cd_line.indexOf( ";", cd_pos );

								String	bit;

								if ( p1 == -1 ){
									bit = cd_line.substring( cd_pos );
								}else{
									bit = cd_line.substring( cd_pos, p1 );
									cd_pos = p1+1;
								}

								int	ep = bit.indexOf( "=" );

								if ( ep != -1 ){

									String	lhs = bit.substring(0,ep).trim();
									String	rhs = bit.substring(ep+1).trim();

									if ( rhs.startsWith("\"")){

										rhs = rhs.substring(1);
									}

									if( rhs.endsWith("\"")){
										rhs = rhs.substring(0,rhs.length()-1);
									}

									attributes.put( lhs.toLowerCase(), rhs );
								}

								if ( p1 == -1 ){
									break;
								}
							}

							String	field_name = (String)attributes.get("name");

							if( field_name == null ){

								throw( new IOException( cd_line + " missing 'name' attribute" ));
							}
							current_field = new FormField( field_name, attributes );

							fields.add( current_field );

						}else{

							current_field.write( buffer, 0, i );
						}

						int	rem = buffer_pos - (i+current_target_length);

						if ( rem > 0 ){

							System.arraycopy( buffer, i+current_target_length, buffer, 0, rem );
						}

						buffer_pos	= rem;

						if ( in_header ){

							in_header	= false;

							current_target			= boundary_bytes;
							current_target_length	= boundary_len;

						}else{

							in_header	= true;

							current_target 			= header_end_bytes;
							current_target_length	= 4;
						}

						break;
					}
				}
			}

				// if we didn't find the target and we're not in the header then
				// any remaining data in the buffer (less current target length)
				// is part of a body and can be written out

			if ( !(found_target || in_header )){

				int	rem = buffer_pos - current_target_length;

				if ( rem > 0 ){

						// process buffer 0 length rem

					current_field.write( buffer, 0, rem );

					System.arraycopy( buffer, rem, buffer, 0, current_target_length );

					buffer_pos = current_target_length;
				}
			}

			if ( len == 0 && buffer_pos == buffer_pos_start ){

					// nothing read and no progress made

				break;
			}
		}

			// should end in --

		if ( buffer_pos < 2 || buffer[0] != '-' || buffer[1] != '-' ){

			throw( new IOException( "Incorrect termination of form upload data"));
		}

		if ( current_field != null ){

			current_field.complete();
		}

		FormField[]	res = new FormField[fields.size()];

		fields.toArray( res );

		return( res );
	}

	public static class
	FormField
	{
		protected final String		name;
		protected final Map			attributes;

		protected long			total_len;

		final ByteArrayOutputStream	baos = new ByteArrayOutputStream(1024);

		File					file;
		FileOutputStream		fos;

		InputStream				returned_stream;

		protected
		FormField(
			String		_name,
			Map			_attributes )
		{
			name		= _name;
			attributes	= _attributes;

			// System.out.println( "formField:" + name );
		}

		public String
		getName()
		{
			return( name );
		}

		public String
		getAttribute(
			String	attr_name )
		{
			return((String)attributes.get(attr_name.toLowerCase()));
		}

		public InputStream
		getInputStream()

			throws IOException
		{
			if ( file == null ){

				returned_stream = new ByteArrayInputStream( baos.toByteArray());

			}else{

				returned_stream = FileUtil.newFileInputStream( file );
			}

			return( returned_stream );
		}

		public String
		getString()

			throws IOException
		{
			String	str = new LineNumberReader(new InputStreamReader( getInputStream())).readLine();

			if ( str == null ){

				str = "";
			}

			return( str );
		}

		public void
		destroy()
		{
			if ( returned_stream != null ){

				try{
					returned_stream.close();

				}catch( Throwable e ){

				}
			}

			if ( file != null ){

				file.delete();
			}
		}

		protected void
		write(
			byte[]		buffer,
			int			start,
			int			len )

			throws IOException
		{
			total_len	+= len;

			if ( fos != null ){

				fos.write( buffer, start, len );

			}else{

				if ( total_len > 1024 ){

					file	= File.createTempFile( "AZU", null );

					file.deleteOnExit();

					fos = FileUtil.newFileOutputStream( file );

					fos.write( baos.toByteArray());

					fos.write( buffer, start, len );

				}else{

					baos.write( buffer, start, len );
				}
			}
		}

		protected void
		complete()

			throws IOException
		{
			// System.out.println( "    total_len = " + total_len );

			if ( fos != null ){

				fos.close();
			}
		}
	}
}
