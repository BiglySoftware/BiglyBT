/*
 * Created on 07/04/2006
 * Created by David Mohr, based on Alias.java by Paul Duran
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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
 *
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.biglybt.ui.console.commands;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.ui.console.ConsoleInput;

/**
 * the priority command changes the priority of files within a torrent
 * @author dmohr
 */
public class Priority extends OptionsConsoleCommand {

	public Priority() {
		super("prio");
	}

	@Override
	public String getCommandDescriptions() {
		return "prio [#torrent] [#file|range(i.e. 1-2,5)|all] [normal|high|dnd|del]";
	}

	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println("> -----");
		out.println("Usage: prio [torrent] [file(s)] [priority]");
		out.println("Options:");
		out.println("\t[torrent]\tThe torrent number from 'show torrents'");
		out.println("\t[file(s)] is one of:");
		out.println("\t\t\t#file:\tthe file number from 'show [#torrent]',");
		out.println("\t\t\trange:\ta range of file numbers, i.e. 1-3 or 1-10,12-15 or 1,3,5-8 ,");
		out.println("\t\t\tall:\t 'all' applies priority to all files of the torrent");
		out.println("\t[priority] is one of:");
		out.println("\t\t\tnormal\tNormal priority");
		out.println("\t\t\thigh  \tHigh priority");
		out.println("\t\t\tdnd   \tDo not download (skip)");
		out.println("\t\t\tdel   \tDo not download & delete file");
		out.println("> -----");
	}

	private static final int NORMAL=1;
	private static final int HIGH=2;
	private static final int DONOTDOWNLOAD=3;
	private static final int DELETE=4;
	private static final String[] priostr = { "Normal", "High", "DoNotDownload", "Delete" };

	private int newprio;

	@Override
	public void execute(String commandName, ConsoleInput console, CommandLine commandLine) {

		String tnumstr, fnumstr, newpriostr;
		int tnumber;
		DiskManagerFileInfo[] files;
		String[] sections;
		List args = commandLine.getArgList();
		LinkedList fs,fe;
		DownloadManager dm;

		if( args.isEmpty() )
		{
			console.out.println("Torrent # required!");
			return;
		} else {
			tnumstr = (String) args.remove(0);
		}
		if( args.isEmpty() )
		{
			console.out.println("File # required!");
			return;
		} else {
			fnumstr = (String) args.remove(0);
		}

		if ((console.torrents == null) || console.torrents.isEmpty()) {
			console.out.println("> Command 'prio': No torrents in list (try 'show torrents' first).");
			return;
		}

		try {
			tnumber = Integer.parseInt(tnumstr);
			if ((tnumber == 0) || (tnumber > console.torrents.size())) {
				console.out.println("> Command 'prio': Torrent #" + tnumber + " unknown.");
				return;
			}

			dm = (DownloadManager) console.torrents.get(tnumber - 1);
			files = dm.getDiskManagerFileInfo();
		}
		catch (Exception e) {
			e.printStackTrace();
			console.out.println("> Command 'prio': Torrent # '" + tnumstr + "' unknown.");
			return;
		}

		if( args.isEmpty() )
		{
			console.out.println("> Command 'prio': missing parameter for new priority");
			return;
		} else {
			newpriostr = (String) args.remove(0);
		}

		if (newpriostr.equalsIgnoreCase("normal")) {
			newprio = NORMAL;
		} else if (newpriostr.equalsIgnoreCase("high")) {
			newprio = HIGH;
		} else if (newpriostr.equalsIgnoreCase("dnd")) {
			newprio = DONOTDOWNLOAD;
		} else if (newpriostr.equalsIgnoreCase("del")) {
			newprio = DELETE;
		} else {
			console.out.println("> Command 'prio': unknown priority "
					+ newpriostr);
			return;
		}

		if (fnumstr.equalsIgnoreCase("all")) {
			sections = new String[1];
			sections[0] = "1-"+files.length;
		} else
			sections = fnumstr.split(",");

		fs = new LinkedList();
		fe = new LinkedList();

		int dash,start,end;
		for (int i=0; i<sections.length; i++) {
			try {
				if ((dash = sections[i].indexOf('-')) != -1) {
					start = Integer.parseInt(sections[i].substring(0,dash));
					end = Integer.parseInt(sections[i].substring(dash+1));
				} else
					start = end = Integer.parseInt(sections[i]);
				if ((start == 0) || (end > files.length)) {
					console.out.println("> Command 'prio': Invalid file range " + sections[i]);
					return;
				}
				if (start > end) {
					console.out.println("> Command 'prio': Invalid file range '"+sections[i]+"'");
				}

				// -1 compensates for 0-based offsets
				fs.add(new Integer(start - 1));
				fe.add(new Integer(end - 1));
			} catch (Exception e) {
				console.out.println("> Command 'prio': File # '" + sections[i]
						+ "' unknown.");
				return;
			}
		}

//		console.out.println("DM was " + dm.getState());
		if ((newprio == DELETE) && (dm.getState() != DownloadManager.STATE_STOPPED)) {
			try {
				dm.stopIt( DownloadManager.STATE_STOPPED, false, false );
			} catch (Exception e) {
				console.out.println("Failed to stop torrent " + tnumber);
				return;
			}
		}

//		console.out.println("DM is " + dm.getState());
		int nummod = 0;
		while (fs.size() > 0) {
			start = ((Integer) fs.removeFirst()).intValue();
			end = ((Integer) fe.removeFirst()).intValue();
			for (int i = start; i <= end; i++) {
				nummod++;
				// DEBUG
//				console.out.println("Setting priority for file " + i + " to " + newprio);
				if (newprio == NORMAL) {
					files[i].setPriority(0);
					files[i].setSkipped(false);
				} else if (newprio == HIGH) {
					files[i].setPriority(1);
					files[i].setSkipped(false);
				} else if (newprio == DONOTDOWNLOAD) {
					files[i].setPriority(0);
					files[i].setSkipped(true);
				} else if (newprio == DELETE) {
					int st = files[i].getStorageType();
					int target_st = -1;
					if ( st == DiskManagerFileInfo.ST_LINEAR ){
						target_st = DiskManagerFileInfo.ST_COMPACT;
					}else if ( st == DiskManagerFileInfo.ST_REORDER ){
						target_st = DiskManagerFileInfo.ST_REORDER_COMPACT;
					}
					if (target_st != -1 &&
						files[i].setStorageType(target_st)) {
						files[i].setPriority(0);
						files[i].setSkipped(true);
					} else {
						console.out.println("> Command 'prio': Failed to delete file " + (i+1));
						nummod--;
					}
				}
			}
		}
		if ((newprio == DELETE) && (dm.getState() == DownloadManager.STATE_STOPPED)) {
			try {
				dm.stopIt( DownloadManager.STATE_QUEUED, false, false );
			} catch (Exception e) {
				console.out.println("Failed to restart torrent " + tnumber);
				return;
			}
		}

//		console.out.println("DM is again " + dm.getState());

		console.out.println(nummod + " file(s) priority set to " + priostr[newprio-1]);
	}

}
