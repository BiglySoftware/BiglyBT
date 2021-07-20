/*
 * Created on Jul 10, 2009
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.devices.DeviceManagerException;
import com.biglybt.core.devices.TranscodeException;
import com.biglybt.core.devices.TranscodeFile;
import com.biglybt.core.devices.TranscodeTargetListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;


public class
DeviceMediaRendererManual
	extends DeviceMediaRendererImpl
{
	static final Object	COPY_ERROR_KEY 		= new Object();
	private static final Object	COPY_PENDING_KEY 	= new Object();

	private boolean				can_copy_to_folder	= true;
	private boolean				copy_outstanding;
	private boolean				copy_outstanding_set;
	private AEThread2			copy_thread;
	private AESemaphore			copy_sem = new AESemaphore( "Device:copy" );
	private AsyncDispatcher		async_dispatcher = new AsyncDispatcher( 5000 );

	protected
	DeviceMediaRendererManual(
		DeviceManagerImpl	_manager,
		String				_uid,
		String				_classification,
		boolean				_manual,
		String				_name )
	{
		super( _manager, _uid, _classification, _manual, _name );
	}

	protected
	DeviceMediaRendererManual(
		DeviceManagerImpl	_manager,
		Map					_map )

		throws IOException
	{
		super(_manager, _map );
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
					updateStatus( file );
				}

				@Override
				public void
				fileChanged(
					TranscodeFile		file,
					int					type,
					Object				data )
				{
					updateStatus( file );
				}

				@Override
				public void
				fileRemoved(
					TranscodeFile		file )
				{
						// do this to pick up change in copy-to-device state caused by removal

					setCopyOutstanding();
				}

				private void
				updateStatus(
					TranscodeFile		file )
				{
					if ( file.isComplete() && !file.isCopiedToDevice()){

						setCopyOutstanding();
					}
				}
			});
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
	public boolean
	canFilterFilesView()
	{
		return( false );
	}

	@Override
	public boolean
	isBrowsable()
	{
		return( false );
	}

	@Override
	public boolean
	canCopyToFolder()
	{
		return( can_copy_to_folder );
	}

	@Override
	public void
	setCanCopyToFolder(
		boolean		can )
	{
		can_copy_to_folder = can;

		if ( !can ){

			setPersistentBooleanProperty( PP_COPY_OUTSTANDING, false );

			synchronized( this ){
				copy_outstanding 		= false;
				copy_outstanding_set	= false;
			}
		}
	}

	@Override
	public File
	getCopyToFolder()
	{
		String str = getPersistentStringProperty( PP_COPY_TO_FOLDER, null );

		if ( str == null ){

			return( null );
		}

		return( new File( str ));
	}

	@Override
	public void
	setCopyToFolder(
		File		file )
	{
		setPersistentStringProperty( PP_COPY_TO_FOLDER, file==null?null:file.getAbsolutePath());

		if ( getAutoCopyToFolder()){

			setCopyOutstanding();
		}
	}

	@Override
	public boolean
	isLivenessDetectable()
	{
		return( getPersistentBooleanProperty( PP_LIVENESS_DETECTABLE, false ));
	}

	public void
	setLivenessDetectable(
		boolean	b )
	{
		setPersistentBooleanProperty( PP_LIVENESS_DETECTABLE, true );
	}

	@Override
	public int
	getCopyToFolderPending()
	{
		if ( !can_copy_to_folder ){

			return( 0 );
		}

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
	public boolean
	getAutoCopyToFolder()
	{
		return( getPersistentBooleanProperty( PP_AUTO_COPY, PP_AUTO_COPY_DEFAULT  ));
	}

	@Override
	public void
	setAutoCopyToFolder(
		boolean		auto )
	{
		setPersistentBooleanProperty( PP_AUTO_COPY, auto );

		setCopyOutstanding();
	}

	@Override
	public void
	manualCopy()

		throws DeviceManagerException
	{
		if ( getAutoCopyToFolder()){

			throw( new DeviceManagerException( "Operation prohibited - auto copy enabled" ));
		}

		doCopy();
	}

	protected void
	setCopyOutstanding()
	{
		if ( !can_copy_to_folder ){

			return;
		}

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
	isAudioCompatible(
		TranscodeFile		transcode_file )
	{
		if ( getDeviceClassification().equals( "sony.PSP" )){

			try{
				File file = transcode_file.getSourceFile().getFile();

				if ( file.exists()){

					String name = file.getName().toLowerCase();

					if ( name.endsWith( ".mp3" ) || name.endsWith( ".wma" )){

						((TranscodeFileImpl)transcode_file).setCopyToFolderOverride( ".." + File.separator + "MUSIC" );

						return( true );
					}
				}
			}catch( Throwable e ){

				log( "audio compatible check failed", e );
			}
		}

		return( false );
	}

	protected void
	performCopy()
	{
		if ( !can_copy_to_folder ){

			return;
		}

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

			if ( copy_sem.reserve( 10*1000 )){

				while( copy_sem.reserveIfAvailable());
			}

			boolean	auto_copy = getAutoCopyToFolder();

			boolean	nothing_to_do = false;

			synchronized( this ){

				if ( !auto_copy ){

					copy_thread = null;

					nothing_to_do = true;

				}else{

					copy_outstanding_set = false;
				}
			}

			if ( nothing_to_do ){

				setError( COPY_ERROR_KEY, null );

				int pending = getCopyToFolderPending();

				if ( pending == 0 ){

					setInfo( COPY_PENDING_KEY, null );

				}else{

					String str = MessageText.getString( "devices.info.copypending", new String[]{ String.valueOf( pending ) });

					setInfo( COPY_PENDING_KEY, str );
				}
				return;
			}


			if ( doCopy()){

				break;
			}
		}
	}

	protected boolean
	doCopy()
	{
		if ( !can_copy_to_folder ){

			return( true );
		}

		setInfo( COPY_PENDING_KEY, null );


		File	copy_to = getCopyToFolder();

		List<TranscodeFileImpl>	to_copy = new ArrayList<>();

		boolean	borked = false;

		TranscodeFileImpl[] files = getFiles();

		int	pending = 0;

		for ( TranscodeFileImpl file: files ){

			if ( file.isComplete() && !file.isCopiedToDevice()){

				pending++;

				if ( file.getCopyToDeviceFails() < 3 ){

					to_copy.add( file );

				}else{

					String info = (String)file.getTransientProperty( COPY_ERROR_KEY );

					setError( COPY_ERROR_KEY, MessageText.getString( "device.error.copyfail") + (info==null?"":(" - " + info)));

					borked = true;
				}
			}
		}

		boolean	try_copy = false;

		if ( to_copy.size() > 0 ){

				// handle case where device is auto-detectable and copy-to is missing

			if ( isLivenessDetectable() && !isAlive() && ( copy_to == null || !copy_to.exists())){

				String str = MessageText.getString( "devices.info.copypending2", new String[]{ String.valueOf( pending ) });

				setInfo( COPY_PENDING_KEY, str );

				borked = true;

			}else{
				setInfo( COPY_PENDING_KEY, null );

				boolean	sub_borked = false;

				if ( copy_to == null ){

					setError( COPY_ERROR_KEY, MessageText.getString( "device.error.copytonotset" ));

					sub_borked = true;

				}else if ( !copy_to.exists()){

					// due to the two-phase mount of android devices and the fact that the
					// copy-to location is %root$/videos, the root might exist but the sub-folder
					// not.

					File parent = copy_to.getParentFile();

					if ( parent != null && parent.canWrite()){

						copy_to.mkdir();
					}

					if ( !copy_to.exists()){

						setError( COPY_ERROR_KEY, MessageText.getString( "device.error.mountrequired", new String[]{copy_to.getAbsolutePath()}));

						sub_borked = true;
					}

					//setError( COPY_ERROR_KEY, MessageText.getString( "device.error.copytomissing", new String[]{copy_to.getAbsolutePath()}));
					//sub_borked = true;
				}

				if ( !sub_borked ){

					if ( !copy_to.canWrite()){

						setError( COPY_ERROR_KEY, MessageText.getString( "device.error.copytonowrite", new String[]{copy_to.getAbsolutePath()}));

						sub_borked = true;

					}else{

						try_copy = true;

						setError( COPY_ERROR_KEY, null );
					}
				}

				borked = borked | sub_borked;
			}
		} else {

			setInfo( COPY_PENDING_KEY, null );
		}

		synchronized( this ){

				// all done, tidy up and exit

			if ( to_copy.size() == 0 && !copy_outstanding_set && !borked ){

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

				return( true );
			}
		}

		if ( try_copy ){

			try{
				setBusy( true );

				for ( TranscodeFileImpl transcode_file: to_copy ){

					try{
						transcode_file.setCopyingToDevice( true );

						File	file = transcode_file.getTargetFile().getFile();

						File	target = new File( copy_to, file.getName());

						String override_str = transcode_file.getCopyToFolderOverride();

						if ( override_str != null ){

							File override_dir = new File( copy_to, override_str );

							if ( override_dir.exists()){

								target = new File( override_dir, file.getName());
							}
						}

						try{
							FileUtil.copyFileWithException( file, target, null );

							log( "Copied file '" + file + ": to " + copy_to );

							transcode_file.setCopiedToDevice( true );

						}catch( Throwable e ){

							copy_to.delete();

							transcode_file.setCopyToDeviceFailed();

							transcode_file.setTransientProperty( COPY_ERROR_KEY, Debug.getNestedExceptionMessage( e ));

							log( "Failed to copy file " + file, e );
						}
					}catch( TranscodeException e ){

						// file has been deleted
					}
				}
			}finally{

				setBusy( false );
			}
		}

		return( false );
	}

	@Override
	public boolean
	isExportable()
	{
		return( true );
	}

	@Override
	protected void
	getDisplayProperties(
		List<String[]>	dp )
	{
		super.getDisplayProperties( dp );

		addDP( dp, "devices.copy.pending", copy_outstanding );
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		super.generate( writer );

		try{
			writer.indent();

			writer.println( "auto_copy=" + getAutoCopyToFolder() + ", copy_to=" + getCopyToFolder() + ", copy_os=" + copy_outstanding );

		}finally{

			writer.exdent();
		}
	}

	// @see com.biglybt.core.devices.impl.DeviceImpl#getStatus()
	@Override
	public String getStatus() {
		String s = super.getStatus();

		if (COConfigurationManager.getIntParameter("User Mode") > 0 && getCopyToFolder() != null) {
			s += " (" + getCopyToFolder().getPath() + ")";
		}

		return s;
	}
}
