/*
 * Created on 26 May 2008
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

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.FileUtil;

/**
 * @author Allan Crooks
 *
 */
public class DownloadManagerMoveHandlerUtils {

    // Helper log functions.
	static void logInfo(String message, DownloadManager dm) {
		if ( dm != null && message != null ){
			String name = dm.getDisplayName();
			if ( !message.contains( name )){
				message = name + ": " + message;
			}
		}
		FileUtil.log( message );
		LogRelation lr = (dm instanceof LogRelation) ? (LogRelation)dm : null;
		if (lr == null) {return;}
		if (!Logger.isEnabled()) {return;}
		Logger.log(new LogEvent(lr, LogIDs.CORE, LogEvent.LT_INFORMATION, message));
	}

	static void logWarn(String message, DownloadManager dm) {
		if ( dm != null && message != null ){
			String name = dm.getDisplayName();
			if ( !message.contains( name )){
				message = name + ": " + message;
			}
		}
		FileUtil.log( message );
		LogRelation lr = (dm instanceof LogRelation) ? (LogRelation)dm : null;
		if (lr == null) {return;}
		if (!Logger.isEnabled()) {return;}
		Logger.log(new LogEvent(lr, LogIDs.CORE, LogEvent.LT_WARNING, message));
	}

	static void logError(String message, DownloadManager dm, Throwable e) {
		if ( dm != null && message != null ){
			String name = dm.getDisplayName();
			if ( !message.contains( name )){
				message = name + ": " + message;
			}
		}
		FileUtil.log( message, e );
		LogRelation lr = (dm instanceof LogRelation) ? (LogRelation)dm : null;
		if (lr == null) {return;}
		if (!Logger.isEnabled()) {return;}
		Logger.log(new LogEvent(lr, LogIDs.CORE, message, e));
	}

	static String describe(DownloadManager dm) {
		if (dm == null) {return "";}
		return "\"" + dm.getDisplayName() + "\"";
	}

}
