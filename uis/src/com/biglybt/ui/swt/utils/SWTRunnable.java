/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.utils;

import com.biglybt.core.util.DebugLight;
import com.biglybt.ui.swt.Utils;
import org.eclipse.swt.widgets.Display;

/**
 * Run two different code paths depending on existence of display
 * Created by TuxPaper on 7/12/2017.
 */
public abstract class SWTRunnable
	implements Runnable
{
	public abstract void runWithDisplay(Display display);

	@Override
	public final void run() {
		try {
			Display display = Utils.getDisplayIfNotDisposing();
			if (display != null) {
				runWithDisplay(display);
			} else {
				runNoDisplay();
			}

		} catch (Throwable e) {

			DebugLight.printStackTrace(e);
		}
	}

	public void runNoDisplay() {
	}

}
