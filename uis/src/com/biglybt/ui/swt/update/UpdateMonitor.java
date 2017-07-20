/*
 * Created on 7 mai 2004
 * Created by Olivier Chalouhi
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
package com.biglybt.ui.swt.update;

import java.io.File;

import com.biglybt.core.Core;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.StringListChooser;
import com.biglybt.ui.swt.progress.IProgressReport;
import com.biglybt.ui.swt.progress.IProgressReportConstants;
import com.biglybt.ui.swt.progress.IProgressReporter;
import com.biglybt.ui.swt.progress.IProgressReporterListener;
import com.biglybt.ui.swt.progress.ProgressReportingManager;
import com.biglybt.update.CoreUpdateChecker;

import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.update.*;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;

/**
 * @author Olivier Chalouhi
 *
 */
public class UpdateMonitor
	implements UpdateCheckInstanceListener
{
	private static final LogIDs LOGID = LogIDs.GUI;

	public static final long AUTO_UPDATE_CHECK_PERIOD 		= 23 * 60 * 60 * 1000; // 23 hours
	public static final long AUTO_UPDATE_CHECK_PERIOD_BETA 	= 4 * 60 * 60 * 1000; // 4 hours

	private static UpdateMonitor singleton;

	private static final AEMonitor class_mon = new AEMonitor("UpdateMonitor:class");
	private final UpdateManagerListener updateManagerListener;
	private final UpdateManagerVerificationListener updateManagerVerificationListener;
	private final TimerEventPeriodic timerAutoCheck;

	public static UpdateMonitor getSingleton(Core core) {
		try {
			class_mon.enter();

			if (singleton == null) {

				singleton = new UpdateMonitor(core);
			}

			return (singleton);

		} finally {

			class_mon.exit();
		}
	}

	public static void destroySingleton() {
		try {
			class_mon.enter();

			if (singleton != null) {

				singleton.dispose();
				singleton = null;
			}

		} finally {

			class_mon.exit();
		}
	}


	private Core azCore;

	private UpdateWindow current_update_window;

	private UpdateCheckInstance current_update_instance;

	private long last_recheck_time;

	protected UpdateMonitor(Core _core) {
		azCore = _core;

		PluginInterface defPI = PluginInitializer.getDefaultInterface();
		UpdateManager um = defPI.getUpdateManager();

		updateManagerListener = new UpdateManagerListener() {
			@Override
			public void checkInstanceCreated(UpdateCheckInstance instance) {
				instance.addListener(UpdateMonitor.this);

				if (!instance.isLowNoise()) {

					new updateStatusChanger(instance);
				}
			}
		};
		um.addListener(updateManagerListener);

		updateManagerVerificationListener = new UpdateManagerVerificationListener() {
			@Override
			public boolean acceptUnVerifiedUpdate(final Update update) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					String title = MessageText.getString("UpdateMonitor.messagebox.accept.unverified.title");
					String text = MessageText.getString("UpdateMonitor.messagebox.accept.unverified.text", new String[]{
							update.getName()
					});
					UIFunctionsUserPrompter prompter = uiFunctions.getUserPrompter(title, text, new String[]{
							MessageText.getString("Button.yes"),
							MessageText.getString("Button.no")
					}, 1);
					prompter.setRemember("UpdateMonitor.messagebox.accept.unverified", false,
							MessageText.getString("MessageBoxWindow.nomoreprompting"));
					prompter.setAutoCloseInMS(0);
					prompter.open(null);
					return prompter.waitUntilClosed() == 0;
				}

				return false;
			}

			@Override
			public void verificationFailed(final Update update, final Throwable cause) {
				final String cause_str = Debug.getNestedExceptionMessage(cause);
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					String title = MessageText.getString("UpdateMonitor.messagebox.verification.failed.title");
					String text = MessageText.getString("UpdateMonitor.messagebox.verification.failed.text", new String[]{
							update.getName(),
							cause_str
					});
					uiFunctions.promptUser(title, text, new String[]{
							MessageText.getString("Button.ok")
					}, 0, null, null, false, 0, null);
				}
			}
		};
		um.addVerificationListener(updateManagerVerificationListener);


		timerAutoCheck = SimpleTimer.addPeriodicEvent("UpdateMon:autocheck",
				COConfigurationManager.getBooleanParameter("Beta Programme Enabled") ? AUTO_UPDATE_CHECK_PERIOD_BETA : AUTO_UPDATE_CHECK_PERIOD,
				new TimerEventPerformer() {
					@Override
					public void perform(TimerEvent ev) {
						performAutoCheck(false);
					}
				});

		DelayedTask delayed_task =
			UtilitiesImpl.addDelayedTask(
				"Update Check",
				new Runnable()
				{
					@Override
					public void
					run()
					{
						// check for non-writeable app dir on non-vista platforms (vista we've got a chance of
						// elevating perms when updating) and warn user. Particularly useful on OSX when
						// users haven't installed properly

						if ( !( Constants.isWindowsVistaOrHigher || Constants.isUnix || SystemProperties.isJavaWebStartInstance())){

							String	app_str = SystemProperties.getApplicationPath();

							if ( !new File(app_str).canWrite()){

								final UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

								if ( uiFunctions != null ){

									if ( app_str.endsWith( File.separator )){

										app_str = app_str.substring(0, app_str.length()-1);
									}

									final String f_app_str = app_str;

									Utils.execSWTThread(
										new Runnable()
										{
											@Override
											public void
											run()
											{
												UIFunctionsUserPrompter prompt =
													uiFunctions.getUserPrompter(
														MessageText.getString("updater.cant.write.to.app.title"),
														MessageText.getString("updater.cant.write.to.app.details", new String[]{f_app_str}),
														new String[]{ MessageText.getString( "Button.ok" )},
														0 );

												//prompt.setHtml( "http://a.b.c/" );

												prompt.setIconResource( "warning" );

												prompt.setRemember( "UpdateMonitor.can.not.write.to.app.dir.2", false,
														MessageText.getString( "MessageBoxWindow.nomoreprompting" ));

												prompt.open(null);
											}
										},
										true );
								}
							}
						}

						performAutoCheck(true);
					}
				});

		delayed_task.queue();
	}

	private void dispose() {
		PluginInterface defPI = PluginInitializer.getDefaultInterface();
		UpdateManager um = defPI.getUpdateManager();

		if (updateManagerListener != null) {
			um.removeListener(updateManagerListener);
		}
		if (updateManagerVerificationListener != null) {
			um.removeVerificationListener(updateManagerVerificationListener);
		}
		if (timerAutoCheck != null && !timerAutoCheck.isCancelled()) {
			timerAutoCheck.cancel();
		}
	}


	protected class updateStatusChanger
		implements IProgressReportConstants
	{
		UpdateCheckInstance instance;

		int check_num = 0;

		/*
		 * Creates a ProgressReporter for the update process
		 */
		IProgressReporter updateReporter = ProgressReportingManager.getInstance().addReporter(
				MessageText.getString("UpdateWindow.title"));

		protected updateStatusChanger(UpdateCheckInstance _instance) {

			instance = _instance;

			/*
			 * Init reporter and allow cancel
			 */
			updateReporter.setReporterType("reporterType_updater");
			updateReporter.setCancelAllowed(true);
			updateReporter.setTitle(MessageText.getString("updater.progress.window.title"));
			updateReporter.appendDetailMessage(format(instance, "added"));

			String name = instance.getName();
			if (MessageText.keyExists(name)) {
				updateReporter.setMessage(MessageText.getString(name));
			} else {
				updateReporter.setMessage(name);
			}

			updateReporter.setMinimum(0);
			updateReporter.setMaximum(instance.getCheckers().length);
			updateReporter.setSelection(check_num, null);

			/*
			 * Add a listener to the reporter for a cancel event and cancel the update
			 * check instance if the event is detected
			 */
			updateReporter.addListener(new IProgressReporterListener() {

				@Override
				public int report(IProgressReport progressReport) {
					if (progressReport.getReportType() == REPORT_TYPE_DONE
							|| progressReport.getReportType() == REPORT_TYPE_ERROR) {
						return RETVAL_OK_TO_DISPOSE;
					}

					if (progressReport.getReportType() == REPORT_TYPE_CANCEL) {
						if (null != instance) {
							instance.cancel();
						}
						return RETVAL_OK_TO_DISPOSE;
					}

					return RETVAL_OK;
				}

			});

			/*
			 * Add listener to the running state of the update check instance and forward
			 * to the reporter when they arrive
			 */
			instance.addListener(new UpdateCheckInstanceListener() {
				@Override
				public void cancelled(UpdateCheckInstance instance) {
					updateReporter.appendDetailMessage(format(instance,
							MessageText.getString("Progress.reporting.status.canceled")));

						updateReporter.cancel();
				}

				@Override
				public void complete(UpdateCheckInstance instance) {
					updateReporter.appendDetailMessage(format(instance,
							MessageText.getString("Progress.reporting.status.finished")));
					updateReporter.setDone();
				}
			});

			UpdateChecker[] checkers = instance.getCheckers();

			for (int i = 0; i < checkers.length; i++) {
				final UpdateChecker checker = checkers[i];

				/*
				 * Add update check listener to get running state
				 */
				checker.addListener(new UpdateCheckerListener() {

					@Override
					public void cancelled(UpdateChecker checker) {
						// we don't count a cancellation as progress step
						updateReporter.appendDetailMessage(format(checker,
								MessageText.getString("Progress.reporting.status.canceled")));
					}

					@Override
					public void completed(UpdateChecker checker) {

						updateReporter.appendDetailMessage(format(checker,
								MessageText.getString("Progress.reporting.status.finished")));

						updateReporter.setSelection(++check_num, null);
					}

					@Override
					public void failed(UpdateChecker checker) {

						updateReporter.appendDetailMessage(format(checker,
								MessageText.getString("Progress.reporting.default.error")));

						updateReporter.setSelection(++check_num, null);

						// notify user of a failed update, use default error message
						updateReporter.setErrorMessage(null);
					}
				});

				/*
				 * Add a listener to get the detail messages
				 */
				checker.addProgressListener(new UpdateProgressListener() {
					@Override
					public void reportProgress(String str) {
						updateReporter.appendDetailMessage(format(checker, "    " + str));
					}
				});
			}
		}
	}

	// ============================================================
	// Convenience methods for formatting the detail messages for
	// the update process
	// ============================================================

	private String format(UpdateCheckInstance instance, String str) {
		String name = instance.getName();
		if (MessageText.keyExists(name)) {
			name = MessageText.getString(name);
		}
		return name + " - " + str;
	}

	private String format(UpdateChecker checker, String str) {
		return "    " + checker.getComponent().getName() + " - " + str;
	}


	protected void requestRecheck()
	{
		if (Logger.isEnabled()){
			Logger.log(new LogEvent(LOGID, "UpdateMonitor: recheck requested" ));
		}

		performCheck( false, true, true, null );
	}

	protected void performAutoCheck(final boolean start_of_day) {
		boolean check_at_start = false;
		boolean check_periodic = false;
		boolean bOldSWT = SWT.getVersion() < 3139;

		// no update checks for java web start

		if (!SystemProperties.isJavaWebStartInstance()) {

			// force check when SWT is really old
			check_at_start = COConfigurationManager.getBooleanParameter("update.start")
					|| bOldSWT;
			check_periodic = COConfigurationManager.getBooleanParameter("update.periodic");
		}

		// periodic -> check at start as well

		check_at_start = check_at_start || check_periodic;

		if ((check_at_start && start_of_day) || (check_periodic && !start_of_day)) {

			performCheck(bOldSWT, true, false, null ); // this will implicitly do usage stats

		} else {

			new DelayedEvent("UpdateMon:wait2", 5000, new AERunnable() {
				@Override
				public void runSupport() {
					if (start_of_day) {
						UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
						if (uiFunctions != null) {
							uiFunctions.setStatusText("");
						}
					}

					CoreUpdateChecker.doUsageStats();
				}
			});
		}
	}

	public void
	performCheck(
		final boolean 						bForce,
		final boolean 						automatic,
		final boolean						isRecheck,
		final UpdateCheckInstanceListener 	l )
	{
		long now = SystemTime.getCurrentTime();

		if ( isRecheck ){

			if ( last_recheck_time > now || now - last_recheck_time < 23*60*60*1000 ){

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"skipping recheck as consecutive recheck too soon"));

				return;
			}

			last_recheck_time = now;

		}else{

			last_recheck_time	= 0;
		}

		if (SystemProperties.isJavaWebStartInstance()) {

			// just in case we get here somehome!
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID,
						"skipping update check as java web start"));

			return;
		}

		// kill any existing update window

		if (current_update_window != null && !current_update_window.isDisposed()) {
			current_update_window.dispose();
		}

		if (current_update_instance != null) {

			current_update_instance.cancel();
		}

		if ( bForce ){

			VersionCheckClient.getSingleton().clearCache();
		}

		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null) {
			uiFunctions.setStatusText("MainWindow.status.checking");
		}

		// take this off this GUI thread in case it blocks for a while

		AEThread2 t = new AEThread2("UpdateMonitor:kickoff", true) {
			@Override
			public void run() {
				UpdateManager um = PluginInitializer.getDefaultInterface().getUpdateManager();

				current_update_instance = um.createUpdateCheckInstance(bForce
						? UpdateCheckInstance.UCI_INSTALL : UpdateCheckInstance.UCI_UPDATE,
						"update.instance.update");

				if (!automatic) {

					current_update_instance.setAutomatic(false);
				}

				if (l != null) {
					current_update_instance.addListener(l);
				}
				current_update_instance.start();
			}
		};

		t.start();
	}

	@Override
	public void complete(final UpdateCheckInstance instance) {

		if ( instance.isLowNoise()){

			handleLowNoise( instance );

			return;
		}

		boolean hasDownloads = false;

		Update[] us = instance.getUpdates();

		// updates with zero-length downloaders exist for admin purposes
		// and shoudn't cause the window to be shown if only they exist

		for (int i = 0; i < us.length; i++) {

			if (us[i].getDownloaders().length > 0) {

				hasDownloads = true;

				break;
			}
		}

		try{
			int ui = (Integer)instance.getProperty( UpdateCheckInstance.PT_UI_STYLE );

			if ( ui == UpdateCheckInstance.PT_UI_STYLE_SIMPLE ){

				new SimpleInstallUI( this, instance );

				return;

			}else if ( ui == UpdateCheckInstance.PT_UI_STYLE_NONE ){

				new SilentInstallUI( this, instance );

				return;
			}

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		// we can get here for either update actions (triggered above) or for plugin
		// install actions (triggered by the plugin installer)

		boolean update_action = instance.getType() == UpdateCheckInstance.UCI_UPDATE;

		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null) {
			uiFunctions.setStatusText("");
		}



		// this controls whether or not the update window is displayed
		// note that we just don't show the window if this is set, we still do the
		// update check (as amongst other things we want ot know the latest
		// version of the core anyway

		if (hasDownloads) {

			// don't show another update if one's already there!

			UpdateWindow this_window = null;
			boolean autoDownload = COConfigurationManager.getBooleanParameter("update.autodownload");

			if (update_action) {
				if (!autoDownload
						&& (current_update_window == null || current_update_window.isDisposed())) {

					this_window = current_update_window = new UpdateWindow( this, azCore,instance);
				}
			} else {

				// always show an installer window

				this_window = new UpdateWindow( this, azCore, instance);
			}

			if (this_window != null) {

				for (int i = 0; i < us.length; i++) {

					if (us[i].getDownloaders().length > 0) {

						this_window.addUpdate(us[i]);
					}
				}

				this_window.updateAdditionComplete();

			} else {
				if (autoDownload) {
					new UpdateAutoDownloader(us, new UpdateAutoDownloader.cbCompletion() {
						@Override
						public void
						allUpdatesComplete(
							boolean requiresRestart,
							boolean bHadMandatoryUpdates)
						{
							Boolean b = (Boolean)instance.getProperty( UpdateCheckInstance.PT_CLOSE_OR_RESTART_ALREADY_IN_PROGRESS );

							if ( b != null && b ){

								return;
							}

							if (requiresRestart) {
								handleRestart();
							}else if ( bHadMandatoryUpdates ){

									// no restart and mandatory -> rescan for optional updates now

								requestRecheck();
							}
						}
					});
				} else {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
								"UpdateMonitor: user dialog already "
										+ "in progress, updates skipped"));
				}

			}
		} else {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "UpdateMonitor: check instance "
						+ "resulted in no user-actionable updates"));

		}
	}

	@Override
	public void cancelled(UpdateCheckInstance instance) {
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null) {
			uiFunctions.setStatusText("");
		}
	}

	protected void
	handleRestart()
 {
		final UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

		if ( uiFunctions != null ){

			int	visiblity_state = uiFunctions.getVisibilityState();

			if ( 	visiblity_state == UIFunctions.VS_TRAY_ONLY &&
					COConfigurationManager.getBooleanParameter( "Low Resource Silent Update Restart Enabled" )){

				uiFunctions.dispose( true, false );

			}else{

				uiFunctions.performAction(
					UIFunctions.ACTION_UPDATE_RESTART_REQUEST,
					Constants.isWindows7OrHigher,		// no timer for in 7 as they always get an elevation prompt so we don't want to shutdown and then leave\
														// Vuze down pending user authorisation of the update
					new UIFunctions.actionListener()
					{
						@Override
						public void
						actionComplete(
							Object	result )
						{
							if ((Boolean)result){

								uiFunctions.dispose(true, false);
							}
						}
					});
			}
		}else{

			Debug.out( "Can't handle restart as no ui functions available" );
		}
	}

	protected void
	addDecisionHandler(
		UpdateCheckInstance		instance )
	{
		instance.addDecisionListener(
		  		new UpdateManagerDecisionListener()
		  		{
		  			@Override
					  public Object
		  			decide(
		  				Update		update,
		  				int			decision_type,
		  				String		decision_name,
		  				String		decision_description,
		  				Object		decision_data )
		  			{
		  				if ( decision_type == UpdateManagerDecisionListener.DT_STRING_ARRAY_TO_STRING ){

		  					String[]	options = (String[])decision_data;

		  					Shell	shell = UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell();

		  					if ( shell == null ){

		  						Debug.out( "Shell doesn't exist" );

		  						return( null );
		  					}

		  					StringListChooser chooser = new StringListChooser( shell );

		  					chooser.setTitle( decision_name );
		  					chooser.setText( decision_description );

		  					for (int i=0;i<options.length;i++){

		  						chooser.addOption( options[i] );
		  					}

		  					String	result = chooser.open();

		  					return( result );
		  				}

		  				return( null );
		  			}
		  		});
	}

	protected void
	handleLowNoise(
		UpdateCheckInstance		instance )
	{
		addDecisionHandler( instance );

		Update[] updates = instance.getUpdates();

		try{
			for (int i=0;i<updates.length;i++){

				ResourceDownloader[] downloaders = updates[i].getDownloaders();

				for (int j=0;j<downloaders.length;j++){

					downloaders[j].download();
				}
			}

			boolean	restart_required = false;

			for (int i=0;i<updates.length;i++){

				if ( updates[i].getRestartRequired() == Update.RESTART_REQUIRED_YES ){

					restart_required = true;
				}
			}

			if ( restart_required ){

				handleRestart();
			}
		}catch( Throwable e ){

			// TODO:
			e.printStackTrace();
		}
	}
}
