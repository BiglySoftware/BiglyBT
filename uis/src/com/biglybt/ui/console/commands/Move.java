/*
 * Written and copyright 2001-2004 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 * Move.java
 *
 * Created on 23.03.2004
 *
 */
package com.biglybt.ui.console.commands;

import java.util.List;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.ui.console.ConsoleInput;

/**
 * @author Tobias Minich
 */
public class Move extends IConsoleCommand {

	public Move()
	{
		super("move", "m");
	}

	@Override
	public String getCommandDescriptions()
	{
		return("move <from #> [<to #>]\t\tm\tMove torrent from to to. If to is omitted, the torrent is moved to top or to the bottom if given negative.");
	}

	@Override
	public void execute(String commandName, ConsoleInput ci, List args) {
		if( args.isEmpty() )
		{
			ci.out.println("> Missing subcommand for 'move'\r\n> move syntax: move <#from> [<#to>]");
			return;
		}

		if (ci.torrents.isEmpty())
		{
			ci.out.println("> Command 'move': No torrents in list.");
			return;
		}

		int ncommand;
		int nmoveto = -1;
		boolean moveto = false;
		try {
			ncommand = Integer.parseInt((String) args.get(0));
			if (args.size() > 1) {
				nmoveto = Integer.parseInt((String) args.get(1));
				moveto = true;
			}
		} catch (NumberFormatException e) {
			ci.out.println("> Command 'move': Subcommand '" + args.get(0) + "' unknown.");
			return;
		}
		int number = Math.abs(ncommand);
		if (number == 0 || number > ci.torrents.size()) {
			ci.out.println("> Command 'move': Torrent #" + Integer.toString(number) + " unknown.");
			return;
		}
		DownloadManager dm = (DownloadManager) ci.torrents.get(number - 1);
		String name = dm.getDisplayName();
		if (name == null)
			name = "?";

		GlobalManager	gm = dm.getGlobalManager();

		if (moveto) {
			gm.moveTo(dm, nmoveto - 1);
			gm.fixUpDownloadManagerPositions();
			ci.out.println("> Torrent #" + Integer.toString(number) + " (" + name + ") moved to #" + Integer.toString(nmoveto) + ".");
		} else if (ncommand > 0) {
			if (gm.isMoveableUp(dm)) {
				while (gm.isMoveableUp(dm))
					gm.moveUp(dm);
				gm.fixUpDownloadManagerPositions();
				ci.out.println("> Torrent #" + Integer.toString(number) + " (" + name + ") moved to top.");
			} else {
				ci.out.println("> Torrent #" + Integer.toString(number) + " (" + name + ") already at top.");
			}
		} else {
			if (gm.isMoveableDown(dm)) {
				while (gm.isMoveableDown(dm))
					gm.moveDown(dm);
				gm.fixUpDownloadManagerPositions();
				ci.out.println("> Torrent #" + Integer.toString(number) + " (" + name + ") moved to bottom.");
			} else {
				ci.out.println("> Torrent #" + Integer.toString(number) + " (" + name + ") already at bottom.");
			}
		}
	}
}
