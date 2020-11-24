/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.config;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.backup.BackupManager;
import com.biglybt.core.backup.BackupManagerFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.*;

import static com.biglybt.core.config.ConfigKeys.BackupRestore.*;

public class ConfigSectionBackupRestore
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "backuprestore";

	public static final String PP_BACKUP_LAST_TIME = "lasttime";

	public static final String PP_BACKUP_LAST_ERR = "lasterr";

	private ConfigDetailsCallback cbManualBackup;

	private ConfigDetailsCallback cbBackupNow;

	private ConfigDetailsCallback cbRestore;

	public ConfigSectionBackupRestore() {
		super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	public void init(ConfigDetailsCallback cbManualBackup,
			ConfigDetailsCallback cbBackupNow, ConfigDetailsCallback cbRestore) {
		this.cbManualBackup = cbManualBackup;
		this.cbBackupNow = cbBackupNow;
		this.cbRestore = cbRestore;
	}

	@Override
	public void build() {
		Core core = CoreFactory.getSingleton();
		BackupManager backup_manager = BackupManagerFactory.getManager(core);
		List<Parameter> listBackupParams = new ArrayList<>();

		add("overview", new LabelParameterImpl("ConfigView.section.br.overview"));

		// wiki link

		add(SECTION_ID + ".link",
				new HyperlinkParameterImpl("ConfigView.label.please.visit.here",
						Wiki.BACKUP_AND_RESTORE));

		// backup

		long backup_time = backup_manager.getLastBackupTime();
		InfoParameterImpl paramInfoLastTime = new InfoParameterImpl(null,
				"br.backup.last.time",
				backup_time == 0 ? "" : String.valueOf(new Date(backup_time)));
		add(PP_BACKUP_LAST_TIME, paramInfoLastTime, listBackupParams);
		InfoParameterImpl paramInfoLastErr = new InfoParameterImpl(null,
				"br.backup.last.error", backup_manager.getLastBackupError());
		add(PP_BACKUP_LAST_ERR, paramInfoLastErr, listBackupParams);

		if (cbManualBackup != null) {
			ActionParameterImpl paramBackupButton = new ActionParameterImpl(
					"br.backup.manual.info", "br.backup");
			paramBackupButton.setStyle(ActionParameter.STYLE_BUTTON);
			paramBackupButton.addListener(
					param -> cbManualBackup.run(mapPluginParams));
			add("btnManualBackup", paramBackupButton, listBackupParams);
		}

		BooleanParameterImpl paramEnableBackup = new BooleanParameterImpl(
				BCFG_BACKUP_AUTO_ENABLE, "br.backup.auto.enable");
		add(paramEnableBackup, listBackupParams);

		//

		DirectoryParameterImpl paramPath = new DirectoryParameterImpl(
				SCFG_BACKUP_AUTO_DIR, "ConfigView.section.file.defaultdir.ask");
		add(paramPath, listBackupParams);
		paramPath.setDialogTitleKey("ConfigView.section.file.defaultdir.ask");
		paramPath.setDialogMessageKey("br.backup.auto.dir.select");

		if (paramPath.getValue().length() == 0) {
			String def_dir = COConfigurationManager.getStringParameter(
					SCFG_BACKUP_FOLDER_DEFAULT);

			paramPath.setValue(def_dir);
		}

		paramPath.addListener(p -> {
			String path = ((DirectoryParameter) p).getValue();
			COConfigurationManager.setParameter(SCFG_BACKUP_FOLDER_DEFAULT, path);
		});

		//
		
		BooleanParameterImpl paramDoPlugins = new BooleanParameterImpl(
				BCFG_BACKUP_PLUGINS, "br.backup.do.plugins");

		add(paramDoPlugins, listBackupParams);
		
		IntParameterImpl paramBackupDays = new IntParameterImpl(
				ICFG_BACKUP_AUTO_EVERYDAYS, ICFG_BACKUP_AUTO_EVERYDAYS, 0,
				Integer.MAX_VALUE);
		add(paramBackupDays, listBackupParams);

		IntParameterImpl paramBackupHours = new IntParameterImpl(
				ICFG_BACKUP_AUTO_EVERYHOURS, ICFG_BACKUP_AUTO_EVERYHOURS, 0,
				Integer.MAX_VALUE);
		add(paramBackupHours, listBackupParams);

		IntParameterImpl paramAutoRetain = new IntParameterImpl(
				ICFG_BACKUP_AUTO_RETAIN, ICFG_BACKUP_AUTO_RETAIN, 1,
				Integer.MAX_VALUE);
		add(paramAutoRetain, listBackupParams);

		BooleanParameterImpl paramNotify = new BooleanParameterImpl(
				BCFG_BACKUP_NOTIFY, "br.backup.notify");
		paramNotify.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramNotify, listBackupParams);

		ActionParameterImpl paramBackupNow = new ActionParameterImpl(
				"br.backup.auto.now", "br.test");
		add("backupNow", paramBackupNow, listBackupParams);

		paramBackupNow.addListener(param -> {
			if (cbBackupNow == null) {
				backup_manager.runAutoBackup(null);
			} else {
				cbBackupNow.run(mapPluginParams);
			}
		});

		ParameterListener enableListener = (n)->{
			
			boolean enable = paramEnableBackup.getValue();
			
			boolean hoursEnable = enable && paramBackupDays.getValue()==0;

			paramPath.setEnabled( enable );
			paramBackupDays.setEnabled( enable );
			paramBackupHours.setEnabled( hoursEnable );
			paramAutoRetain.setEnabled( enable );
			paramNotify.setEnabled( enable );
			paramBackupNow.setEnabled( enable );
		};
		
		paramEnableBackup.addListener(enableListener);
		paramBackupDays.addListener(enableListener);
		
		enableListener.parameterChanged(null);
		
		add(new ParameterGroupImpl("br.backup", listBackupParams));

		// restore

		if (cbRestore != null) {
			List<Parameter> listRestoreParams = new ArrayList<>();

			ActionParameterImpl paramButtonRestore = new ActionParameterImpl(
					"br.restore.info", "br.restore");
			add(SECTION_ID + ".restore", paramButtonRestore, listRestoreParams);

			paramButtonRestore.addListener(param -> cbRestore.run(mapPluginParams));

			add(new BooleanParameterImpl(BCFG_RESTORE_AUTOPAUSE,
					BCFG_RESTORE_AUTOPAUSE), listRestoreParams);

			add(new ParameterGroupImpl("br.restore", listRestoreParams));
		}

	}
}
