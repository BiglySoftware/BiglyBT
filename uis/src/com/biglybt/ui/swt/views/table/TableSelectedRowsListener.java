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

package com.biglybt.ui.swt.views.table;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.common.table.TableGroupRowRunner;
import com.biglybt.ui.common.table.TableView;

/**
 * Listener primarily for Menu Selection.  Implement run(TableRowCore) and it
 * will get called for each row the user has selected.
 */
public abstract class TableSelectedRowsListener
	extends TableGroupRowRunner
	implements Listener
{
	private final TableView<?> tv;
	private final boolean getOffSWT;

	public TableSelectedRowsListener(TableView<?> impl, boolean getOffSWT) {
		tv = impl;
		this.getOffSWT = getOffSWT;
	}

	/**
	 * triggers the event off of the SWT thread
	 * @param impl
	 */
	public TableSelectedRowsListener(TableView<?> impl) {
		tv = impl;
		this.getOffSWT = true;
	}

	/** Event information passed in via the Listener.  Accessible in
	 * run(TableRowSWT).
	 */
	protected Event event;

	/** Process the trapped event.  This function does not need to be overidden.
	 * @param e event information
	 */
	@Override
	public final void handleEvent(Event e) {
		event = e;
		if (getOffSWT) {
			Utils.getOffOfSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					tv.runForSelectedRows(TableSelectedRowsListener.this);
				}
			});
		} else {
			tv.runForSelectedRows(this);
		}
	}
}
