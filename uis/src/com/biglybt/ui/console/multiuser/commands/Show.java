/*
 * Created on 2/02/2005
 * Created by Paul Duran
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.biglybt.ui.console.multiuser.commands;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;

/**
 * subclass of Show command that is useful for multi-user
 * @author pauld
 */
public class Show extends com.biglybt.ui.console.commands.Show
{
	/**
	 *
	 */
	public Show() {
		super();
	}

	/**
	 * display a similar torrent summary to the superclass but also display the username
	 */
	@Override
	protected String getDefaultSummaryFormat() {
		return "[%o] " + super.getDefaultSummaryFormat();
	}

	/**
	 * adds an additional variable 'o' for torrent owner
	 */
	@Override
	protected String expandVariable(char variable, DownloadManager dm) {
		switch( variable )
		{
			case 'o':
				String user = dm.getDownloadState().getAttribute(DownloadManagerState.AT_USER);
				if( user == null )
					user = "admin";
				return user;
			default:
				return super.expandVariable(variable, dm);
		}
	}
}
