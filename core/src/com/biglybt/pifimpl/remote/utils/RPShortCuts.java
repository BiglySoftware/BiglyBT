/*
 * Created on 10-May-2004
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

package com.biglybt.pifimpl.remote.utils;

/**
 * @author parg
 *
 */

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadStats;
import com.biglybt.pif.utils.ShortCuts;
import com.biglybt.pifimpl.remote.*;
import com.biglybt.pifimpl.remote.download.RPDownload;
import com.biglybt.pifimpl.remote.download.RPDownloadStats;

public class
RPShortCuts
	extends		RPObject
	implements 	ShortCuts
{
	protected transient ShortCuts		delegate;

		// don't change these field names as they are visible on XML serialisation

	public static RPShortCuts
	create(
		ShortCuts		_delegate )
	{
		RPShortCuts	res =(RPShortCuts)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPShortCuts( _delegate );
		}

		return( res );
	}

	protected
	RPShortCuts(
		ShortCuts		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (ShortCuts)_delegate;
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		Object res = _fixupLocal();

		return( res );
	}

	@Override
	public void
	_setRemote(
		RPRequestDispatcher		_dispatcher )
	{
		super._setRemote( _dispatcher );
	}

	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String		method 	= request.getMethod();
		Object[]	params	= request.getParams();

		if ( method.equals( "getDownload[byte[]]")){

			try{
				return( new RPReply( RPDownload.create( delegate.getDownload((byte[])params[0]))));

			}catch( DownloadException e ){

				return( new RPReply(e));
			}
		}else if ( method.equals( "getDownloadStats[byte[]]")){

				try{
					return( new RPReply( RPDownloadStats.create( delegate.getDownloadStats((byte[])params[0]))));

				}catch( DownloadException e ){

					return( new RPReply(e));
				}
		}else if ( method.equals( "restartDownload[byte[]]")){

			try{
				delegate.restartDownload((byte[])params[0]);

			}catch( DownloadException e ){

				return( new RPReply(e));
			}

			return( null );

		}else if ( method.equals( "stopDownload[byte[]]")){

			try{
				delegate.stopDownload((byte[])params[0]);

			}catch( DownloadException e ){

				return( new RPReply(e));
			}

			return( null );

		}else if ( method.equals( "removeDownload[byte[]]")){

			try{
				delegate.removeDownload((byte[])params[0]);

			}catch( Throwable e ){

				return( new RPReply(e));
			}

			return( null );
		}

		throw( new RPException( "Unknown method: " + method ));
	}

		// ***************************************************

	@Override
	public Download
	getDownload(
		byte[]		hash )

		throws DownloadException
	{
		try{
			RPDownload	res = (RPDownload)_dispatcher.dispatch( new RPRequest( this, "getDownload[byte[]]", new Object[]{hash})).getResponse();

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
	public DownloadStats
	getDownloadStats(
		byte[]		hash )

		throws DownloadException
	{
		try{
			RPDownloadStats	res = (RPDownloadStats)_dispatcher.dispatch( new RPRequest( this, "getDownloadStats[byte[]]", new Object[]{hash})).getResponse();

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
	public void
	restartDownload(
		byte[]		hash )

		throws DownloadException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "restartDownload[byte[]]", new Object[]{hash})).getResponse();

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public void
	stopDownload(
		byte[]		hash )

		throws DownloadException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "stopDownload[byte[]]", new Object[]{hash})).getResponse();

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
	}

	@Override
	public void
	removeDownload(
		byte[]		hash )

		throws DownloadException
	{
		try{
			_dispatcher.dispatch( new RPRequest( this, "removeDownload[byte[]]", new Object[]{hash})).getResponse();

		}catch( RPException e ){

			if ( e.getCause() instanceof DownloadException ){

				throw((DownloadException)e.getCause());
			}

			throw( e );
		}
	}
}