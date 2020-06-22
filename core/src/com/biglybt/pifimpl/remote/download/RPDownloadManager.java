/*
 * File    : PRDownloadManager.java
 * Created : 28-Jan-2004
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

package com.biglybt.pifimpl.remote.download;

/**
 * @author parg
 *
 */

import java.io.File;
import java.net.URL;
import java.util.Map;

import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.download.*;
import com.biglybt.pif.download.savelocation.DefaultSaveLocationManager;
import com.biglybt.pif.download.savelocation.SaveLocationManager;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;
import com.biglybt.pifimpl.remote.torrent.RPTorrent;

public class
RPDownloadManager
	extends		RPObject
	implements 	DownloadManager
{
	protected transient DownloadManager		delegate;

	public static RPDownloadManager
	create(
		DownloadManager		_delegate )
	{
		RPDownloadManager	res =(RPDownloadManager)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPDownloadManager( _delegate );
		}

		return( res );
	}

	protected
	RPDownloadManager(
		DownloadManager		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (DownloadManager)_delegate;
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		return( _fixupLocal());
	}


	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String		method 	= request.getMethod();
		Object[]	params	= request.getParams();

		if ( method.equals( "getDownloads")){

			Download[]	downloads = delegate.getDownloads();

				// unfortunately downloads with broken torrents can exist and have no associated
				// Torrent. Easiest fix here is to filter them out.

			RPDownload[]	res = new RPDownload[downloads.length];

			for (int i=0;i<res.length;i++){

				res[i] = RPDownload.create( downloads[i]);
			}

			return( new RPReply( res ));

		}else if ( method.equals( "getDownloads[boolean]")){

			Download[]	downloads = delegate.getDownloads(((Boolean)request.getParams()[0]).booleanValue());

			RPDownload[]	res = new RPDownload[downloads.length];

			for (int i=0;i<res.length;i++){

				res[i] = RPDownload.create( downloads[i]);
			}

			return( new RPReply( res ));

		}else if ( method.equals( "addDownload[Torrent]" )){

			try{
				RPTorrent	torrent = (RPTorrent)request.getParams()[0];

				Download res = delegate.addDownload((Torrent)torrent._setLocal());

				return( new RPReply( RPDownload.create(res)));

			}catch( DownloadException e ){

				throw( new RPException("DownloadManager::addDownload failed", e ));
			}

		}else if ( method.equals( "addDownload[Torrent,String,String]" )){

			try{
				RPTorrent	torrent = (RPTorrent)request.getParams()[0];
				File		f1 = params[1]==null?null:FileUtil.newFile((String)params[1]);
				File		f2 = params[2]==null?null:FileUtil.newFile((String)params[2]);

				Download res = delegate.addDownload((Torrent)torrent._setLocal(), f1, f2 );

				return( new RPReply( RPDownload.create(res)));

			}catch( DownloadException e ){

				throw( new RPException("DownloadManager::addDownload failed", e ));
			}

		}else if ( method.equals( "addDownload[URL]" )){

			try{
				delegate.addDownload((URL)request.getParams()[0]);

			}catch( DownloadException e ){

				throw( new RPException("DownloadManager::addDownload failed", e ));
			}

			return( new RPReply( null ));

		}else if ( method.equals( "pauseDownloads")){

			delegate.pauseDownloads();

			return( null );

		}else if ( method.equals( "resumeDownloads")){

			delegate.resumeDownloads();

			return( null );

		}else if ( method.equals( "stopAllDownloads")){

			delegate.stopAllDownloads();

			return( null );

		}else if ( method.equals( "startAllDownloads")){

			delegate.startAllDownloads();

			return( null );
		}

		throw( new RPException( "Unknown method: " + method ));
	}

		// ***********************************************************************************8

	@Override
	public void
	addDownload(
		File 	torrent_file )

		throws DownloadException
	{
		notSupported();
	}

	@Override
	public void
	addDownload(
		URL		url,
		URL		referer)
	{
		notSupported();
	}

	@Override
	public void
	addDownload(
		URL		url )

		throws DownloadException
	{
		_dispatcher.dispatch( new RPRequest( this, "addDownload[URL]", new Object[]{url} )).getResponse();
	}

	@Override
	public void
	addDownload(
		URL			url,
		boolean		auto_download )

		throws DownloadException
	{
		notSupported();
	}

	@Override
	public void
	addDownload(
		URL 		url,
		Map 		request_properties)
	{
		notSupported();
	}

	@Override
	public Download
	addDownload(
		Torrent		torrent )

		throws DownloadException
	{
		try{
			RPDownload	res = (RPDownload)_dispatcher.dispatch( new RPRequest( this, "addDownload[Torrent]", new Object[]{torrent})).getResponse();

			res._setRemote( _dispatcher );

			return( res );

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
	}


	@Override
	public Download
	addDownload(
		Torrent		torrent,
		File		torrent_location,
		File		data_location )

		throws DownloadException
	{
		try{
			RPDownload	res = (RPDownload)_dispatcher.dispatch(
					new RPRequest( this, "addDownload[Torrent,String,String]",
							new Object[]{
								torrent,
								torrent_location==null?null:torrent_location.toString(),
								data_location==null?null:data_location.toString(),
							})).getResponse();

			res._setRemote( _dispatcher );

			return( res );

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public Download
	addDownloadStopped(
		Torrent		torrent,
		File		torrent_location,
		File		data_location )

		throws DownloadException
	{
		notSupported();

		return( null );
	}

	@Override
	public Download
	addNonPersistentDownload(
		Torrent		torrent,
		File		torrent_location,
		File		data_location )

		throws DownloadException
	{
		notSupported();

		return( null );
	}

	@Override
	public Download
	addNonPersistentDownloadStopped(
		Torrent torrent,
		File torrentLocation,
		File dataLocation)

		throws DownloadException
	{
		notSupported();

		return( null );
	}

	@Override
	public void
	clearNonPersistentDownloadState(
		byte[] hash)
	{
		notSupported();
	}

	@Override
	public Download
	getDownload(
		Torrent		torrent )
	{
		notSupported();

		return( null );
	}

	@Override
	public Download
	getDownload(
		byte[]		hash )
	{
		notSupported();

		return( null );
	}

	@Override
	public Download[]
	getDownloads()
	{
		RPDownload[]	res = (RPDownload[])_dispatcher.dispatch( new RPRequest( this, "getDownloads", null )).getResponse();

		for (int i=0;i<res.length;i++){

			res[i]._setRemote( _dispatcher );
		}

		return( res );
	}

	@Override
	public Download[]
	getDownloads(boolean bSort)
	{
		RPDownload[]	res = (RPDownload[])_dispatcher.dispatch( new RPRequest( this, "getDownloads[boolean]", new Object[]{
			Boolean.valueOf(bSort)
		} )).getResponse();

		for (int i=0;i<res.length;i++){

			res[i]._setRemote( _dispatcher );
		}

		return( res );
	}

	@Override
	public void
	pauseDownloads()
	{
		_dispatcher.dispatch( new RPRequest( this, "pauseDownloads", null )).getResponse();
	}

	@Override
	public boolean
	canPauseDownloads()
	{
		notSupported();

		return false;
	}

	@Override
	public void
	resumeDownloads()
	{
		_dispatcher.dispatch( new RPRequest( this, "resumeDownloads", null )).getResponse();
	}

	@Override
	public boolean
	canResumeDownloads()
	{
		notSupported();

		return false;
	}

	@Override
	public void
	startAllDownloads()
	{
		_dispatcher.dispatch( new RPRequest( this, "startAllDownloads", null )).getResponse();
	}

	@Override
	public void
	stopAllDownloads()
	{
		_dispatcher.dispatch( new RPRequest( this, "stopAllDownloads", null )).getResponse();
	}

	@Override
	public DownloadManagerStats
	getStats()
	{
		notSupported();

		return( null );
	}

	@Override
	public boolean
	isSeedingOnly()
	{
		notSupported();

		return( false );
	}

	@Override
	public void
	addListener(
		DownloadManagerListener	l )
	{
		notSupported();
	}

	@Override
	public void addListener(DownloadManagerListener	l, boolean notify) {
		notSupported();
	}

	@Override
	public void
	removeListener(
		DownloadManagerListener	l )
	{
		notSupported();
	}

	@Override
	public void removeListener(DownloadManagerListener	l, boolean notify) {
		notSupported();
	}

	@Override
	public void
	addDownloadWillBeAddedListener(
		DownloadWillBeAddedListener		listener )
	{
		notSupported();
	}

	@Override
	public void
	removeDownloadWillBeAddedListener(
		DownloadWillBeAddedListener		listener )
	{
		notSupported();
	}

	@Override
	public DownloadEventNotifier getGlobalDownloadEventNotifier() {
		notSupported();
		return null;
	}

	@Override
	public void setSaveLocationManager(SaveLocationManager manager) {
		notSupported();
	}

	@Override
	public SaveLocationManager getSaveLocationManager() {
		notSupported();
		return null;
	}

	@Override
	public DefaultSaveLocationManager getDefaultSaveLocationManager() {
		notSupported();
		return null;
	}

	@Override
	public DownloadStub[]
	getDownloadStubs()
	{
		notSupported();

		return( null );
	}

	@Override
	public int
	getDownloadStubCount()
	{
		notSupported();

		return 0;
	}

	@Override
	public DownloadStub
	lookupDownloadStub(
		byte[] hash)
	{
		notSupported();

		return( null );
	}

	@Override
	public void addDownloadStubListener(DownloadStubListener l, boolean inform_of_current ){
		notSupported();
	}

	@Override
	public void removeDownloadStubListener(DownloadStubListener l ){
		notSupported();
	}
}
