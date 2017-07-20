/*
 * File    : TorrentDownloader.java
 * Created : 28-Feb-2004
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

package com.biglybt.pif.torrent;

/**
 * @author parg
 *
 */
public interface
TorrentDownloader
{
		/**
		 * Downloads and prompts the user/guesses the torrent encoding
		 * @return
		 * @throws TorrentException
		 */

	public Torrent
	download()

		throws TorrentException;

		/**
		 * Downloads and tries to use the supplied encoding. If the supplied encoding isn't
		 * valed then a TorrentEncodingException is thrown detailing the valid ones
		 *
		 * @param encoding		use "System" for system encoding
		 * @return
		 * @throws TorrentException
		 */

	public Torrent
	download(
		String	encoding )

		throws TorrentException;


	/**
	 *
	 * @param key "URL_Cookie" to set cookies
	 * @param value
	 *
	 * @since 4.8.1.3
	 */
	public void setRequestProperty(String key, Object value) throws TorrentException;

	/**
	 * @param key "URL_Cookie" to get cookies
	 * @return
	 */
	public Object getRequestProperty(String key) throws TorrentException;

}
