/*
 * File    : TrackerWebPageResponse.java
 * Created : 08-Dec-2003
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

package com.biglybt.pif.tracker.web;

import java.io.ByteArrayOutputStream;

/**
 * @author parg
 *
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.biglybt.pif.tracker.TrackerTorrent;

public interface
TrackerWebPageResponse
{
	public OutputStream
	getOutputStream();

	public void
	setOutputStream(
		ByteArrayOutputStream	os );
	
	public void
	setReplyStatus(
		int		status );

	public String
	getContentType();
	
	public void
	setContentType(
		String		type );

	public void
	setLastModified(
		long		time );

	public void
	setExpires(
		long		time );

	public void
	setHeader(
		String		name,
		String		value );

	public void
	setGZIP(
		boolean		gzip );

		/**
		 * use a file contents as the response. returns true of loaded ok, false if doesn't exist
		 * exception if error occurred during processing.
		 * @param root_dir			e.g. c:\temp\parp  or /tmp/trout/
		 * @param relative_url		e.g. /here/there/wibble.html
		 * @return
		 * @throws IOException
		 */

	public boolean
	useFile(
		String		root_dir,
		String		relative_url )

		throws IOException;

	public void
	useStream(
		String		file_type,
		InputStream	stream )

		throws IOException;

	public void
	writeTorrent(
		TrackerTorrent	torrent )

		throws IOException;

		/**
		 * For a non-blocking tracker the construction of the response can be completed asynchronously
		 * by setting async=true and then, when complete, setting it to false
		 * @param async
		 */

	public void
	setAsynchronous(
		boolean		async )

		throws IOException;

	public boolean
	getAsynchronous();

		/**
		 * Request complete responsibility for writing the output stream
		 * @since 5101
		 * @return
		 * @throws IOException
		 */

	public OutputStream
	getRawOutputStream()

		throws IOException;

	public boolean
	isActive();
}
