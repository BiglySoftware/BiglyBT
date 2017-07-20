/*
 * Created on 16-Sep-2005
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

package com.biglybt.net.upnp.impl.services;

import com.biglybt.net.upnp.*;
import com.biglybt.net.upnp.services.UPnPOfflineDownloader;

public class
UPnPSSOfflineDownloaderImpl
	implements UPnPOfflineDownloader
{
	private UPnPServiceImpl		service;

	protected
	UPnPSSOfflineDownloaderImpl(
		UPnPServiceImpl		_service )
	{
		service = _service;
	}

	@Override
	public UPnPService
	getGenericService()
	{
		return( service );
	}

	@Override
	public long
	getFreeSpace(
		String		client_id )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "GetFreeSpace" );

		if ( act == null ){

			throw( new UPnPException( "GetFreeSpace not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			inv.addArgument( "NewClientID", client_id );

			UPnPActionArgument[]	args = inv.invoke();

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewFreeSpace")){

					return( Long.parseLong( arg.getValue()));

				}
			}

			throw( new UPnPException( "result not found" ));
		}
	}

	@Override
	public void
	activate(
		String		client_id )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "Activate" );

		if ( act == null ){

			throw( new UPnPException( "Activate not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			inv.addArgument( "NewClientID", client_id );

			UPnPActionArgument[]	args = inv.invoke();

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewStatus")){

					return;

				}
			}

			throw( new UPnPException( "status not found" ));
		}
	}

	@Override
	public String[]
	setDownloads(
		String client_id,
		String hash_list )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "SetDownloads" );

		if ( act == null ){

			throw( new UPnPException( "SetDownloads not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			inv.addArgument( "NewClientID", client_id );
			inv.addArgument( "NewTorrentHashList", hash_list );

			UPnPActionArgument[]	args = inv.invoke();

			String	result	= null;
			String	status 	= null;

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewSetDownloadsResultList")){

					result = arg.getValue();

				}else if ( name.equalsIgnoreCase("NewStatus")){

					status = arg.getValue();
				}
			}

			if ( result != null && status != null ){

				return( new String[]{ result, status });
			}

			throw( new UPnPException( "result or status not found" ));
		}
	}

	@Override
	public String
	addDownload(
		String 	client_id,
		String 	hash,
		String	torrent )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "AddDownload" );

		if ( act == null ){

			throw( new UPnPException( "AddDownload not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			inv.addArgument( "NewClientID", client_id );
			inv.addArgument( "NewTorrentHash", hash );
			inv.addArgument( "NewTorrentData", torrent );

			UPnPActionArgument[]	args = inv.invoke();

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewStatus")){

					return( arg.getValue());
				}
			}

			throw( new UPnPException( "result not found" ));
		}
	}

	@Override
	public String
	addDownloadChunked(
		String 	client_id,
		String 	hash,
		String	chunk,
		int		offset,
		int		total_size )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "AddDownloadChunked" );

		if ( act == null ){

			throw( new UPnPException( "AddDownloadChunked not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			inv.addArgument( "NewClientID", client_id );
			inv.addArgument( "NewTorrentHash", hash );
			inv.addArgument( "NewTorrentData", chunk );
			inv.addArgument( "NewChunkOffset", String.valueOf( offset ));
			inv.addArgument( "NewTotalLength", String.valueOf( total_size ));

			UPnPActionArgument[]	args = inv.invoke();

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewStatus")){

					return( arg.getValue());
				}
			}

			throw( new UPnPException( "result not found" ));
		}
	}

	@Override
	public String[]
  	updateDownload(
  		String 	client_id,
  		String 	hash,
  		String	required_map )

  		throws UPnPException
  	{
  		UPnPAction act = service.getAction( "UpdateDownload" );

  		if ( act == null ){

  			throw( new UPnPException( "UpdateDownload not supported" ));

  		}else{

  			UPnPActionInvocation inv = act.getInvocation();

  			inv.addArgument( "NewClientID", client_id );
 			inv.addArgument( "NewTorrentHash", hash );
 			inv.addArgument( "NewPieceRequiredMap", required_map );

  			UPnPActionArgument[]	args = inv.invoke();

  			String	have	= null;
  			String	status 	= null;

  			for (int i=0;i<args.length;i++){

  				UPnPActionArgument	arg = args[i];

  				String	name = arg.getName();

  				if ( name.equalsIgnoreCase("NewPieceHaveMap")){

  					have = arg.getValue();

  				}else if ( name.equalsIgnoreCase("NewStatus")){

  					status = arg.getValue();
  				}
  			}

  			if ( have != null && status != null ){

  				return( new String[]{ have, status });
  			}

  			throw( new UPnPException( "have or status not found" ));
  		}
  	}

	@Override
	public String
	removeDownload(
		String 	client_id,
		String 	hash )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "RemoveDownload" );

		if ( act == null ){

			throw( new UPnPException( "RemoveDownload not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			inv.addArgument( "NewClientID", client_id );
			inv.addArgument( "NewTorrentHash", hash );

			UPnPActionArgument[]	args = inv.invoke();

			String	status 	= null;

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewStatus")){

					status = arg.getValue();
				}
			}

			if ( status != null ){

				return( status );
			}

			throw( new UPnPException( "status not found" ));
		}
	}

	@Override
	public String[]
	startDownload(
		String 	client_id,
		String 	hash )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "StartDownload" );

		if ( act == null ){

			throw( new UPnPException( "StartDownload not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			inv.addArgument( "NewClientID", client_id );
			inv.addArgument( "NewTorrentHash", hash );

			UPnPActionArgument[]	args = inv.invoke();

			String	status 		= null;
			String	data_port	= null;

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewStatus")){

					status = arg.getValue();

				}else if ( name.equalsIgnoreCase("NewDataPort")){

					data_port = arg.getValue();
				}
			}

			if ( status != null && data_port != null ){

				return( new String[]{ data_port, status });
			}

			throw( new UPnPException( "status or data port not found" ));
		}
	}
}
