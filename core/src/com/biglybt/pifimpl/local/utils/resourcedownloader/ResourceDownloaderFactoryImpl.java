/*
 * Created on 03-May-2004
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

package com.biglybt.pifimpl.local.utils.resourcedownloader;

/**
 * @author parg
 *
 */

import java.io.File;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderDelayedFactory;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;

public class
ResourceDownloaderFactoryImpl
	implements ResourceDownloaderFactory
{

	private static final LogIDs LOGID = LogIDs.CORE;
	protected static ResourceDownloaderFactoryImpl	singleton = new ResourceDownloaderFactoryImpl();

	public static ResourceDownloaderFactory
	getSingleton()
	{
		return( singleton );
	}

	@Override
	public ResourceDownloader
	create(
		File		file )
	{
		return( new ResourceDownloaderFileImpl( null, file ));
	}


	@Override
	public ResourceDownloader
	create(
		URL		url )
	{
		if ( url.getProtocol().equalsIgnoreCase("file")){

			try{
				return( new ResourceDownloaderFileImpl( null, FileUtil.newFile( new URI( url.toString()))));

			}catch( Throwable e ){

				return( new ResourceDownloaderURLImpl( null, url ));
			}
		}else{

			return( new ResourceDownloaderURLImpl( null, url ));
		}
	}

	@Override
	public ResourceDownloader
	createWithAutoPluginProxy(
		URL					url,
		Map<String,Object>	options )
	{
		ResourceDownloader rd = create(url);
		if ( rd instanceof ResourceDownloaderURLImpl) {
			((ResourceDownloaderURLImpl)rd).setAutoPluginProxy( options );
		}
		return rd;
	}

	@Override
	public ResourceDownloader create(URL url, boolean force_no_proxy) {
		ResourceDownloader rd = create(url);
		if (force_no_proxy && rd instanceof ResourceDownloaderURLImpl) {
			((ResourceDownloaderURLImpl)rd).setForceNoProxy(force_no_proxy);
		}
		return rd;
	}

	@Override
	public ResourceDownloader create(URL url, Proxy proxy ) {
		ResourceDownloader rd = create(url);
		if (proxy != null && rd instanceof ResourceDownloaderURLImpl) {
			((ResourceDownloaderURLImpl)rd).setForceProxy(proxy);
		}
		return rd;
	}

	@Override
	public ResourceDownloader
	create(
		URL		url,
		String 	postData )
	{
		return new ResourceDownloaderURLImpl(null, url, postData.getBytes(), false, null, null);
	}

	@Override
	public ResourceDownloader create(URL url, String postData, Proxy proxy ) {
		ResourceDownloader rd = create(url,postData);
		if (proxy != null && rd instanceof ResourceDownloaderURLImpl) {
			((ResourceDownloaderURLImpl)rd).setForceProxy(proxy);
		}
		return rd;
	}

	@Override
	public ResourceDownloader
	create(
		URL		url,
		byte[]	postData)
	{
		return new ResourceDownloaderURLImpl(null, url, postData, false, null, null);
	}

	@Override
	public ResourceDownloader
	create(
		URL		url,
		String	user_name,
		String	password )
	{
		return( new ResourceDownloaderURLImpl( null, url, user_name, password ));
	}

	@Override
	public ResourceDownloader
	create(
		ResourceDownloaderDelayedFactory		factory )
	{
		return( new ResourceDownloaderDelayedImpl( null, factory ));
	}

	@Override
	public ResourceDownloader
	getRetryDownloader(
		ResourceDownloader		downloader,
		int						retry_count )
	{
		ResourceDownloader res = new ResourceDownloaderRetryImpl( null, downloader, retry_count );

		return( res );
	}

	@Override
	public ResourceDownloader
	getTimeoutDownloader(
		ResourceDownloader		downloader,
		int						timeout_millis )
	{
		ResourceDownloader res = new ResourceDownloaderTimeoutImpl( null, downloader, timeout_millis );

		return( res );
	}

	@Override
	public ResourceDownloader
	getAlternateDownloader(
		ResourceDownloader[]		downloaders )
	{
		return( getAlternateDownloader( downloaders, -1, false ));
	}

	@Override
	public ResourceDownloader
	getAlternateDownloader(
		ResourceDownloader[]		downloaders,
		int							max_to_try )
	{
		return( getAlternateDownloader( downloaders, max_to_try, false ));
	}

	@Override
	public ResourceDownloader
	getRandomDownloader(
		ResourceDownloader[]		downloaders )
	{
		return( getAlternateDownloader( downloaders, -1, true ));
	}

	@Override
	public ResourceDownloader
	getRandomDownloader(
		ResourceDownloader[]		downloaders,
		int							max_to_try )
	{
		return( getAlternateDownloader( downloaders, max_to_try, true ));
	}

	protected ResourceDownloader
	getAlternateDownloader(
		ResourceDownloader[]		downloaders,
		int							max_to_try,
		boolean						random )
	{
		ResourceDownloaderBaseImpl res = new ResourceDownloaderAlternateImpl( null, downloaders, max_to_try, random );

		boolean	anon = false;

		for ( ResourceDownloaderBaseImpl kid: res.getChildren()){

			if ( kid.isAnonymous()){

				anon = true;
			}
		}

		if ( anon ){

			try{
				res.setPropertyRecursive( ResourceDownloader.PR_BOOLEAN_ANONYMOUS, true );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( res );
	}

	@Override
	public ResourceDownloader
	getMetaRefreshDownloader(
		ResourceDownloader			downloader )
	{
		ResourceDownloader res = new ResourceDownloaderMetaRefreshImpl( null, downloader );

		return( res );
	}

	@Override
	public ResourceDownloader
	getTorrentDownloader(
		ResourceDownloader			downloader,
		boolean						persistent )
	{
		return( getTorrentDownloader( downloader, persistent, null ));
	}

	@Override
	public ResourceDownloader
	getTorrentDownloader(
		ResourceDownloader			downloader,
		boolean						persistent,
		File						download_directory )
	{
		return( new ResourceDownloaderTorrentImpl( null, downloader, persistent, download_directory ));
	}

	@Override
	public ResourceDownloader
	getSuffixBasedDownloader(
		ResourceDownloader			_downloader )
	{
		ResourceDownloaderBaseImpl	dl = (ResourceDownloaderBaseImpl)_downloader;

		URL	target = null;

		while( true ){

			List	kids = dl.getChildren();

			if ( kids.size() == 0 ){

				target = ((ResourceDownloaderURLImpl)dl).getURL();

				break;
			}

			dl = (ResourceDownloaderBaseImpl)kids.get(0);
		}

		ResourceDownloader result;

		if ( target == null ){

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "ResourceDownloader: suffix "
						+ "based downloader failed to find leaf"));

			result = _downloader;
		}else{

			if ( target.getPath().toLowerCase().endsWith(".torrent")){

				result = getTorrentDownloader( _downloader, true );

			}else{

				result = _downloader;
			}
		}

		if ( COConfigurationManager.getBooleanParameter( "update.anonymous")){

				// assumption (well currently fact) is that all suffix based downloaders are
				// the result of an update request

			try{
				((ResourceDownloaderBaseImpl)result).setPropertyRecursive( ResourceDownloader.PR_BOOLEAN_ANONYMOUS, true );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( result );
	}
}
