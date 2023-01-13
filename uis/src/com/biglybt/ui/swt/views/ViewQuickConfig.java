/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.config.ConfigSectionStartShutdown;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.config.FileSwtParameter;
import com.biglybt.ui.swt.config.IntSwtParameter;
import com.biglybt.ui.swt.config.ParameterChangeListener;
import com.biglybt.ui.swt.config.StringListSwtParameter;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

/**
 * @author TuxPaper
 * @created Apr 7, 2007
 *
 */
public class ViewQuickConfig
	implements UISWTViewCoreEventListener
{
	private UISWTView swtView;

	Composite	composite;

	public ViewQuickConfig() {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
			}
		});
	}


	private void
	initialize(
		Composite oparent)
	{
		GridLayout layout = Utils.getSimpleGridLayout(1);

		oparent.setLayout( layout);
		
		final Composite parent = new Composite( oparent, SWT.NULL );
		GridData gridData = new GridData(GridData.FILL_BOTH);
		parent.setLayoutData(gridData);

		composite = Utils.createScrolledComposite(parent);

		gridData = new GridData(GridData.FILL_BOTH);
		composite.setLayoutData(gridData);

		layout = new GridLayout(4, false);

		composite.setLayout(layout);

			// done downloading - 2

		addDoneDownloadingOption( composite, false );

			// max simul down - 2

		Label label = new Label(composite, SWT.NULL);
		gridData = new GridData();
		gridData.horizontalIndent = 8;
		label.setLayoutData(gridData);
		Messages.setLanguageText(label, "ConfigView.label.maxdownloads.short");

		IntSwtParameter maxDLs = new IntSwtParameter( composite, "max downloads", null, null, null );

		maxDLs.addChangeListener(p -> COConfigurationManager.setDirty());
		
		addTemporaryRates( composite );
		
		addTemporaryData( composite );
		
	}
	
	private void
	addTemporaryRates(
		Composite	composite )
	{
		Group temp_rates = new Group( composite, SWT.NULL );
		Messages.setLanguageText( temp_rates, "label.temporary.rates" );

		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 4;

		temp_rates.setLayoutData(gridData);

		GridLayout layout = new GridLayout(10, false);
		layout.marginWidth 	= 0;
		layout.marginHeight = 0;

		temp_rates.setLayout(layout);

		//label = new Label(temp_rates, SWT.NULL);
		//Messages.setLanguageText( label, "label.temporary.rates" );

		Label label = new Label(temp_rates, SWT.NULL);
		gridData = new GridData();
		gridData.horizontalIndent=4;
		label.setLayoutData(gridData);
		Messages.setLanguageText( label, "label.upload.kbps", new String[]{ DisplayFormatters.getRateUnit( DisplayFormatters.UNIT_KB )});

		final IntSwtParameter tempULRate = new IntSwtParameter( temp_rates, "global.download.rate.temp.kbps", null, null, 0, Integer.MAX_VALUE, null );

		label = new Label(temp_rates, SWT.NULL);
		Messages.setLanguageText( label, "label.download.kbps", new String[]{ DisplayFormatters.getRateUnit( DisplayFormatters.UNIT_KB )});

		final IntSwtParameter tempDLRate = new IntSwtParameter( temp_rates, "global.upload.rate.temp.kbps", null, null, 0, Integer.MAX_VALUE, null );

		label = new Label(temp_rates, SWT.NULL);
		Messages.setLanguageText( label, "label.duration.mins" );

		final IntSwtParameter tempMins 	= new IntSwtParameter( temp_rates, "global.rate.temp.min", null, null, 0, Integer.MAX_VALUE, null );

		final Button activate = new Button( temp_rates, SWT.TOGGLE );
		Messages.setLanguageText( activate, "label.activate" );

		final BufferedLabel remLabel = new BufferedLabel(temp_rates, SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.widthHint = 150;
		remLabel.setLayoutData(gridData);

		activate.addSelectionListener(
			new SelectionAdapter(){

				private CoreLifecycleAdapter listener;

				private TimerEventPeriodic event;

				private boolean auto_up_enabled;
				private boolean auto_up_seeding_enabled;
				private boolean seeding_limits_enabled;

				private int	up_limit;
				private int	down_limit;

				private long	end_time;

				@Override
				public void
				widgetSelected( SelectionEvent e)
				{
					if ( activate.getSelection()){

						listener =
							new CoreLifecycleAdapter()
							{
								@Override
								public void
								stopping(
									Core core )
								{
									deactivate( true );
								}
							};

						CoreFactory.getSingleton().addLifecycleListener( listener );

						Messages.setLanguageText( activate, "FileView.BlockView.Active" );

						tempDLRate.setEnabled( false );
						tempULRate.setEnabled( false );
						tempMins.setEnabled( false );

					    auto_up_enabled 		= COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY );
					    auto_up_seeding_enabled = COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY );
					    seeding_limits_enabled 	= COConfigurationManager.getBooleanParameter( TransferSpeedValidator.UPLOAD_SEEDING_ENABLED_CONFIGKEY );
					    up_limit 				= COConfigurationManager.getIntParameter( TransferSpeedValidator.UPLOAD_CONFIGKEY );
					    down_limit				= COConfigurationManager.getIntParameter( TransferSpeedValidator.DOWNLOAD_CONFIGKEY );

					    COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, false );
					    COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false );
					    COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_SEEDING_ENABLED_CONFIGKEY, false );

					    COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_CONFIGKEY, tempULRate.getValue());
					    COConfigurationManager.setParameter( TransferSpeedValidator.DOWNLOAD_CONFIGKEY, tempDLRate.getValue());

						end_time = SystemTime.getCurrentTime() + tempMins.getValue() * 60* 1000;

						event = SimpleTimer.addPeriodicEvent(
							"TempRates",
							1000,
							new TimerEventPerformer() {

								@Override
								public void perform(TimerEvent e) {

									Utils.execSWTThread(
										new Runnable(){

											@Override
											public void run() {

												if ( event == null ){

													return;
												}

												long now = SystemTime.getCurrentTime();

												long	rem = end_time - now;

												if ( rem < 1000 || composite.isDisposed()){

													deactivate( false );

												}else{

													remLabel.setText( MessageText.getString( "TableColumn.header.remaining") + ": " + DisplayFormatters.formatTime( rem ));
												}
											}
										});
								}
							});
					}else{

						deactivate( false );
					}
				}

				private void
				deactivate(
					boolean	closing )
				{
				    COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, auto_up_enabled );
				    COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, auto_up_seeding_enabled );
				    COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_SEEDING_ENABLED_CONFIGKEY, seeding_limits_enabled );

				    COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_CONFIGKEY, up_limit  );
				    COConfigurationManager.setParameter( TransferSpeedValidator.DOWNLOAD_CONFIGKEY, down_limit  );

				    if ( !closing ){

					    if ( listener != null ){

					    	CoreFactory.getSingleton().removeLifecycleListener( listener );

					    	listener = null;
					    }

					    if ( !composite.isDisposed()){

						    Messages.setLanguageText( activate, "label.activate" );
							activate.setSelection( false );

							tempDLRate.setEnabled( true );
							tempULRate.setEnabled( true );
							tempMins.setEnabled( true );
							remLabel.setText( "" );
					    }
				    }

					if ( event != null ){
						event.cancel();
						event = null;
					}
				}
			});

		tempMins.addAndFireChangeListener(p -> Utils.execSWTThread(() -> activate.setEnabled( p.getValue() > 0 )));
	}

	private void
	addTemporaryData(
		Composite	composite )
	{
		Group temp_rates = new Group( composite, SWT.NULL );
		Messages.setLanguageText( temp_rates, "label.temporary.data" );

		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 4;

		temp_rates.setLayoutData(gridData);

		GridLayout layout = new GridLayout(10, false);
		layout.marginWidth 	= 0;
		layout.marginHeight = 0;

		temp_rates.setLayout(layout);

		//label = new Label(temp_rates, SWT.NULL);
		//Messages.setLanguageText( label, "label.temporary.rates" );

		Label label = new Label(temp_rates, SWT.NULL);
		gridData = new GridData();
		gridData.horizontalIndent=4;
		label.setLayoutData(gridData);
		Messages.setLanguageText( label, "label.upload.mb", new String[]{ DisplayFormatters.getUnit( DisplayFormatters.UNIT_MB )});

		final IntSwtParameter tempULLimit = new IntSwtParameter( temp_rates, "global.upload.limit.temp.mb", null, null, 0, Integer.MAX_VALUE, null );

		label = new Label(temp_rates, SWT.NULL);
		Messages.setLanguageText( label, "label.download.mb", new String[]{ DisplayFormatters.getUnit( DisplayFormatters.UNIT_MB )});

		final IntSwtParameter tempDLLimit = new IntSwtParameter( temp_rates, "global.download.limit.temp.mb", null, null, 0, Integer.MAX_VALUE, null );

		final Button activate = new Button( temp_rates, SWT.TOGGLE );
		Messages.setLanguageText( activate, "label.activate" );

		final BufferedLabel remLabel = new BufferedLabel(temp_rates, SWT.DOUBLE_BUFFERED );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.widthHint = 200;
		remLabel.setLayoutData(gridData);

		activate.addSelectionListener(
			new SelectionAdapter(){

				private CoreLifecycleAdapter listener;

				private TimerEventPeriodic event;

				private long	end_upload;
				private long	end_download;
				

				@Override
				public void
				widgetSelected( SelectionEvent e)
				{
					if ( activate.getSelection()){

						listener =
							new CoreLifecycleAdapter()
							{
								@Override
								public void
								stopping(
									Core core )
								{
									deactivate( true );
								}
							};

						Core core = CoreFactory.getSingleton();
						
						core.addLifecycleListener( listener );

						Messages.setLanguageText( activate, "FileView.BlockView.Active" );

						final GlobalManagerStats stats = core.getGlobalManager().getStats();
						
						tempULLimit.setEnabled( false );
						tempDLLimit.setEnabled( false );

						long u_limit = tempULLimit.getValue();
						
						if ( u_limit > 0 ){
							
							end_upload = stats.getTotalDataProtocolBytesSent() + u_limit*DisplayFormatters.getMinB();
							
						}else{
							
							end_upload = 0;
						}
						
						long d_limit = tempDLLimit.getValue();
						
						if ( d_limit > 0 ){
							
							end_download = stats.getTotalDataProtocolBytesReceived() +  d_limit*DisplayFormatters.getMinB();
							
						}else{
							
							end_download = 0;
						}
						
						event = SimpleTimer.addPeriodicEvent(
							"TempData",
							5000,
							new TimerEventPerformer() {

								@Override
								public void perform(TimerEvent e) {

									Utils.execSWTThread(
										new Runnable(){

											@Override
											public void run() {

												if ( event == null ){

													return;
												}

												long	rem_up 		= 0;
												long	rem_down	= 0;
												
												if ( end_upload > 0 ){
													
													rem_up = end_upload - stats.getTotalDataProtocolBytesSent();
												}

												if ( end_download > 0 ){
													
													rem_down = end_download - stats.getTotalDataProtocolBytesReceived();
												}
								
												if ( end_upload > 0 &&  rem_up <= 0 ){
													
													java.util.List<DownloadManager> dms = core.getGlobalManager().getDownloadManagers();
													
													for ( DownloadManager dm: dms ){
														
														if ( !dm.isForceStart()) {
															
															int state = dm.getState();
															
															if ( 	state != DownloadManager.STATE_STOPPED &&
																	state != DownloadManager.STATE_ERROR &&
																	!dm.isPaused()){
															
																ManagerUtils.stop(dm, null );
																
																dm.setStopReason( MessageText.getString( "label.temporary.data" ));
															}
														}
													}
												}
												
												if ( end_download > 0 &&  rem_down <= 0 ){
													
													java.util.List<DownloadManager> dms = core.getGlobalManager().getDownloadManagers();
													
													for ( DownloadManager dm: dms ){
														
														if ( !dm.isForceStart()) {
															
															int state = dm.getState();
															
															if ( 	state != DownloadManager.STATE_STOPPED &&
																	state != DownloadManager.STATE_ERROR &&
																	!dm.isPaused()){
															
																if ( !dm.isDownloadComplete( false )){
																
																	ManagerUtils.stop(dm, null );
																	
																	dm.setStopReason( MessageText.getString( "label.temporary.data" ));
																}
															}
														}
													}
												}
												if (( rem_up <= 0 && rem_down <= 0 ) || composite.isDisposed()){

													deactivate( false );

												}else{

													remLabel.setText( 
															MessageText.getString( 
																"TableColumn.header.remaining") + ": " + 
																	DisplayFormatters.formatByteCountToKiBEtc(rem_up<0?0:rem_up ) + "/" + 
																	DisplayFormatters.formatByteCountToKiBEtc(rem_down<0?0:rem_down ));
													
													temp_rates.layout( new Control[] { remLabel.getControl()});
												}
											}
										});
								}
							});
					}else{

						deactivate( false );
					}
				}

				private void
				deactivate(
					boolean	closing )
				{
				    if ( !closing ){

					    if ( listener != null ){

					    	CoreFactory.getSingleton().removeLifecycleListener( listener );

					    	listener = null;
					    }

					    if ( !composite.isDisposed()){

						    Messages.setLanguageText( activate, "label.activate" );
							activate.setSelection( false );

							tempULLimit.setEnabled( true );
							tempDLLimit.setEnabled( true );
							remLabel.setText( "" );
							
							temp_rates.layout( true );
					    }
				    }

					if ( event != null ){
						event.cancel();
						event = null;
					}
				}
			});

		activate.setEnabled( tempULLimit.getValue() > 0 || tempDLLimit.getValue() > 0 );

		ParameterChangeListener adapter =
				p -> Utils.execSWTThread(() -> activate.setEnabled( tempULLimit.getValue() > 0 || tempDLLimit.getValue() > 0 ));
			
		tempULLimit.addChangeListener( adapter );
		tempDLLimit.addChangeListener( adapter );
	}
	
	private void delete() {
		Utils.disposeComposite(composite);
	}

	private String getFullTitle() {
		return( MessageText.getString( "label.quick.config" ));
	}



	private Composite getComposite() {
		return composite;
	}

	private void refresh() {

	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = event.getView();
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;
    }

    return true;
  }

	public static void
	addDoneDownloadingOption(
			Composite		comp,
			boolean			include_script_setting )
	{
		String[][]	action_details = ConfigSectionStartShutdown.getActionDetails();

		StringListSwtParameter dc = new StringListSwtParameter(comp,
				"On Downloading Complete Do", "ConfigView.label.stop.downcomp", null,
				action_details[1], action_details[0], true, null);

		if ( include_script_setting ){


			final FileSwtParameter dc_script = new FileSwtParameter(comp, "On Downloading Complete Script",  "label.script.to.run", new String[0], null);

			boolean	is_script = dc.getValue().startsWith( "RunScript" );

			dc_script.setEnabled( is_script );

			dc.addChangeListener(
					p -> dc_script.setEnabled(dc.getValue().startsWith("RunScript")));
		}
	}
}
