/*
 * Created on Feb 10, 2009
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.devices.impl;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.biglybt.core.devices.*;
import com.biglybt.core.devices.DeviceManager.UnassociatedDevice;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ipc.IPCInterface;

public class
DeviceiTunes
	extends DeviceMediaRendererImpl
	implements DeviceMediaRenderer
{
	private static final String UID = "a5d7869e-1ab9-6098-fef9-88476d988455";

	private static final Object	ERRROR_KEY_ITUNES 	= new Object();
	private static final Object	COPY_PENDING_KEY 	= new Object();

	private static final int INSTALL_CHECK_PERIOD	= 60*1000;
	private static final int RUNNING_CHECK_PERIOD	= 30*1000;
	private static final int DEVICE_CHECK_PERIOD	= 10*1000;

	private static final int INSTALL_CHECK_TICKS	= INSTALL_CHECK_PERIOD / DeviceManagerImpl.DEVICE_UPDATE_PERIOD;
	private static final int RUNNING_CHECK_TICKS	= RUNNING_CHECK_PERIOD / DeviceManagerImpl.DEVICE_UPDATE_PERIOD;
	private static final int DEVICE_CHECK_TICKS		= DEVICE_CHECK_PERIOD / DeviceManagerImpl.DEVICE_UPDATE_PERIOD;

	static final Object	COPY_ERROR_KEY = new Object();

	private PluginInterface		itunes;

	private volatile boolean				is_installed;
	private volatile boolean				is_running;

	private boolean				copy_outstanding;
	private boolean				copy_outstanding_set;
	private AEThread2			copy_thread;
	AESemaphore			copy_sem = new AESemaphore( "Device:copy" );
	private AsyncDispatcher		async_dispatcher = new AsyncDispatcher( 5000 );

	private long				last_update_fail;
	private int					consec_fails;

	private volatile boolean	manual_copy_activated;

	protected
	DeviceiTunes(
		DeviceManagerImpl	_manager,
		PluginInterface		_itunes )
	{
		super( _manager, UID, "iTunes", true );

		itunes	= _itunes;
	}

	protected
	DeviceiTunes(
		DeviceManagerImpl	_manager,
		Map					_map )

		throws IOException
	{
		super( _manager, _map );
	}

	@Override
	protected boolean
	updateFrom(
		DeviceImpl		_other,
		boolean			_is_alive )
	{
		if ( !super.updateFrom( _other, _is_alive )){

			return( false );
		}

		if ( !( _other instanceof DeviceiTunes )){

			Debug.out( "Inconsistent" );

			return( false );
		}

		DeviceiTunes other = (DeviceiTunes)_other;

		itunes = other.itunes;

		return( true );
	}

	@Override
	protected void
	initialise()
	{
		super.initialise();

		if ( getPersistentBooleanProperty( PP_COPY_OUTSTANDING, false )){

			setCopyOutstanding();
		}

		addListener(
			new TranscodeTargetListener()
			{
				@Override
				public void
				fileAdded(
					TranscodeFile		file )
				{
					if ( file.isComplete() && !file.isCopiedToDevice()){

						setCopyOutstanding();
					}
				}

				@Override
				public void
				fileChanged(
					TranscodeFile		file,
					int					type,
					Object				data )
				{
					if ( file.isComplete() && !file.isCopiedToDevice()){

						setCopyOutstanding();
					}
				}

				@Override
				public void
				fileRemoved(
					TranscodeFile		file )
				{
					copy_sem.release();
				}
			});
	}

	@Override
	protected String
	getDeviceClassification()
	{
		return( "apple." );
	}

	@Override
	public int
	getRendererSpecies()
	{
		return( RS_ITUNES );
	}

	@Override
	public InetAddress
	getAddress()
	{
		return( null );
	}

	@Override
	public boolean
	canRemove()
	{
			// no user-initiated removal, they need to uninstall the plugin

		return( false );
	}

	@Override
	public boolean
	isLivenessDetectable()
	{
		return( true );
	}

	@Override
	public URL
	getWikiURL()
	{
		return UrlUtils.getRawURL(Wiki.DEVICES_ITUNES_TIPS);
	}

	@Override
	protected void
	destroy()
	{
		super.destroy();
	}

	@Override
	protected void
	updateStatus(
		int		tick_count )
	{
		super.updateStatus( tick_count );

		updateStatusSupport( tick_count );

		if ( is_installed && is_running ){

			alive();

		}else{

			dead();
		}
	}

	protected void
	updateStatusSupport(
		int		tick_count )
	{
		if ( itunes == null ){

			return;
		}

		if ( !is_installed ){

			if ( tick_count % INSTALL_CHECK_TICKS == 0 ){

				updateiTunesStatus();

				return;
			}
		}

		if ( !is_running ){

			if ( tick_count % RUNNING_CHECK_TICKS == 0 ){

				updateiTunesStatus();

				return;
			}
		}

		if ( tick_count % DEVICE_CHECK_TICKS == 0 ){

			updateiTunesStatus();
		}
	}

	protected void
	updateiTunesStatus()
	{
		if ( getManager().isClosing()){

			return;
		}

		IPCInterface	ipc = itunes.getIPC();

		try{
			Map<String,Object> properties = (Map<String,Object>)ipc.invoke( "getProperties", new Object[]{} );

			is_installed = (Boolean)properties.get( "installed" );

			boolean	was_running = is_running;

			is_running	 = (Boolean)properties.get( "running" );

			if ( is_running && !was_running ){

				copy_sem.release();
			}

			if ( !( is_installed || is_running )){

				last_update_fail = 0;
			}

			String	info = null;

			if ( getCopyToDevicePending() > 0 ){

				if ( !is_installed ){

					info = MessageText.getString( "device.itunes.install" );

				}else if ( !is_running ){

					if ( !getAutoStartDevice()){

						info = MessageText.getString( "device.itunes.start" );
					}
				}
			}

			setInfo( ERRROR_KEY_ITUNES, info );

			Throwable error = (Throwable)properties.get( "error" );

			if ( error != null ){

				throw( error );
			}

			/*
			List<Map<String,Object>> sources = (List<Map<String,Object>>)properties.get( "sources" );

			if ( sources != null ){

				for ( Map<String,Object> source: sources ){

					System.out.println( source );
				}
			}
			*/

			last_update_fail 	= 0;
			consec_fails		= 0;

			setError( ERRROR_KEY_ITUNES, null );

		}catch( Throwable e ){

			long	now = SystemTime.getMonotonousTime();

			consec_fails++;

			if ( last_update_fail == 0 ){

				last_update_fail = now;

			}else if ( now - last_update_fail > 60*1000 && consec_fails >= 3 ){

				setError( ERRROR_KEY_ITUNES, MessageText.getString( "device.itunes.install_problem" ));
			}

			log( "iTunes IPC failed", e );
		}
	}

	@Override
	public boolean
	canCopyToDevice()
	{
		return( true );
	}

	@Override
	public boolean
	getAutoCopyToDevice()
	{
			// default is true for itunes

		return( getPersistentBooleanProperty( PP_AUTO_COPY, true  ));
	}

	@Override
	public void
	setAutoCopyToDevice(
		boolean		auto )
	{
		setPersistentBooleanProperty( PP_AUTO_COPY, auto );

		setCopyOutstanding();
	}

	@Override
	public int
	getCopyToDevicePending()
	{
		synchronized( this ){

			if ( !copy_outstanding ){

				return( 0 );
			}
		}

		TranscodeFileImpl[] files = getFiles();

		int result = 0;

		for ( TranscodeFileImpl file: files ){

			if ( file.isComplete() && !file.isCopiedToDevice()){

				result++;
			}
		}

		return( result );
	}

	@Override
	public void
	manualCopy()

		throws DeviceManagerException
	{
		if ( getAutoCopyToDevice()){

			throw( new DeviceManagerException( "Operation prohibited - auto copy enabled" ));
		}

		manual_copy_activated = true;

		setCopyOutstanding();
	}

	protected void
	setCopyOutstanding()
	{
		synchronized( this ){

			copy_outstanding_set = true;

			if ( copy_thread == null ){

				copy_thread =
					new AEThread2( "Device:copier", true )
					{
						@Override
						public void
						run()
						{
							performCopy();
						}
					};

				copy_thread.start();
			}

			copy_sem.release();
		}
	}

	@Override
	public boolean
	canAutoStartDevice()
	{
		return( true );
	}

	@Override
	public boolean
	getAutoStartDevice()
	{
		return( getPersistentBooleanProperty( PP_AUTO_START, PR_AUTO_START_DEFAULT ));
	}

	@Override
	public void
	setAutoStartDevice(
		boolean		auto )
	{
		setPersistentBooleanProperty( PP_AUTO_START, auto );

		if ( auto ){

			copy_sem.release();
		}
	}

	@Override
	public boolean
	canAssociate()
	{
		return( false );
	}

	@Override
	public boolean
	canRestrictAccess()
	{
		return( false );
	}

	@Override
	public void
	associate(
		UnassociatedDevice	assoc )
	{
	}

	protected void
	performCopy()
	{
		synchronized( this ){

			copy_outstanding = true;

			async_dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						setPersistentBooleanProperty( PP_COPY_OUTSTANDING, true );
					}
				});
		}

		while( true ){

			if ( copy_sem.reserve( 60*1000 )){

				while( copy_sem.reserveIfAvailable());
			}

			if ( !getAutoCopyToDevice()){

				if ( manual_copy_activated ){

					manual_copy_activated = false;

				}else{

					TranscodeFileImpl[] files = getFiles();

					int to_copy = 0;

					for ( TranscodeFileImpl file: files ){

						if ( file.isComplete() && !file.isCopiedToDevice()){

							to_copy++;
						}
					}

					if ( to_copy == 0 ){

						setInfo( COPY_PENDING_KEY, null );
					}else{

						String str = MessageText.getString( "devices.info.copypending3", new String[]{ String.valueOf( to_copy ) });

						setInfo( COPY_PENDING_KEY, str );
					}

					continue;
				}
			}

			setInfo( COPY_PENDING_KEY, null );

			boolean	auto_start = getAutoStartDevice();

			synchronized( this ){

				if ( itunes == null || ( !is_running && !( auto_start && is_installed ))){

					if ( !( copy_outstanding || copy_outstanding_set )){

						copy_thread = null;

						break;
					}

					continue;
				}

				copy_outstanding_set = false;
			}

			TranscodeFileImpl[] files = getFiles();

			List<TranscodeFileImpl>	to_copy = new ArrayList<>();

			boolean	borked_exist = false;

			for ( TranscodeFileImpl file: files ){

				if ( file.isComplete() && !file.isCopiedToDevice()){

					if ( file.getCopyToDeviceFails() < 3 ){

						to_copy.add( file );

					}else{

						borked_exist = true;
					}
				}
			}

			if ( borked_exist ){

				setError( COPY_ERROR_KEY, MessageText.getString( "device.error.copyfail2") );
			}

			synchronized( this ){

				if ( to_copy.size() == 0 && !copy_outstanding_set && !borked_exist ){

					copy_outstanding = false;

					async_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								setError( COPY_ERROR_KEY, null );

								setPersistentBooleanProperty( PP_COPY_OUTSTANDING, false );
							}
						});

					copy_thread = null;

					break;
				}
			}

			for ( TranscodeFileImpl transcode_file: to_copy ){

				try{
					File	file = transcode_file.getTargetFile().getFile();

					try{
						IPCInterface	ipc = itunes.getIPC();

						if ( !is_running ){

							log( "Auto-starting iTunes" );
						}

						Map<String,Object> result = (Map<String,Object>)ipc.invoke( "addFileToLibrary", new Object[]{ file } );

						Throwable error = (Throwable)result.get( "error" );

						if ( error != null ){

							throw( error );
						}

						is_running = true;

						log( "Added file '" + file + ": " + result );

						transcode_file.setCopiedToDevice( true );

					}catch( Throwable e ){

						transcode_file.setCopyToDeviceFailed();

						log( "Failed to copy file " + file, e );
					}
				}catch( TranscodeException e ){

					// file has been deleted
				}
			}
		}
	}

	@Override
	public boolean
	isBrowsable()
	{
		return( false );
	}

	@Override
	public browseLocation[]
	getBrowseLocations()
	{
		return null;
	}

	@Override
	protected void
	getDisplayProperties(
		List<String[]>	dp )
	{
		super.getDisplayProperties( dp );

		if ( itunes == null ){

			addDP( dp, "devices.comp.missing", "<null>" );

		}else{

			updateiTunesStatus();

			addDP( dp, "devices.installed", is_installed );

			addDP( dp, "MyTrackerView.status.started", is_running );

			addDP( dp, "devices.copy.pending", copy_outstanding );

			addDP( dp, "devices.auto.start", getAutoStartDevice());
		}
	}

	@Override
	public String
	getStatus()
	{
		if ( is_running ){

			return( MessageText.getString( "device.itunes.status.running" ));

		}else if ( is_installed ){

			return( MessageText.getString( "device.itunes.status.notrunning" ));

		}else{

			return( MessageText.getString( "device.itunes.status.notinstalled" ));
		}
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		super.generate( writer );

		try{
			writer.indent();

			writer.println( "itunes=" + itunes + ", installed=" + is_installed + ", running=" + is_running + ", auto_start=" + getAutoStartDevice());
			writer.println( "copy_os=" + copy_outstanding + ", last_fail=" + new SimpleDateFormat().format( new Date( last_update_fail )));

		}finally{

			writer.exdent();
		}
	}
}
