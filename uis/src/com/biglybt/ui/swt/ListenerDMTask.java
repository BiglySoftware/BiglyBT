/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;

/**
 * SWT {@link Listener} that walks through a list of {@link DownloadManager}
 * objects, executing {@link #run(DownloadManager)} for each.
 */
abstract class ListenerDMTask
	implements Listener
{
	private DownloadManager[] dms;

	private boolean ascending;

	private boolean async;

	public ListenerDMTask(DownloadManager[] dms) {
		this(dms, true);
	}

	public ListenerDMTask(DownloadManager[] dms, boolean ascending) {
		this.dms = dms;
		this.ascending = ascending;
	}

	public ListenerDMTask(DownloadManager[] dms, boolean ascending, boolean async) {
		this.dms = dms;
		this.ascending = ascending;
		this.async = async;
	}

	// One of the following methods should be overridden.
	public void run(DownloadManager dm) {
	}

	public void run(DownloadManager[] dm) {
	}

	@Override
	public void handleEvent(Event event) {
		if (async) {

			new AEThread2("DMTask:async", true) {
				@Override
				public void run() {
					go();
				}
			}.start();
		} else {

			go();
		}
	}

	public void go() {
		try {
			DownloadManager dm = null;
			for (int i = 0; i < dms.length; i++) {
				dm = dms[ascending ? i : (dms.length - 1) - i];
				if (dm == null) {
					continue;
				}
				this.run(dm);
			}
			this.run(dms);
		} catch (Exception e) {
			Debug.printStackTrace(e);
		}
	}
}