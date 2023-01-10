/*
 * Created on 28.11.2003
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
package com.biglybt.ui.swt;

import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;


import com.biglybt.activities.ActivitiesConstants;
import com.biglybt.activities.ActivitiesEntry;
import com.biglybt.activities.ActivitiesListener;
import com.biglybt.activities.ActivitiesManager;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerDiskListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.impl.DownloadManagerAdapter;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.global.GlobalManagerEvent;
import com.biglybt.core.global.GlobalManagerEventListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.ui.swt.minibar.DownloadBar;

import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;

/**
 * Contains methods to alert the user of certain events.
 * @author Rene Leonhardt
 */

public class
UserAlerts
{
	private static UserAlerts	singleton;
	private final DownloadManagerAdapter download_manager_listener;
	private final DiskManagerListener disk_listener;
	private final DownloadManagerDiskListener dm_disk_listener;
	private final GlobalManagerAdapter globalManagerListener;
	private final GlobalManagerEventListener globalManagerEventListener;
	private final ActivitiesListener activitiesListener;
	private final GlobalManager global_manager;

	public static UserAlerts
	getSingleton()
	{
		return( singleton );
	}

	public static void destroySingleton() {
		if (singleton != null) {
			singleton.dispose();
			singleton = null;
		}
	}
	
    private AEMonitor	this_mon 	= new AEMonitor( "UserAlerts" );

    private boolean startup = true;

    private TrayIcon	native_tray_icon;
    private int			native_message_count;
    
    private final LoggerChannel log;
    
	public
	UserAlerts(
		GlobalManager	global_manager )
 	{
	  this.global_manager = global_manager;
	  singleton = this;
	  
	  PluginInterface plugin_interface = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface();

	  log = plugin_interface.getLogger().getChannel( "Alerts" );

	  UIManager	ui_manager = plugin_interface.getUIManager();

	  BasicPluginViewModel model = ui_manager.createBasicPluginViewModel("ConfigView.section.interface.alerts" );

	  model.getActivity().setVisible( false );
	  model.getProgress().setVisible( false );

	  model.attachLoggerChannel( log );

		
	  // @see com.biglybt.core.download.impl.DownloadManagerAdapter#stateChanged(com.biglybt.core.download.DownloadManager, int)
// if state == STARTED, then open the details window (according to config)
	  download_manager_listener = new DownloadManagerAdapter()
	  {
	  @Override
	  public void downloadComplete(DownloadManager manager) {
		  activityFinished( manager, null );
	  }

	  // @see com.biglybt.core.download.impl.DownloadManagerAdapter#stateChanged(com.biglybt.core.download.DownloadManager, int)
	  @Override
	  public void stateChanged(final DownloadManager manager, int state) {

		  boolean lowNoise = manager.getDownloadState().getFlag(
				  DownloadManagerState.FLAG_LOW_NOISE);
		  if (lowNoise) {
			  return;
		  }

		  // if state == STARTED, then open the details window (according to config)
		  if (state == DownloadManager.STATE_DOWNLOADING
				  || state == DownloadManager.STATE_SEEDING) {
			  Utils.execSWTThread(new AERunnable() {
				  @Override
				  public void runSupport() {
					  boolean complete = manager.isDownloadComplete(false);

					  if ((!complete && COConfigurationManager.getBooleanParameter("Open Details"))
							  || (complete && COConfigurationManager.getBooleanParameter("Open Seeding Details"))) {
						  UIFunctionsManager.getUIFunctions().getMDI().loadEntryByID(
								  MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS,
								  false, false, manager);
					  }

					  if (((!complete) && COConfigurationManager.getBooleanParameter("Open Bar Incomplete"))
							  || (complete && COConfigurationManager.getBooleanParameter("Open Bar Complete"))) {

						  DownloadBar.open(manager, Utils.findAnyShell());
					  }
				  }
			  });
		  }

		  boolean error_reported = manager.getDownloadState().getFlag( DownloadManagerState.FLAG_ERROR_REPORTED );

		  if ( state == DownloadManager.STATE_ERROR ){

			  if ( !error_reported ){

				  manager.getDownloadState().setFlag( DownloadManagerState.FLAG_ERROR_REPORTED, true );

				  reportError( manager );
			  }
		  }else if ( state == DownloadManager.STATE_DOWNLOADING || state == DownloadManager.STATE_SEEDING ){

			  if ( error_reported ){

				  manager.getDownloadState().setFlag( DownloadManagerState.FLAG_ERROR_REPORTED, false );
			  }
		  }
	  }
  };

		/*
		System.out.println(
			"amc:" +
			file.getDownloadManager().getDisplayName() + "/" +
			file.getName() + ":" + old_mode + " -> " + new_mode );
		*/
	  disk_listener = new DiskManagerListener()
	  {
		  @Override
		  public void
		  stateChanged(
				  DiskManager	dm,
			  int oldState,
			  int	newState )
		  {
		  }

		  @Override
		  public void
		  filePriorityChanged(
				  DiskManager	dm,
			  DiskManagerFileInfo file )
		  {
		  }

		  @Override
		  public void
		  pieceDoneChanged(
				  DiskManager	dm,
			  DiskManagerPiece piece )
		  {
		  }

		  @Override
		  public void
		  fileCompleted(
			  DiskManager				diskManager,
			  DiskManagerFileInfo		file )
		  {
			  DownloadManager dm = file.getDownloadManager();

			  if ( dm != null ){

				 activityFinished( dm, file );	 
			  }
		  }
	  };

	  dm_disk_listener = new DownloadManagerDiskListener()
	  {
		  @Override
		  public void
		  diskManagerAdded(
				  DiskManager	dm )
		  {
			  dm.addListener( disk_listener );
		  }

		  @Override
		  public void
		  diskManagerRemoved(
				  DiskManager	dm )
		  {
			  dm.removeListener( disk_listener );
		  }

	  };

	  globalManagerListener = new GlobalManagerAdapter() {
		  @Override
		  public void
		  downloadManagerAdded(DownloadManager manager)
		  {
			  // don't pop up for non-persistent as these get added late in the day every time
			  // so we'll notify for each download every startup

			  if (!startup && manager.isPersistent()) {

				  boolean bPopup = COConfigurationManager.getBooleanParameter("Popup Download Added");

				  if (bPopup) {

					  if( !manager.getDownloadState().getFlag( DownloadManagerState.FLAG_LOW_NOISE )){

						  String popup_text = MessageText.getString("popup.download.added",
								  new String[] { manager.getDisplayName()
								  });
						  forceNotify(
								  UIFunctions.STATUSICON_NONE, null, popup_text, null,
								  new Object[] {
										  manager
								  }, -1);
					  }
				  }
			  }

			  manager.addListener(download_manager_listener);

			  manager.addDiskListener(dm_disk_listener);
		  }

		  @Override
		  public void
		  downloadManagerRemoved(DownloadManager manager)
		  {
			  manager.removeListener(download_manager_listener);

			  manager.removeDiskListener( dm_disk_listener );
		  }

		  @Override
		  public void
		  destroyed()
		  {
			  tidyUp();
		  }
	  };
	  global_manager.addListener(globalManagerListener);

	  globalManagerEventListener = new GlobalManagerEventListener(){
		
		@Override
		public void 
		eventOccurred(
			GlobalManagerEvent event)
		{
			if ( event.getEventType() == GlobalManagerEvent.ET_RECHECK_COMPLETE ){
				
				Object[] params = (Object[])event.getEventData();
				
				boolean explicit 	= (Boolean)params[0];
				boolean cancelled 	= (Boolean)params[1];
				
				checkComplete( event.getDownload(), explicit, cancelled );
			}
		}
	  };
	  
	  global_manager.addEventListener( globalManagerEventListener );
	  
	  activitiesListener = new ActivitiesListener() {

		  @Override
		  public void vuzeNewsEntryChanged(ActivitiesEntry entry) {
		  }

		  @Override
		  public void vuzeNewsEntriesRemoved(ActivitiesEntry[] entries) {
		  }

		  @Override
		  public void vuzeNewsEntriesAdded(ActivitiesEntry[] entries) {
			  boolean	local_added = false;
			  for ( ActivitiesEntry entry: entries ){
				  if ( entry.getTypeID().equals( ActivitiesConstants.TYPEID_LOCALNEWS )){
					  local_added = true;
				  }
			  }
			  if ( local_added ){
				  UserAlerts ua = UserAlerts.getSingleton();

				  if ( ua != null ){
					  ua.notificationAdded();
				  }
			  }
		  }
	  };
	  ActivitiesManager.addListener(activitiesListener);

	  startup = false;
	}

  	private void
  	activityFinished(
  		DownloadManager			manager,
  		DiskManagerFileInfo		dm_file )
  	{
  		DownloadManagerState dm_state = manager.getDownloadState();

		if ( dm_state.getFlag( DownloadManagerState.FLAG_LOW_NOISE)) {

			return;
		}

		boolean	download = dm_file == null;

		Object 	relatedObject;
		String	item_name;

		if ( download ){

			relatedObject 	= manager;
			item_name		= manager.getDisplayName();

		}else{

			relatedObject	= dm_file.getDiskManager();
			item_name		= dm_file.getFile( true ).getName();
		}

  		final String sound_enabler;
  		final String sound_file;

  		final String speech_enabler;
  		final String speech_text;

  		final String popup_enabler;
  		final String popup_def_text;

  		final String native_enabler;
  		final String native_text;
  		
  		if ( download ){
  			
	 		sound_enabler 	= "Play Download Finished";
	  		sound_file		= "Play Download Finished File";

	  		speech_enabler 	= "Play Download Finished Announcement";
	  		speech_text		= "Play Download Finished Announcement Text";

	  		popup_enabler   = "Popup Download Finished";
	  		popup_def_text  = "popup.download.finished";

	  		native_enabler 	= "Notify Download Finished";
	  		native_text		= "notify.download.finished";
	  		
  		}else{
  			
	 		sound_enabler 	= "Play File Finished";
	  		sound_file		= "Play File Finished File";

	  		speech_enabler 	= "Play File Finished Announcement";
	  		speech_text		= "Play File Finished Announcement Text";

	  		popup_enabler   = "Popup File Finished";
	  		popup_def_text  = "popup.file.finished";
	  		
	  		native_enabler 	= null;
	  		native_text		= null;
  		}

  		Map 	dl_file_alerts = dm_state.getMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS );
  		String 	dlf_prefix = download?"":(String.valueOf(dm_file.getIndex()) + "." );

  		boolean do_popup 	= COConfigurationManager.getBooleanParameter(popup_enabler) || isDLFEnabled( dl_file_alerts, dlf_prefix, popup_enabler );
  		boolean do_speech 	= Constants.isOSX	&& ( COConfigurationManager.getBooleanParameter(speech_enabler) || isDLFEnabled( dl_file_alerts, dlf_prefix, speech_enabler ));
  		boolean do_sound 	= COConfigurationManager.getBooleanParameter( sound_enabler, false) || isDLFEnabled( dl_file_alerts, dlf_prefix, sound_enabler );

    	boolean do_native 	= native_enabler != null && COConfigurationManager.getBooleanParameter(native_enabler);

  		doStuff(
  			relatedObject, item_name,
  			do_popup, popup_def_text, false, do_native, native_text,
  			do_speech, speech_text,
  			do_sound, sound_file );
  	}

 	private void
  	checkComplete(
  		DownloadManager			manager,
  		boolean					explicit,
  		boolean					cancelled )
  	{
  		DownloadManagerState dm_state = manager.getDownloadState();

		if ( cancelled || dm_state.getFlag( DownloadManagerState.FLAG_LOW_NOISE) && !explicit ) {

			return;
		}
		
    	boolean do_popup 	= 	COConfigurationManager.getBooleanParameter( "Popup Check Complete" );

		doStuff(
	  			manager, manager.getDisplayName(),
	  			do_popup, "popup.check.complete", false, 
	  			false, null,
	  			false, null,
	  			false, null );
  	}
 	
  	private long	last_error_speech;
  	private long	last_error_sound;

  	private void
  	reportError(
  		DownloadManager			manager )
  	{
		final Object relatedObject 	= manager;
		final String item_name		= manager.getDisplayName();


  		final String sound_enabler 	= "Play Download Error";
  		final String sound_file		= "Play Download Error File";

  		final String speech_enabler 	= "Play Download Error Announcement";
  		final String speech_text		= "Play Download Error Announcement Text";

  		final String popup_enabler   = "Popup Download Error";
  		final String popup_def_text  = "popup.download.error";

  		final String native_enabler 	= "Notify Download Error";
  		final String native_text		= "notify.download.error";
  		
		long now = SystemTime.getMonotonousTime();

    	boolean do_popup 	= 	COConfigurationManager.getBooleanParameter(popup_enabler);

    	boolean do_speech	= 	Constants.isOSX &&
    							COConfigurationManager.getBooleanParameter(speech_enabler) &&
    							( last_error_speech == 0 || now - last_error_speech > 5000 );

    	boolean do_sound	= 	COConfigurationManager.getBooleanParameter( sound_enabler, false) &&
    							( last_error_sound == 0 || now - last_error_sound > 5000 );

    	boolean do_native 	= 	COConfigurationManager.getBooleanParameter(native_enabler);

    	if ( do_speech ){
    		last_error_speech = now;
    	}
       	if ( do_sound ){
       		last_error_sound = now;
    	}

 		doStuff(
  			relatedObject, item_name,
  			do_popup, popup_def_text, true, do_native, native_text,
  			do_speech, speech_text,
  			do_sound, sound_file );
  	}

  	public void
  	notificationAdded()
  	{
  		boolean do_popup 	= 	false;

    	boolean do_speech	= 	Constants.isOSX &&
    							COConfigurationManager.getBooleanParameter("Play Notification Added Announcement");

    	boolean do_sound	= 	COConfigurationManager.getBooleanParameter( "Play Notification Added", false);

		doStuff(
  			null, null,
  			do_popup, null, false, false, null,
  			do_speech, "Play Notification Added Announcement Text",
  			do_sound, "Play Notification Added File" );
  	}

  	private void
  	doStuff(
  		Object			relatedObject,
  		String			item_name,
  		boolean			do_popup,
  		String			popup_def_text,
  		boolean			popup_is_error,
  		boolean			do_native_tray,
  		String			native_text,
  		boolean			do_speech,
  		final String	speech_text,
  		boolean			do_sound,
  		String			sound_file )
  	{
   		try{
  			this_mon.enter();

  			if (  do_popup ) {
  				String popup_text = MessageText.getString(popup_def_text, new String[]{item_name});

  				forceNotify(
						popup_is_error?UIFunctions.STATUSICON_ERROR:UIFunctions.STATUSICON_NONE, null, popup_text, null,
						new Object[] {
							relatedObject
						}, -1);
  			}

			if ( do_speech ) {
				new AEThread2("SaySound") {
					@Override
					public void run() {
						try {
							Runtime.getRuntime().exec(new String[] {
								"say",
								COConfigurationManager.getStringParameter(speech_text)
							}); // Speech Synthesis services

							Thread.sleep(2500);
						} catch (Throwable e) {
						}
					}
				}.start();
			}

			if ( do_sound ){

	    		String	file = COConfigurationManager.getStringParameter( sound_file );
	    		
	    		GeneralUtils.playSound( file );
	    	}
			
			if ( do_native_tray ){
				
				displayNativeMessage( MessageText.getString( native_text ), item_name, popup_is_error );
			}
  		}catch( Throwable e ){

  			Debug.printStackTrace( e );

  		}finally{

  			this_mon.exit();
  		}
  	}

  	public boolean
  	displayNativeMessage(
  		String		caption,
  		String		text,
  		boolean		is_error )
  	{
  		try{ 		
  			this_mon.enter();
  			
			if ( native_tray_icon == null ){
				
				try{
					if ( SystemTray.isSupported()){
						
						SystemTray st = SystemTray.getSystemTray();
						
						Image image = Toolkit.getDefaultToolkit().createImage(getClass().getResource( "/com/biglybt/ui/icons/a32info.png" ));
						
						native_tray_icon = new TrayIcon(image, "");
						
						native_tray_icon.setImageAutoSize(true);
						
						native_tray_icon.setToolTip( MessageText.getString( "label.product.alerts" ));
				        
						try{
							PopupMenu menu = new PopupMenu();
							
							native_tray_icon.setPopupMenu( menu );
							
							MenuItem mi = new MenuItem( MessageText.getString( "sharing.progress.hide" ));
							
							mi.addActionListener(
								new ActionListener(){
									
									@Override
									public void actionPerformed(ActionEvent event){
										try{
											this_mon.enter();
											
											try{
												SystemTray st = SystemTray.getSystemTray();
											
												st.remove( native_tray_icon );
												
												native_tray_icon = null;
												
											}catch( Throwable e ){										
											}
										}finally{
											
											this_mon.exit();
										}
									}
								});
							
							menu.add( mi );
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
						
				        st.add( native_tray_icon );
					}
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		
			if ( native_tray_icon != null ){
			        					
				native_tray_icon.displayMessage( caption, text, is_error?MessageType.ERROR:MessageType.INFO );
				
				/* Unfortunately removing the icon also removes any messages associated with it
				 * that may be in the notification area (on Windows 10 for example)
				 
				final int mine = ++native_message_count;
				
				SimpleTimer.addEvent(
					"iconhider",
					SystemTime.getOffsetTime( 30*1000 ),
					new TimerEventPerformer(){
						
						@Override
						public void perform(TimerEvent event){
							try{
								this_mon.enter();
								
								if ( native_message_count == mine ){
									
									try{
										SystemTray st = SystemTray.getSystemTray();
									
										st.remove( native_tray_icon );
										
										native_tray_icon = null;
										
									}catch( Throwable e ){										
									}
								}
								
							}finally{
								
								this_mon.exit();
							}
						}
					});
				*/
				
				return( true );
			}
			
			return( false );
			
  		}finally{
  			
  			this_mon.exit();
  		}
  	}

  	private boolean
  	isDLFEnabled(
  		Map		map,
  		String	prefix,
  		String	key )
  	{
  		if ( map == null ){

  			return( false );
  		}

  		key = prefix + key;

  		return( map.containsKey( key ));
  	}

	private void
	forceNotify(
		final int iconID, final String title, final String text, final String details,
		final Object[] relatedObjects, final int timeoutSecs)
	{
		if ( title != null && !title.isEmpty()){
			log.log( title );
		}
		if ( text != null && !text.isEmpty()){
			log.log( text );
		}
		if ( details != null && !details.isEmpty()){
			log.log( details );
		}
		
		UIFunctionsManager.execWithUIFunctions(
			new UIFunctionsManager.UIFCallback() {

				@Override
				public void run(UIFunctions uif) {
					uif.forceNotify(iconID, title, text, details, relatedObjects, timeoutSecs);
				}
			});
	}


  	protected void
  	tidyUp()
  	{
		/*
		The Java audio system keeps some threads running even after playback is finished.
		One of them, named "Java Sound event dispatcher", is *not* a daemon
		thread and keeps the VM alive.
		We have to locate and interrupt it explicitely.
		*/

		try{

			ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();

			Thread[] threadList = new Thread[threadGroup.activeCount()];

			threadGroup.enumerate(threadList);

			for (int i = 0;	i < threadList.length;	i++){

				if(threadList[i] != null && "Java Sound event dispatcher".equals(threadList[i].getName())){

					threadList[i].interrupt();
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
		
		if ( native_tray_icon != null ){
			
			try{
				SystemTray st = SystemTray.getSystemTray();
			
				st.remove( native_tray_icon );
				
			}catch( Throwable e ){
				
			}
		}
  	}


  	/**
  	 * Grab the user's attention in a platform dependent way
  	 * @param type one of <code>PlatformManager.USER_REQUEST_INFO</code>,
  	 * 										<code>PlatformManager.USER_REQUEST_WARNING</code>, OR
  	 * 										<code>PlatformManager.USER_REQUEST_QUESTION</code>
  	 * @param data user-defined data object;
  	 * 				see the platform-specific <code>PlatformManager</code> for what may be supported
  	 */
  	public static void requestUserAttention(int type, Object data) {

  		PlatformManager pm = PlatformManagerFactory.getPlatformManager();
  		if (pm.hasCapability(PlatformManagerCapabilities.RequestUserAttention)) {
  			try {
  				pm.requestUserAttention(type, data);
  			} catch (PlatformManagerException e) {
  				Debug.printStackTrace(e);
  			}
  		}
  	}

  	public void dispose() {
  		if (singleton == this) {
  			singleton = null;
  		}

  		global_manager.removeListener(globalManagerListener);
  		global_manager.removeEventListener(globalManagerEventListener);
  		
  		List<DownloadManager> dms = global_manager.getDownloadManagers();
  		for (DownloadManager dm : dms) {
  			dm.removeListener(download_manager_listener);
  			dm.removeDiskListener(dm_disk_listener);

  			DiskManager diskManager = dm.getDiskManager();
  			if (diskManager != null) {
  				diskManager.removeListener(disk_listener);
  			}
  		}


  		ActivitiesManager.removeListener(activitiesListener);
  	}
}