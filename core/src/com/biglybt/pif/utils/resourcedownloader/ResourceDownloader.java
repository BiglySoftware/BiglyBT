/*
 * File    : TorrentDownloader2.java
 * Created : 27-Feb-2004
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

package com.biglybt.pif.utils.resourcedownloader;

/**
 * @author parg
 *
 */

import java.io.InputStream;

public interface
ResourceDownloader
{
	public static final String	PR_STRING_CONTENT_TYPE		= "ContentType";
	public static final String	PR_BOOLEAN_ANONYMOUS		= "Anonymous";

		// Properties prefixed with URL_ will be passed directly to URL connections. For example
		// URL_User-Agent will be passed as User-Agent

		/**
		 * Get a sensible name for the download based on its details (e.g. URL)
		 * @return
		 */

	public String
	getName();

		/**
		 * Synchronously download. Events are still reported to listeners
		 * @return
		 * @throws ResourceDownloaderException
		 */

	public InputStream
	download()

		throws ResourceDownloaderException;

		/**
		 * Asynchronously download.
		 *
		 */

	public void
	asyncDownload();

		/**
		 * Attempts to get the size of the download. Returns -1 if the size can't be determined.
		 *
		 * <p>
		 *
		 * <b>Note:</b> You must not call this method from the <tt>reportActivity</tt> callback method.
		 *
		 * @throws ResourceDownloaderException
		 */

	public long
	getSize()

		throws ResourceDownloaderException;

	public void
	setProperty(
		String		name,
		Object		value )

		throws ResourceDownloaderException;

		/**
		 * Warning! URL response properties have a type of 'List of String'
		 *
		 * @param name
		 * @return
		 * @throws ResourceDownloaderException
		 */

	public Object
	getProperty(
		String		name )

		throws ResourceDownloaderException;

		/**
		 * Cancel the download.
		 */

	public void
	cancel();

	public boolean
	isCancelled();

	public ResourceDownloader
	getClone();

	public void
	reportActivity(
		String				activity );

	public void
	addListener(
		ResourceDownloaderListener	l );

	public void
	removeListener(
		ResourceDownloaderListener	l );
}
