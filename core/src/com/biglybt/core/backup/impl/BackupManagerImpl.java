/*
 * Created on Jun 22, 2012
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


package com.biglybt.core.backup.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.backup.BackupManager;
import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.custom.CustomizationManagerFactory;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.update.UpdateInstaller;

public class
BackupManagerImpl
	implements BackupManager
{
	private static BackupManagerImpl	singleton;

	public static synchronized BackupManager
	getSingleton(
		Core core )
	{
		if ( singleton == null ){

			singleton = new BackupManagerImpl( core );
		}

		return( singleton );
	}

	private final Core core;

	private final AsyncDispatcher		dispatcher = new AsyncDispatcher();

	private boolean		first_schedule_check = true;
	private TimerEvent	backup_event;
	private long		last_auto_backup;


	volatile boolean	closing;

	private
	BackupManagerImpl(
		Core _core )
	{
		core	= _core;

		COConfigurationManager.addParameterListener(
			new String[]{
				"br.backup.auto.enable",
				"br.backup.auto.everydays",
				"br.backup.auto.retain",
			},
			new ParameterListener()
			{
				COConfigurationListener	save_listener;

				final Object	lock = this;

				@Override
				public void
				parameterChanged(
					String parameter )
				{
					synchronized( lock ){

						if ( save_listener == null ){

							save_listener =
								new COConfigurationListener()
								{
									@Override
									public void
									configurationSaved()
									{
										checkSchedule();

										COConfigurationManager.removeListener( this );

										synchronized( lock ){

											if ( save_listener == this ){

												save_listener = null;
											}
										}
									}
								};

								COConfigurationManager.addListener( save_listener );
						}
					}
				}
			});

		checkSchedule();

		core.addLifecycleListener(
			new CoreLifecycleAdapter()
			{
				@Override
				public void
				stopping(
					Core core )
				{
					closing = true;
				}
			});
	}

	@Override
	public long
	getLastBackupTime()
	{
		return( COConfigurationManager.getLongParameter( "br.backup.last.time", 0 ));

	}

	@Override
	public String
	getLastBackupError()
	{
		return( COConfigurationManager.getStringParameter( "br.backup.last.error", "" ));
	}

	void
	checkSchedule()
	{
		checkSchedule( null, false );
	}

	private void
	checkSchedule(
		final BackupListener 	_listener,
		boolean					force )
	{
		final BackupListener	listener =
			new BackupListener()
			{
				@Override
				public boolean
				reportProgress(String str)
				{
					if ( _listener != null ){

						try{

							return( _listener.reportProgress(str));

						}catch( Throwable e ){

							Debug.out( e );
						}
					}

					return( true );
				}

				@Override
				public void
				reportError(
					Throwable error)
				{
					if ( _listener != null ){

						try{

							_listener.reportError(error);

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}

				@Override
				public void
				reportComplete()
				{
					if ( _listener != null ){

						try{

							_listener.reportComplete();

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			};

		boolean	enabled = COConfigurationManager.getBooleanParameter( "br.backup.auto.enable" );

		boolean	do_backup = false;

		synchronized( this ){

			if ( backup_event != null ){

				backup_event.cancel();

				backup_event = null;
			}

			if ( first_schedule_check ){

				if ( !enabled ){

					String last_ver = COConfigurationManager.getStringParameter( "br.backup.config.info.ver", "" );

					String current_ver = Constants.BIGLYBT_VERSION;

					if ( !last_ver.equals( current_ver )){

						COConfigurationManager.setParameter( "br.backup.config.info.ver", current_ver );

						Logger.log(
							new LogAlert(
								false,
								LogAlert.AT_INFORMATION,
								MessageText.getString("br.backup.setup.info")));
					}
				}

				first_schedule_check = false;

				if ( !force ){

					if ( enabled ){

						backup_event =
							SimpleTimer.addEvent(
								"BM:startup",
								SystemTime.getCurrentTime() + 5*60*1000,
								new TimerEventPerformer()
								{
									@Override
									public void
									perform(
										TimerEvent event )
									{
										checkSchedule();
									}
								});
					}

					return;
				}
			}

			if ( !enabled ){

				listener.reportError( new Exception( "Auto-backup not enabled" ));

				return;
			}

			long	now_utc = SystemTime.getCurrentTime();

			int offset = TimeZone.getDefault().getOffset( now_utc );

			long	now_local = now_utc + offset;

			long	HOUR 	= 60*60*1000L;
			long	DAY 	= 24*HOUR;
			
			long	local_day_index 	= now_local/DAY;
			long	local_hour_index 	= now_local/HOUR;

			long	last_auto_backup_day 	= COConfigurationManager.getLongParameter( "br.backup.auto.last_backup_day", 0 );
			long	last_auto_backup_hour 	= COConfigurationManager.getLongParameter( "br.backup.auto.last_backup_hour", 0 );

			if ( last_auto_backup_day > local_day_index ){

				last_auto_backup_day = local_day_index;
			}

			if ( last_auto_backup_hour > local_hour_index ){

				last_auto_backup_hour = local_hour_index;
			}
			
			long	backup_every_days 	= COConfigurationManager.getLongParameter( "br.backup.auto.everydays" );
			long	backup_every_hours 	= COConfigurationManager.getLongParameter( "br.backup.auto.everyhours" );

			backup_every_days = Math.max( 0, backup_every_days );

			long	utc_next_backup;
			
			long	check_period;
			
			if ( backup_every_days == 0 ){

				backup_every_hours = Math.max( 1, backup_every_hours );

				utc_next_backup =  ( last_auto_backup_hour + backup_every_hours ) * HOUR;

				check_period = 10*60*1000;
				
			}else{
				
				utc_next_backup =  ( last_auto_backup_day + backup_every_days ) * DAY;
				
				check_period = 4*60*60*1000;
			}
			
			long	time_to_next_backup = utc_next_backup - now_local;

			if ( time_to_next_backup <= 0 || force ){

				if ( now_utc - last_auto_backup >= check_period || force ){

					do_backup = true;

					last_auto_backup	= now_utc;

					COConfigurationManager.setParameter( "br.backup.auto.last_backup_day", local_day_index );
					COConfigurationManager.setParameter( "br.backup.auto.last_backup_hour", local_hour_index );

				}else{

					time_to_next_backup	= check_period;
				}
			}

			if ( !do_backup ){

				time_to_next_backup = Math.max( time_to_next_backup, 60*1000 );

				listener.reportProgress( "Scheduling next backup in " + TimeFormatter.format( time_to_next_backup/1000 ));

				backup_event =
					SimpleTimer.addEvent(
						"BM:auto",
						now_utc + time_to_next_backup,
						new TimerEventPerformer()
						{
							@Override
							public void
							perform(
								TimerEvent event )
							{
								checkSchedule();
							}
						});
			}
		}

		if ( do_backup ){

			String backup_dir = COConfigurationManager.getStringParameter( "br.backup.auto.dir", "" );

			listener.reportProgress( "Auto backup starting: folder=" + backup_dir );

			final File target_dir = FileUtil.newFile( backup_dir );

			backup(
				target_dir,
				new BackupListener()
				{
					@Override
					public boolean
					reportProgress(
						String		str )
					{
						return( listener.reportProgress( str ));
					}

					@Override
					public void
					reportComplete()
					{
						try{
							System.out.println( "Auto backup completed" );

							COConfigurationManager.save();


							if ( COConfigurationManager.getBooleanParameter("br.backup.notify")){

								Logger.log(
										new LogAlert(
										true,
										LogAlert.AT_INFORMATION,
										"Backup completed at " + new Date()));
							}

							int	backup_retain = COConfigurationManager.getIntParameter( "br.backup.auto.retain" );

							backup_retain = Math.max( 1, backup_retain );

							File[] backups = target_dir.listFiles();

							List<File>	backup_dirs = new ArrayList<>();

							for ( File f: backups ){

								if ( f.isDirectory() && getBackupDirTime( f ) > 0 ){

									File	test_file = FileUtil.newFile( f, ConfigurationManager.CONFIG_FILENAME );

									if ( test_file.exists()){

										backup_dirs.add( f );
									}
								}
							}

							Collections.sort(
								backup_dirs,
								new Comparator<File>()
								{
									@Override
									public int
									compare(
										File o1,
										File o2 )
									{
										long	t1 = getBackupDirTime( o1 );
										long	t2 = getBackupDirTime( o2 );

										long res = t2 - t1;

										if ( res < 0 ){
											return( -1 );
										}else if ( res > 0 ){
											return( 1 );
										}else{
											Debug.out( "hmm: " + o1 + "/" + o2 );

											return( 0 );
										}
									}
								});

							for ( int i=backup_retain;i< backup_dirs.size();i++){

								File f = backup_dirs.get( i );

								listener.reportProgress( "Deleting old backup: " + f );

								FileUtil.recursiveDeleteNoCheck( f );
							}
						}finally{

							listener.reportComplete();

							checkSchedule();
						}
					}

					@Override
					public void
					reportError(
						Throwable 	error )
					{
						try{
							listener.reportProgress( "Auto backup failed" );

							Logger.log(
									new LogAlert(
										true,
										LogAlert.AT_ERROR,
										"Backup failed at " + new Date(),
										error ));
						}finally{

							listener.reportError( error );

							checkSchedule();
						}
					}
				});

		}else{

			listener.reportError( new Exception( "Backup not scheduled to run now" ));
		}
	}

	@Override
	public void
	runAutoBackup(
		BackupListener			listener )
	{
		checkSchedule( listener, true );
	}

	@Override
	public void
	backup(
		final File				parent_folder,
		final BackupListener	_listener )
	{
		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					BackupListener listener = new
					BackupListener()
					{
						@Override
						public boolean
						reportProgress(
							String		str )
						{
							return( _listener.reportProgress(str));
						}

						@Override
						public void
						reportComplete()
						{
							try{
								setStatus( "" );

							}finally{

								_listener.reportComplete();
							}
						}

						@Override
						public void
						reportError(
							Throwable 	error )
						{
							try{
								setStatus( Debug.getNestedExceptionMessage( error ));

							}finally{

								_listener.reportError( error );
							}
						}
					};

					backupSupport( parent_folder, listener );
				}

				void
				setStatus(
					String	error )
				{
					COConfigurationManager.setParameter( "br.backup.last.time", SystemTime.getCurrentTime());
					COConfigurationManager.setParameter( "br.backup.last.error", error );
				}
			});
	}

	private void
	checkClosing()

		throws Exception
	{
		if ( closing ){

			throw( new Exception( "operation cancelled, app is closing" ));
		}
	}

	private long[]
 	copyFiles(
 		File		from_file,
 		File		to_file )

 		throws Exception
 	{
		return( copyFilesSupport( from_file, to_file, 1 ));
 	}

	private long[]
	copyFilesSupport(
		File		from_file,
		File		to_file,
		int			depth )

		throws Exception
	{
		long	total_files		= 0;
		long	total_copied 	= 0;

		if ( depth > 32 ){

				// lazy but whatever, our config never gets this deep

			throw( new Exception( "Backup path too deep, abandoning" ));
		}

		if ( from_file.isDirectory()){

			boolean	skip = false;
			
			File parent = from_file.getParentFile();
			
			String parent_name = parent==null?"":parent.getName();
			
			String name = from_file.getName();
			
			if ( parent_name.equals( "aznettorbrowser" )){
				
				if ( name.startsWith( "browser_" )){
					
					skip = true;
				}
			}else if ( parent_name.equals( "azwebtorrent" )){
			
				if ( name.equals( "data" ) ||  name.startsWith( "browser_" )){
					
					skip = true;
				}
			}
			
			if ( !skip ){
				
				if ( !to_file.mkdirs()){
	
					throw( new Exception( "Failed to create '" + to_file.getAbsolutePath() + "'" ));
				}
	
				File[] files = from_file.listFiles();
	
				if ( files != null ){
					
					for ( File f: files ){
		
						checkClosing();
		
						long[] temp = copyFilesSupport( f, FileUtil.newFile( to_file, f.getName()), depth+1 );
		
						total_files 	+= temp[0];
						total_copied	+= temp[1];
					}
				}
			}
		}else{

			if ( !FileUtil.copyFileWithDates( from_file, to_file )){

				try{
					Thread.sleep( 5000 );

				}catch( Throwable e ){
				}

				if ( !FileUtil.copyFileWithDates( from_file, to_file )){

						// a few exceptions here (e.g. dasu plugin has a 'lock' file that breaks things)

					String name = from_file.getName().toLowerCase( Locale.US );

					if ( 	name.startsWith( ".lock" ) 		||
							name.startsWith( ".azlock" ) 	|| 	// i2phelper
							name.startsWith( "lock" ) 		|| 	// dasu
							name.equals( "stats.lck" ) 		||	// advanced stats plugin
							name.endsWith( ".saving" )		||	// intermediate file
							name.endsWith( ".gz" )			||	// most likely not interesting
							name.endsWith( ".jar" )			||	// easy to recover later
							name.endsWith( ".zip" )			||	// most likely not interesting
							name.endsWith( ".dll" )			||	// might be in use, no big deal
							name.endsWith( ".so" )			||	// might be in use, no big deal
							FileUtil.containsPathSegment(from_file, "cache", false) || // caches can be in use, no big deal
							FileUtil.containsPathSegment(from_file, "azneti2phelper" + File.separator + "netdb", false ) ||	// troublesome i2p files
							FileUtil.containsPathSegment(from_file, "azneti2phelper" + File.separator + "peerprofiles", false )	// troublesome i2p files
							){

						return( new long[]{ total_files, total_copied });
					}

					throw( new Exception( "Failed to copy file '" + from_file + "'" ));
				}
			}

			total_files++;

			total_copied = from_file.length();
		}

		return( new long[]{ total_files, total_copied });
	}

	long
	getBackupDirTime(
		File		file )
	{
		String	name = file.getName();

		int	pos = name.indexOf( "." );

		long	suffix = 0;

		if ( pos != -1 ){

			try{
				suffix = Integer.parseInt( name.substring( pos+1 ));

				name = name.substring( 0, pos );

			}catch( Throwable e ){

				return( -1 );
			}
		}

		try{
			return( new SimpleDateFormat( "yyyy-MM-dd" ).parse( name ).getTime() + suffix );

		}catch( Throwable e ){

			return( -1 );
		}
	}

	void
	backupSupport(
		File						parent_folder,
		final BackupListener		_listener )
	{
		try{
			String date_dir = new SimpleDateFormat( "yyyy-MM-dd" ).format( new Date());

			File 		backup_folder 	= null;
			boolean		ok 				= false;

			try{
				checkClosing();

				if ( 	parent_folder.getName().length() == 0 ||
						!parent_folder.isDirectory()){

					throw( new Exception( "Backup folder '" + parent_folder + "' is invalid" ));
				}

				BackupListener listener = new
					BackupListener()
					{
						@Override
						public boolean
						reportProgress(
							String		str )
						{
							if ( !_listener.reportProgress( str )){

								throw( new RuntimeException( "Operation abandoned by listener" ));
							}

							return( true );
						}

						@Override
						public void
						reportComplete()
						{
							_listener.reportComplete();
						}

						@Override
						public void
						reportError(
							Throwable 	error )
						{
							_listener.reportError( error );
						}
					};

				int	max_suffix = -1;

				String[] existing = parent_folder.list();

				if ( existing != null ){

					for ( String ex: existing ){

						if ( ex.startsWith( date_dir )){

							int pos = ex.indexOf( "." );

							if ( pos >= 0 ){

								try{
									max_suffix = Math.max( max_suffix, Integer.parseInt( ex.substring( pos+1 )));

								}catch( Throwable e ){

								}
							}else{

								if ( max_suffix == -1 ){

									max_suffix = 0;
								}
							}
						}
					}
				}

				for ( int i=max_suffix+1;i<100;i++){

					String test_dir = date_dir;

					if ( i > 0 ){

						test_dir = test_dir + "." + i;
					}

					File test_file = FileUtil.newFile( parent_folder, test_dir );

					if ( !test_file.exists()){

						backup_folder = test_file;

						backup_folder.mkdirs();

						break;
					}
				}

				if ( backup_folder == null ){

					backup_folder = FileUtil.newFile( parent_folder, date_dir );
				}

				File user_dir = FileUtil.newFile( SystemProperties.getUserPath());

				File temp_dir = backup_folder;

				while( temp_dir != null ){

					if ( temp_dir.equals( user_dir )){

						throw( new Exception( "Backup folder '" + backup_folder + "' is not permitted to be within the configuration folder '" + user_dir + "'.\r\nSelect an alternative location." ));
					}

					temp_dir = temp_dir.getParentFile();
				}

				listener.reportProgress( "Writing to " + backup_folder.getAbsolutePath());

				if ( !backup_folder.exists() && !backup_folder.mkdirs()){

					throw( new Exception( "Failed to create '" + backup_folder.getAbsolutePath() + "'" ));
				}

				listener.reportProgress( "Syncing current state" );

				core.saveState();

				try{
					listener.reportProgress( "Reading configuration data from " + user_dir.getAbsolutePath());

					File[] user_files = user_dir.listFiles();

					for ( File f: user_files ){

						checkClosing();

						String	name = f.getName();

						if ( f.isDirectory()){

							if ( 	name.equals( "cache" ) ||
									name.equals( "tmp" ) ||
									name.equals( "logs" ) ||
									name.equals( "updates" ) ||
									name.equals( "debug")){

								continue;
							}
															
							if ( name.equals( "plugins" )){
							
								if ( !COConfigurationManager.getBooleanParameter( ConfigKeys.BackupRestore.BCFG_BACKUP_PLUGINS )){

									listener.reportProgress( "Not backing up plugins due to configuration settings" );
									
									continue;
								}
							}
						}else if ( 	name.equals( ".lock" ) ||
									name.equals( ".azlock" ) ||
									name.equals( "update.properties" ) ||
									name.endsWith( ".log" )){

							continue;
						}

						File	dest_file = FileUtil.newFile( backup_folder, name );

						listener.reportProgress( "Copying '" + name  + "' ..." );

						long[]	result = copyFiles( f, dest_file );

						String	result_str = DisplayFormatters.formatByteCountToKiBEtc( result[1] );

						if ( result[0] > 1 ){

							result_str = result[0] + " files, " + result_str;
						}

						listener.reportProgress( result_str );
					}

					listener.reportComplete();

					ok	= true;

				}catch( Throwable e ){

					throw( e );
				}
			}finally{

				if ( !ok ){

					if ( backup_folder != null ){

						FileUtil.recursiveDeleteNoCheck( backup_folder );
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );
			
			_listener.reportError( e );
		}
	}

	@Override
	public void
	restore(
		final File				backup_folder,
		final BackupListener	listener )
	{
		dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						restoreSupport( backup_folder, listener );
					}
				});
	}

	private void
	addActions(
		UpdateInstaller	installer,
		File			source,
		File			target )

		throws Exception
	{
		if ( source.isDirectory()){

			File[]	files = source.listFiles();

			for ( File f: files ){

				addActions( installer, f, FileUtil.newFile( target, f.getName()));
			}
		}else{

			installer.addMoveAction(
					source.getAbsolutePath(),
					target.getAbsolutePath());
		}
	}

	private int
	patch(
		Map<String,Object>	map,
		String				from,
		String				to )
	{
		int	mods = 0;

		Iterator<Map.Entry<String,Object>> it = map.entrySet().iterator();

		Map<String,Object>	replacements = new HashMap<>();

		while( it.hasNext()){

			Map.Entry<String,Object> entry = it.next();

			String	key = entry.getKey();

			Object	value = entry.getValue();

			Object	new_value = value;

			if ( value instanceof Map ){

				mods += patch((Map)value, from, to );

			}else if ( value instanceof List ){

				mods += patch((List)value, from, to );

			}else if ( value instanceof byte[] ){

				try{
					String	str = new String((byte[])value, "UTF-8" );

					if ( str.startsWith( from )){

						new_value = to + str.substring( from.length());

						mods++;
					}
				}catch( Throwable e ){
				}
			}

			if ( key.startsWith( from )){

					// shouldn't really have file names as keys due to charset issues...

				String new_key = to + key.substring( from.length());

				mods++;

				it.remove();

				replacements.put( new_key, new_value );

			}else{

				if ( value != new_value ){

					entry.setValue( new_value );
				}
			}
		}

		map.putAll( replacements );

		return( mods );
	}

	private int
	patch(
		List				list,
		String				from,
		String				to )
	{
		int	mods = 0;

		for ( int i=0;i<list.size();i++){

			Object entry = list.get( i );

			if ( entry instanceof Map ){

				mods += patch((Map)entry, from , to );

			}else if ( entry instanceof List ){

				mods += patch((List)entry, from , to );

			}else if ( entry instanceof byte[] ){

				try{
					String	str = new String((byte[])entry, "UTF-8" );

					if ( str.startsWith( from )){

						list.set( i, to + str.substring( from.length()));

						mods++;
					}
				}catch( Throwable e ){
				}
			}
		}

		return( mods );
	}

	void
	restoreSupport(
		File				backup_folder,
		BackupListener		listener )
	{
		try{
			UpdateInstaller installer 	= null;
			File 			temp_dir 	= null;

			boolean	ok = false;

			try{
				listener.reportProgress( "Reading from " + backup_folder.getAbsolutePath());

				if ( !backup_folder.isDirectory()){

					throw( new Exception( "Location '" + backup_folder.getAbsolutePath() + "' must be a directory" ));
				}

				listener.reportProgress( "Analysing backup" );

				File	config = FileUtil.newFile( backup_folder, ConfigurationManager.CONFIG_FILENAME );

				if ( !config.exists()){

					throw( new Exception( "Invalid backup: " + ConfigurationManager.CONFIG_FILENAME + " not found" ));
				}

				Map config_map = BDecoder.decode( FileUtil.readFileAsByteArray( config ));

				byte[]	temp = (byte[])config_map.get( "azureus.user.directory" );

				if ( temp == null ){

					throw( new Exception( "Invalid backup: " + ConfigurationManager.CONFIG_FILENAME + " doesn't contain user directory details" ));
				}

				File current_user_dir	= FileUtil.newFile( SystemProperties.getUserPath());
				File backup_user_dir 	= FileUtil.newFile( new String( temp, "UTF-8" ));

				listener.reportProgress( "Current user directory:\t"  + current_user_dir.getAbsolutePath());
				listener.reportProgress( "Backup's user directory:\t" + backup_user_dir.getAbsolutePath());

				temp_dir = AETemporaryFileHandler.createTempDir();

				PluginInterface pi = core.getPluginManager().getDefaultPluginInterface();

				installer = pi.getUpdateManager().createInstaller();

				File[] files = backup_folder.listFiles();

				if ( COConfigurationManager.getBooleanParameter("br.restore.autopause")){
					
					File	cf = CustomizationManagerFactory.getSingleton().getNewUserCustomizationFile( "restore_ap" );
			
					FileUtil.writeStringAsFile(
						cf,
						"Pause\\ Downloads\\ On\\ Start\\ After\\ Resume=bool:true" );
				}
				
				if ( current_user_dir.equals( backup_user_dir )){

					listener.reportProgress( "Directories are the same, no patching required" );

					for ( File f: files ){

						File source = FileUtil.newFile( temp_dir, f.getName());

						listener.reportProgress( "Creating restore action for '" + f.getName() + "'" );

						copyFiles( f, source );

						File target = FileUtil.newFile( current_user_dir, f.getName());

						addActions( installer, source, target );
					}
				}else{

					listener.reportProgress( "Directories are different, backup requires patching" );

					for ( File f: files ){

						File source = FileUtil.newFile( temp_dir, f.getName());

						listener.reportProgress( "Creating restore action for '" + f.getName() + "'" );

						if ( f.isDirectory() || !f.getName().contains( ".config" )){

							copyFiles( f, source );

						}else{

							boolean	patched = false;

							BufferedInputStream bis = new BufferedInputStream( FileUtil.newFileInputStream( f ), 1024*1024 );

							try{
								Map m = BDecoder.decode( bis );

								bis.close();

								bis = null;

								if ( m.size() > 0 ){

									int applied = patch( m, backup_user_dir.getAbsolutePath(), current_user_dir.getAbsolutePath());

									if ( applied > 0 ){

										listener.reportProgress( "    Applied " + applied + " patches" );

										patched = FileUtil.writeBytesAsFile2( source.getAbsolutePath(), BEncoder.encode( m ));

										if ( !patched ){

											throw( new Exception( "Failed to write " + source ));
										}
									}
								}
							}catch( Throwable e ){

								String name = f.getName();

									// use 'contains' as we can get .bad .bad1 .bad2 etc

								if ( name.contains( ".bad" ) || name.contains( ".bak" )){

									listener.reportProgress( "    Ignored failure to patch bad configuration file" );

								}else{

									throw( e );
								}

							}finally{

								if ( bis != null ){

									try{
										bis.close();

									}catch( Throwable e ){

									}
								}
							}

							if ( !patched ){

								copyFiles( f, source );
							}
						}

						File target = FileUtil.newFile( current_user_dir, f.getName());

						addActions( installer, source, target );
					}
				}

				listener.reportProgress( "Restore action creation complete, restart required to complete the operation" );

				listener.reportComplete();

				ok = true;

			}finally{

				if ( !ok ){

					if ( installer != null ){

						installer.destroy();
					}

					if ( temp_dir != null ){

						FileUtil.recursiveDeleteNoCheck( temp_dir );
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );
			
			listener.reportError( e );
		}
	}
}
