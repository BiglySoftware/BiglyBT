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


package com.biglybt.core.backup;

import java.io.File;

public interface
BackupManager
{
	public void
	backup(
		File				parent_folder,
		BackupListener		listener );

	public void
	restore(
		File				backup_folder,
		BackupListener		listener );

	public void
	runAutoBackup(
		BackupListener		listener );

	public long
	getLastBackupTime();

	public String
	getLastBackupError();

	public interface
	BackupListener
	{
		/**
		 * @return false -> abandon process
		 */

		public boolean
		reportProgress(
			String		str );

		public void
		reportComplete();

		public void
		reportError(
			Throwable 	error );
	}
}
