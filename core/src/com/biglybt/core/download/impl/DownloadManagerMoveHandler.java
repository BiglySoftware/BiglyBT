/*
 * Created on 23 May 2008
 * Created by Allan Crooks
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
package com.biglybt.core.download.impl;

import java.io.File;
import java.util.ArrayList;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.savelocation.SaveLocationChange;
import com.biglybt.pif.download.savelocation.SaveLocationManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;

/**
 * @author Allan Crooks
 *
 */
public class DownloadManagerMoveHandler extends DownloadManagerMoveHandlerUtils {

	public static SaveLocationManager CURRENT_HANDLER = DownloadManagerDefaultPaths.DEFAULT_HANDLER;

	private static boolean isApplicableDownload(DownloadManager dm) {
		if (!dm.isPersistent()) {
			logInfo(describe(dm) + " is not persistent, auto-move disabled.", dm);
			return false;
		}

		if (dm.getDownloadState().getFlag(DownloadManagerState.FLAG_DISABLE_AUTO_FILE_MOVE)) {
			logInfo(describe(dm) + " has auto-move exclusion flag set.", dm);
			return false;
		}

		return true;
	}

	public static SaveLocationChange onInitialisation(DownloadManager dm) {
		if (!isApplicableDownload(dm)) {return null;}
		try {return CURRENT_HANDLER.onInitialization(PluginCoreUtils.wrap(dm), true, true);}
		catch (Exception e) {
			logError("Error trying to determine initial download location.", dm, e);
			return null;
		}
	}

	public static SaveLocationChange onRemoval(DownloadManager dm) {
		if (!isApplicableDownload(dm)) {return null;}
		try {return CURRENT_HANDLER.onRemoval(PluginCoreUtils.wrap(dm), true, true);}
		catch (Exception e) {
			logError("Error trying to determine on-removal location.", dm, e);
			return null;
		}
	}

	public static SaveLocationChange onCompletion(DownloadManager dm, MoveCallback callback, boolean startOfDay ) {
		if (!isApplicableDownload(dm)) {return null;}

		if (dm.getDownloadState().getFlag(DownloadManagerState.FLAG_MOVE_ON_COMPLETION_DONE)) {
			if ( !startOfDay ){
				logInfo("Completion flag already set on " + describe(dm) + ", skip move-on-completion behaviour.", dm);
			}
			return null;
		}

		SaveLocationChange sc;
		try {sc = CURRENT_HANDLER.onCompletion(PluginCoreUtils.wrap(dm), true, true);}
		catch (Exception e) {
			logError("Error trying to determine on-completion location.", dm, e);
			return null;
		}

			// give caller the opportunity to perform the action *before* we set the completion-done
			// flag...

		if ( callback != null && sc != null ){

			callback.perform( sc );
		}

		logInfo("Setting completion flag on " + describe(dm) + ", may have been set before.", dm);
		dm.getDownloadState().setFlag(DownloadManagerState.FLAG_MOVE_ON_COMPLETION_DONE, true);
		return sc;
	}

	public static boolean canGoToCompleteDir(DownloadManager dm) {
		return (dm.isDownloadComplete(false) && isOnCompleteEnabled());
	}

	public static boolean isOnCompleteEnabled() {
		return COConfigurationManager.getBooleanParameter("Move Completed When Done");
	}

	public static boolean isOnRemovalEnabled() {
		return COConfigurationManager.getBooleanParameter("File.move.download.removed.enabled");
	}

	public static SaveLocationChange recalculatePath(DownloadManager dm) {
		Download download = PluginCoreUtils.wrap(dm);
		SaveLocationChange result = null;
		if (canGoToCompleteDir(dm)) {
			result = CURRENT_HANDLER.onCompletion(download, true, false);
		}
		if (result == null) {
			result = CURRENT_HANDLER.onInitialization(download, true, false);
		}
		return result;
	}

	/**
	 * Find all file locations that a download might exist in - this is used
	 * to see locate existing files to reuse to prevent downloads being re-added.
	 */
	public static File[] getRelatedDirs(DownloadManager dm) {
		ArrayList result = new ArrayList();
		Download d = PluginCoreUtils.wrap(dm);

		if (isOnCompleteEnabled()) {
			addFile(result, COConfigurationManager.getStringParameter("Completed Files Directory"));
			addFile(result, CURRENT_HANDLER.onCompletion(d, false, false));
			addFile(result, DownloadManagerDefaultPaths.DEFAULT_HANDLER.onCompletion(d, false, false));
		}
		if (isOnRemovalEnabled()) {
			addFile(result, COConfigurationManager.getStringParameter("File.move.download.removed.path"));
			addFile(result, CURRENT_HANDLER.onRemoval(d, false, false));
			addFile(result, DownloadManagerDefaultPaths.DEFAULT_HANDLER.onRemoval(d, false, false));
		}
		return (File[])result.toArray(new File[result.size()]);
	}

	private static void addFile(ArrayList l, SaveLocationChange slc) {
		if (slc != null) {addFile(l, slc.download_location);}
	}

	private static void addFile(ArrayList l, File f) {
		if (f != null && !l.contains(f)) {l.add(f);}
	}

	private static void addFile(ArrayList l, String s) {
		if (s != null && s.trim().length()!=0) {addFile(l, FileUtil.newFile(s));}
	}

	public interface
	MoveCallback
	{
		public void
		perform(
			SaveLocationChange		details );
	}
}
