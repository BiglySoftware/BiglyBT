/*
 * Created on Feb 15, 2007
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

package com.biglybt.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HTTPUtils {
	public static final String	NL				= "\r\n";

	private static final String	default_type	= "application/octet-stream";

	private static final Map<String,String>	file_types		= new HashMap<>();
	private static final Set<String>	compression		= new HashSet<>();

	static {
		file_types.put("html", "text/html");
		file_types.put("htm", "text/html");
		file_types.put("css", "text/css");
		file_types.put("js", "text/javascript");
		file_types.put("xml", "text/xml");
		file_types.put("xsl", "text/xml");
		file_types.put("jpg", "image/jpeg");
		file_types.put("jpeg", "image/jpeg");
		file_types.put("gif", "image/gif");
		file_types.put("tiff", "image/tiff");
		file_types.put("bmp", "image/bmp");
		file_types.put("png", "image/png");
		file_types.put("ico", "image/x-icon");
		file_types.put("torrent", "application/x-bittorrent");
		file_types.put("tor", "application/x-bittorrent");
		file_types.put("vuze", "application/x-vuze");
		file_types.put("vuz", "application/x-vuze");
		file_types.put("zip", "application/zip");
		file_types.put("txt", "text/plain");
		file_types.put("jar", "application/java-archive");
		file_types.put("jnlp", "application/x-java-jnlp-file");
		file_types.put("mp3", "audio/x-mpeg");

		file_types.put("flv", "video/x-flv");
		file_types.put("swf", "application/x-shockwave-flash");
		file_types.put("mkv", "video/x-matroska");
		file_types.put("mp4", "video/mp4");
		file_types.put("mov", "video/quicktime");
		file_types.put("avi", "video/avi");

		file_types.put("xap", "application/x-silverlight-app");

		compression.add("text/html");
		compression.add("text/css");
		compression.add("text/xml");
		compression.add("text/plain");
		compression.add("text/javascript");
	}

	/**
	 * @param file_type file extension
	 * @return appropriate content type string if found
	 */
	public static String guessContentTypeFromFileType(String file_type) {
		if (file_type != null) {

			if ( file_type.startsWith( "." )){
				file_type = file_type.substring(1);
			}
			
			String type = (String) file_types.get(file_type.toLowerCase( Constants.LOCALE_ENGLISH ));

			if (type != null) {

				return (type);
			}
		}

		return (default_type);
	}
	
	public static boolean isImageFileType(String file_type) {
		if (file_type != null) {

			if ( file_type.startsWith( "." )){
				file_type = file_type.substring(1);
			}
			
			String type = (String) file_types.get(file_type.toLowerCase( Constants.LOCALE_ENGLISH ));

			if (type != null) {

				return (type.startsWith( "image"));
			}
		}

		return( false );
	}

	public static boolean
	canGZIP(
		String	accept_encoding )
	{
		boolean	gzip_reply = false;

		if ( accept_encoding != null ){

			accept_encoding = accept_encoding.toLowerCase( Constants.LOCALE_ENGLISH );

			int gzip_index = accept_encoding.indexOf( "gzip" );

			if ( gzip_index != -1 ){

				gzip_reply	= true;

				if ( accept_encoding.length() - gzip_index >= 8 ){

						// gzip;q=0
						// look to see if there's a q=0 (or 0.0) disabling gzip

					char[]	chars = accept_encoding.toCharArray();

					boolean	q_value = false;

					for (int i=gzip_index+4;i<chars.length;i++){

						char	c = chars[i];

						if ( c == ',' ){

							break;

						}else if ( c == '=' ){

							q_value		= true;
							gzip_reply	= false;

						}else{

							if ( q_value ){

								if ( c != ' ' && c != '0' && c != '.' ){

									gzip_reply	= true;

									break;
								}
							}
						}
					}
				}
			}
		}

		return( gzip_reply );
	}

	/**
	 * @param file_type a file type like text/plain
	 * @return true if the file_type should be compressed
	 */
	public static boolean useCompressionForFileType(String file_type) {
		return compression.contains(file_type);
	}

	public static InputStream
	decodeChunkedEncoding(
		Socket		socket )

				throws IOException
	{
		return decodeChunkedEncoding(socket, false);
	}


	public static InputStream
	decodeChunkedEncoding(
		Socket		socket,
		boolean ignoreStatusCode)

		throws IOException
	{
		InputStream	is = socket.getInputStream();

		String reply_header = "";

		while (true) {

			byte[] buffer = new byte[1];

			if (is.read(buffer) <= 0) {

				throw (new IOException("Premature end of input stream"));
			}

			reply_header += (char) buffer[0];

			if (reply_header.endsWith(NL + NL)) {

				break;
			}
		}

		int p1 = reply_header.indexOf(NL);

		String first_line = reply_header.substring(0, p1).trim();

		if ( !ignoreStatusCode && !first_line.contains("200")){

			String	info = null;

			try{
					// limit time spent trying to read debug

				int timeout = socket.getSoTimeout();

				socket.setSoTimeout( 500 );

				info = FileUtil.readInputStreamAsStringWithTruncation( is, 512 );

				socket.setSoTimeout( timeout );

			}catch( Throwable e ){
			}

			String error = "HTTP request failed: " + first_line;

			if ( info != null ){

				error += " - " + info;
			}

			throw ( new IOException( error ));
		}

		String lc_reply_header = reply_header.toLowerCase( Constants.LOCALE_ENGLISH );

		int te_pos = lc_reply_header.indexOf("transfer-encoding");

		if (te_pos != -1) {

			String property = lc_reply_header.substring(te_pos);

			property = property.substring(property.indexOf(':') + 1,
					property.indexOf(NL)).trim();

			if (property.equals("chunked")) {

				ByteArrayOutputStream baos = new ByteArrayOutputStream();

				String chunk = "";

				int total_length = 0;

				while (true) {

					int x = is.read();

					if (x == -1) {

						break;
					}

					chunk += (char) x;

					// second time around the chunk will be prefixed with NL
					// from end of previous
					// so make sure we ignore this

					if (chunk.endsWith(NL) && chunk.length() > 2) {

						int semi_pos = chunk.indexOf(';');

						if (semi_pos != -1) {

							chunk = chunk.substring(0, semi_pos);
						}

						chunk = chunk.trim();

						int chunk_length = Integer.parseInt(chunk, 16);

						if (chunk_length <= 0) {

							break;
						}

						total_length += chunk_length;

						if (total_length > 1024 * 1024) {

							throw (new IOException("Chunk size " + chunk_length
									+ " too large"));
						}

						byte[] buffer = new byte[chunk_length];

						int buffer_pos = 0;
						int rem = chunk_length;

						while (rem > 0) {

							int len = is.read(buffer, buffer_pos, rem);

							if (len <= 0) {

								throw (new IOException(
										"Premature end of stream"));
							}

							buffer_pos += len;
							rem -= len;
						}

						baos.write(buffer);

						chunk = "";
					}
				}

				return (new ByteArrayInputStream(baos.toByteArray()));
			}
		} else {
			// if we have a content-length, grab only that many bytes
			// Some socket connectsions will timeout if you try to read more
			int cl_pos = lc_reply_header.indexOf("content-length");
			if (cl_pos == -1) {
				return is;
			}
			String property = lc_reply_header.substring(cl_pos);

			property = property.substring(property.indexOf(':') + 1,
					property.indexOf(NL)).trim();
			try {
  			long length = Long.parseLong(property);

  			// could be smarter with the buffer here
  			if (length > 0xFFFF) {
  				return is;
  			}

  			int remaining = (int) length;
  			int pos = 0;
  			byte[] buffer = new byte[remaining];
  			while (remaining > 0) {
  				int read = is.read(buffer, pos, remaining);
  				if (read < 0) {
  					break;
  				}
  				remaining -= read;
  				pos += read;
  			}
  			return new ByteArrayInputStream(buffer);
			} catch (NumberFormatException ignoreError) {
			}
		}

		return (is);
	}
}
