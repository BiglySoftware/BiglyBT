/*
 * File    : TorrentDownloader2Factory.java
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

import java.io.File;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;

public interface
ResourceDownloaderFactory
{
		/**
		 * Creates a downloader for a local file - in particular this is useful for installing a plugin
		 * from a local file as the installer required ResourceDownloader instances to operate
		 * @param file
		 * @return
		 */

	public ResourceDownloader
	create(
		File		file );

		/**
		 * creates a basic downloader. current url must be http or https
		 * @param url
		 * @return
		 */

	public ResourceDownloader
	create(
		URL		url );

	public default ResourceDownloader
	createWithAutoPluginProxy(
		URL			url )
	{
		return( createWithAutoPluginProxy( url, null ));
	}
	
	public ResourceDownloader
	createWithAutoPluginProxy(
		URL					url,
		Map<String,Object>	options );

	/**
	 * Creates a basic downloader, where you can force any configured proxy
	 * to be avoided.
	 *
	 * @since 3.1.0.1
	 */
	public ResourceDownloader create(URL url, boolean force_no_proxy);

	/**
	 * @since 5.2.0.1
	 */
	public ResourceDownloader create(URL url, Proxy force_proxy );

	public ResourceDownloader
	create(
		URL		url,
		String	user_name,
		String	password );

		/**
		 * creates a downloader that will be asked to create a ResourceDownloader
		 * when required. Useful when used in combination with an alternate downloader
		 * so that time isn't wasted creating downloaders for subsequent possibilities
		 * if the first one succeeds
		 * @param factory
		 * @return
		 */

	public ResourceDownloader
	create(
		ResourceDownloaderDelayedFactory	factory );

		/**
		 * gets a downloader that will retry a number of times before failing
		 * @param downloader
		 * @param retry_count
		 * @return
		 */

	public ResourceDownloader
	getRetryDownloader(
		ResourceDownloader		downloader,
		int						retry_count );


		/**
		 * gets a downloader that will timeout after a given period
		 * @param downloader
		 * @param timeout_millis
		 * @return
		 */

	public ResourceDownloader
	getTimeoutDownloader(
		ResourceDownloader		downloader,
		int						timeout_millis );


	/**
	 * Gets a downloader that will cycle through a list of downloaders until
	 * a download succeeds. The resource downloaders will be tried in order.
	 */
	public ResourceDownloader
	getAlternateDownloader(
		ResourceDownloader[]		downloaders );

	/**
	 * Gets a downloader that will cycle through a list of downloaders until
	 * a download succeeds. The resource downloaders will be tried in order.
	 */
	public ResourceDownloader
	getAlternateDownloader(
		ResourceDownloader[]		downloaders,
		int							max_to_try );

	/**
	 * Gets a downloader that will cycle through a list of downloaders until
	 * a download succeeds. The resource downloaders will be tried randomly.
	 */
	public ResourceDownloader
	getRandomDownloader(
		ResourceDownloader[]		downloaders );

	/**
	 * Gets a downloader that will cycle through a list of downloaders until
	 * a download succeeds. The resource downloaders will be tried randomly.
	 */
	public ResourceDownloader
	getRandomDownloader(
		ResourceDownloader[]		downloaders,
		int							max_to_try );

		/**
		 * gets a downloader that will automatically follow META refresh tags
		 * Will only do a single level of indirection
		 * @param downloader
		 * @return
		 */

	public ResourceDownloader
	getMetaRefreshDownloader(
		ResourceDownloader			downloader );

		/**
		 * Given a downloader that will download a torrent, this will download
		 * the torrent data itself. Note that the torrent MUST contain only a
		 * single file (although a future enhancement may return a ZIP input stream
		 * for multi-file torrents)
		 * @param downloader
		 * @param persistent whether or not the d/l will be retained over az stop/start
		 * @return
		 */

	public ResourceDownloader
	getTorrentDownloader(
		ResourceDownloader			downloader,
		boolean						persistent );

		/**
		 * Download a torrent's data to the given download location
		 * @param downloader
		 * @param persistent
		 * @param download_directory
		 * @return
		 */

	public ResourceDownloader
	getTorrentDownloader(
		ResourceDownloader			downloader,
		boolean						persistent,
		File						download_directory );

		/**
		 * Returns a downloader that does something sensible based on the url suffix.
		 * In particular will return a torrent downloader if the URL ends with ".torrent"
		 * The decision is made based on a random child downloader, so don't mix URL
		 * suffixes below this point in the hierarchy
		 * @param url
		 * @return
		 */

	public ResourceDownloader
	getSuffixBasedDownloader(
		ResourceDownloader			downloader );

		/**
		 * @param url
		 * @param postData
		 * @return
		 */

	ResourceDownloader
	create(
		URL 		url,
		String 		post_data );

	ResourceDownloader
	create(
		URL 		url,
		String 		post_data,
		Proxy		proxy );

	ResourceDownloader
	create(
		URL 		url,
		byte[] 		post_data );
}
