/*
 * Created on 04/12/2004
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
package com.biglybt.ui.console.commands;

import java.util.List;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManagerDownloadRemovalVetoException;
import com.biglybt.ui.console.ConsoleInput;

/**
 * console command to remove a torrent.
 * extracted from the Torrent command written by tobias
 */
public class TorrentRemove extends TorrentCommand {

	public TorrentRemove()
	{
		super("remove", "r", "Removing");
	}

	@Override
	protected boolean performCommand(ConsoleInput ci, DownloadManager dm, List args) {
		try {
			ci.getGlobalManager().removeDownloadManager(dm);
		} catch (GlobalManagerDownloadRemovalVetoException e) {
			ci.out.println("> Veto when removing torrent (" + e.getMessage() + ")");
			return false;
		} catch (Exception e) {
			e.printStackTrace(ci.out);
			return false;
		}
		return true;
	}

	@Override
	public String getCommandDescriptions() {
		return("remove (<torrentoptions>)\tr\tRemove torrent(s).");
	}

}
