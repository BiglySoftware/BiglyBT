/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
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

package com.biglybt.ui.swt.views.configsections;

import java.io.File;
import java.util.Date;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.backup.BackupManager;
import com.biglybt.core.backup.BackupManagerFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.ConfigSectionBackupRestore;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.BaseSwtParameter;
import com.biglybt.ui.swt.shells.MessageBoxShell;

import com.biglybt.pif.ui.config.InfoParameter;

import static com.biglybt.core.config.ConfigKeys.BackupRestore.*;

public class ConfigSectionBackupRestoreSWT
		extends ConfigSectionBackupRestore
		implements BaseConfigSectionSWT
{
	private Shell shell;

	public ConfigSectionBackupRestoreSWT() {
		BackupManager backup_manager = BackupManagerFactory.getManager(
				CoreFactory.getSingleton());

		init( //
				mapParams -> doManualBackup(backup_manager,
						() -> updateInfoParams(backup_manager, mapParams)),
				mapParams -> runBackup(backup_manager, null,
						() -> updateInfoParams(backup_manager, mapParams)),
				mapParams -> restoreBackup());
	}

	@Override
	public void configSectionCreate(Composite parent,
			Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam) {
		shell = parent.getShell();
	}

	private void doManualBackup(BackupManager backup_manager,
			Runnable stats_updater) {
		if (Utils.runIfNotSWTThread(
				() -> doManualBackup(backup_manager, stats_updater))) {
			return;
		}

		if (shell == null) {
			shell = Utils.findAnyShell();
		}
		String def_dir = COConfigurationManager.getStringParameter(
				SCFG_BACKUP_FOLDER_DEFAULT);

		DirectoryDialog dialog = new DirectoryDialog(shell, SWT.APPLICATION_MODAL);

		if (!def_dir.isEmpty()) {
			dialog.setFilterPath(def_dir);
		}

		dialog.setMessage(MessageText.getString("br.backup.folder.info"));
		dialog.setText(MessageText.getString("br.backup.folder.title"));

		String path = dialog.open();

		if (path != null) {

			COConfigurationManager.setParameter(SCFG_BACKUP_FOLDER_DEFAULT, path);

			runBackup(backup_manager, path, stats_updater);
		}
	}

	private void restoreBackup() {
		if (Utils.runIfNotSWTThread(this::restoreBackup)) {
			return;
		}

		String def_dir = COConfigurationManager.getStringParameter(
				SCFG_BACKUP_FOLDER_DEFAULT);

		DirectoryDialog dialog = new DirectoryDialog(shell, SWT.APPLICATION_MODAL);

		if (!def_dir.isEmpty()) {
			dialog.setFilterPath(def_dir);
		}

		dialog.setMessage(MessageText.getString("br.restore.folder.info"));

		dialog.setText(MessageText.getString("br.restore.folder.title"));

		final String path = dialog.open();

		if (path != null) {

			MessageBoxShell mb = new MessageBoxShell(
					SWT.ICON_WARNING | SWT.OK | SWT.CANCEL,
					MessageText.getString("br.restore.warning.title"),
					MessageText.getString("br.restore.warning.info"));

			mb.setDefaultButtonUsingStyle(SWT.CANCEL);
			mb.setParent(shell);

			mb.open(returnVal -> {
				if (returnVal != SWT.OK) {
					return;
				}

				final TextViewerWindow viewer = new TextViewerWindow(
						MessageText.getString("br.backup.progress"), null, "", true, true);

				viewer.setEditable(false);

				viewer.setOKEnabled(false);

				BackupManager backup_manager = BackupManagerFactory.getManager(
						CoreFactory.getSingleton());
				backup_manager.restore(new File(path),
						new BackupManager.BackupListener() {
							@Override
							public boolean reportProgress(String str) {
								return (append(str, false));
							}

							@Override
							public void reportComplete() {
								append("Restore Complete!", true);

								Utils.execSWTThread(() -> {
									MessageBoxShell mb1 = new MessageBoxShell(
											SWT.ICON_INFORMATION | SWT.OK,
											MessageText.getString(
													"ConfigView.section.security.restart.title"),
											MessageText.getString(
													"ConfigView.section.security.restart.msg"));
									mb1.setParent(shell);
									mb1.open(returnVal1 -> {
										UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();

										if (uiFunctions != null) {

											uiFunctions.dispose(true);
										}
									});
								});
							}

							@Override
							public void reportError(Throwable error) {
								append(
										"Restore Failed: " + Debug.getNestedExceptionMessage(error),
										true);
							}

							private boolean append(final String str, final boolean complete) {
								if (viewer.isDisposed() || str == null) {

									return (false);
								}

								Utils.execSWTThread(() -> {
									if (!viewer.isDisposed()) {

										if (str.endsWith("...")) {

											viewer.append(str);

										} else {

											viewer.append(str + "\r\n");
										}

										if (complete) {

											viewer.setOKEnabled(true);
										}
									}
								});

								return (true);
							}
						});

				viewer.goModal();

			});
		}
	}

	private static void updateInfoParams(BackupManager backup_manager,
			Map<String, ParameterImpl> mapParams) {
		InfoParameter paramInfoLastTime = (InfoParameter) mapParams.get(
				ConfigSectionBackupRestoreSWT.PP_BACKUP_LAST_TIME);
		InfoParameter paramInfoLastErr = (InfoParameter) mapParams.get(
				ConfigSectionBackupRestoreSWT.PP_BACKUP_LAST_ERR);
		if (paramInfoLastErr != null && paramInfoLastTime != null) {
			long backup_time = backup_manager.getLastBackupTime();

			paramInfoLastTime.setValue(
					backup_time == 0 ? "" : String.valueOf(new Date(backup_time)));

			paramInfoLastErr.setValue(backup_manager.getLastBackupError());
		}
	}

	private static void
	runBackup(
		BackupManager		backup_manager,
		String				path,
		final Runnable		stats_updater )

	{
		boolean modal = false;	// switch to non-modal after user request
		
		if (Utils.runIfNotSWTThread(
				() -> runBackup(backup_manager, path, stats_updater))) {
			return;
		}

		final TextViewerWindow viewer =
			new TextViewerWindow(
					MessageText.getString( "br.backup.progress" ),
					null, "", modal, modal );

		viewer.setEditable( false );

		viewer.setOKEnabled( false );

		BackupManager.BackupListener	listener =
			new BackupManager.BackupListener()
			{
				@Override
				public boolean
				reportProgress(
					String		str )
				{
					return( append( str, false ));
				}

				@Override
				public void
				reportComplete()
				{
					append( "Backup Complete!", true );
				}

				@Override
				public void
				reportError(
					Throwable 	error )
				{
					append( "Backup Failed: " + Debug.getNestedExceptionMessage( error ), true );
				}

				private boolean
				append(
					final String		str,
					final boolean		complete )
				{
					if ( viewer.isDisposed() || str == null){

						return( false );
					}

					Utils.execSWTThread(() -> 
						{
								if ( str.endsWith( "..." )){

									viewer.append( str );

								}else{

									viewer.append( str + "\r\n" );
								}

								if ( complete ){

									viewer.setOKEnabled( true );

									stats_updater.run();
							}
						});

					return( true );
				}
			};

		if ( path == null ){

			backup_manager.runAutoBackup( listener );

		}else{

			backup_manager.backup( new File( path ), listener );
		}

		if ( modal ){
		
			viewer.goModal();
		}
	}
}
