/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.console.commands;

import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.logging.ILogEventListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.ui.console.ConsoleInput;

import com.biglybt.core.CoreFactory;

/**
 * @author TuxPaper
 * @created Dec 21, 2006
 *
 */
public class TorrentLog extends TorrentCommand implements ILogEventListener
{
	private static int MODE_OFF = 0;
	private static int MODE_ON = 1;
	private static int MODE_FLIP = 2;

	private static SimpleDateFormat dateFormatter;

	private static FieldPosition formatPos;

	private int mode = 0;

	private AEMonitor dms_mon = new AEMonitor("TorrentLog");

	private ArrayList dms = new ArrayList();

	private boolean	gm_listener_added;

	static {
		dateFormatter = new SimpleDateFormat("[h:mm:ss.SSS] ");
		formatPos = new FieldPosition(0);
	}

	/**
	 * @param commandNames
	 * @param action
	 */
	public TorrentLog() {
		super("tlog", "tl", "Torrent Logging");
	}

	@Override
	public void execute(String commandName, ConsoleInput ci, List<String> args) {
		mode = MODE_ON;
		Vector newargs = new Vector(args);
		if (newargs.isEmpty()) {
			mode = MODE_FLIP;
		} else if (newargs.contains("off")) {
			newargs.removeElement("off");
			mode = MODE_OFF;
		} else if (!newargs.contains("on")) {
			mode = MODE_FLIP;
		}
		super.execute(commandName, ci, args);
	}

	@Override
	protected boolean performCommand(ConsoleInput ci, DownloadManager dm,
	                                 List args) {
		try {
			dms_mon.enter();

				// defer this so that a non-running core doesn't prevent console ui init

			if ( !gm_listener_added ){

				gm_listener_added = true;

				GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
				gm.addListener(new GlobalManagerAdapter() {
					@Override
					public void downloadManagerRemoved(DownloadManager dm) {
						dms.remove(dm);
					}
				}, false);
			}

			boolean turnOn;
			if (mode == MODE_FLIP) {
				turnOn = !dms.contains(dm);
			} else {
				turnOn = mode == MODE_ON;
			}

			if (turnOn) {
				ci.out.print("->on] ");
				if (dms.contains(dm)) {
					return true;
				}
				dms.add(dm);
				if (dms.size() == 1) {
					Logger.addListener(this);
				}
			} else {
				ci.out.print("->off] ");
				dms.remove(dm);
				if (dms.size() == 0) {
					Logger.removeListener(this);
				}
			}
		} catch (Exception e) {
			e.printStackTrace(ci.out);
			return false;
		} finally {
			dms_mon.exit();
		}
		return true;
	}

	@Override
	public String getCommandDescriptions() {
		return "tl [on|off]\tTorrentLogging";
	}

	@Override
	public void log(LogEvent event) {
		boolean bMatch = false;

		if (event.relatedTo == null) {
			return;
		}

		try {
			dms_mon.enter();

			for (int i = 0; !bMatch && i < event.relatedTo.length; i++) {
				Object obj = event.relatedTo[i];

				if (obj == null)
					continue;

				for (int j = 0; !bMatch && j < dms.size(); j++) {
					if (obj instanceof LogRelation) {
						//System.err.println(obj.getClass().getSimpleName() + " is Logrelation");

						Object newObj = ((LogRelation) obj).queryForClass(DownloadManager.class);
						if (newObj != null)
							obj = newObj;
					}

					//System.err.println(obj.getClass().getName() + " matches " + filter[j].getClass().getSimpleName() + "?");

					if (obj == dms.get(j))
						bMatch = true;
				} // for filter
			} // for relatedTo

		} finally {
			dms_mon.exit();
		}

		if (bMatch) {
			final StringBuffer buf = new StringBuffer();
			dateFormatter.format(event.timeStamp, buf, formatPos);
			buf.append("{").append(event.logID).append("} ");

			buf.append(event.text);
			if (event.relatedTo != null) {
				buf.append("; \t| ");
				for (int j = 0; j < event.relatedTo.length; j++) {
					Object obj = event.relatedTo[j];
					if (j > 0)
						buf.append("; ");
					if (obj instanceof LogRelation) {
						buf.append(((LogRelation) obj).getRelationText());
					} else if (obj != null) {
						buf.append(obj.getClass().getName()).append(": '").append(
								obj.toString()).append("'");
					}
				}
			}
			System.out.println(buf.toString());
		}
	}

}
