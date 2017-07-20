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

package com.biglybt.ui.common.util;

import org.apache.log4j.Logger;
import com.biglybt.core.logging.ILogEventListener;
import com.biglybt.core.logging.LogEvent;

/**
 * @author tobi
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class LGLogger2Log4j implements ILogEventListener {

	public static Logger core = Logger.getLogger("biglybt.core");

	private static LGLogger2Log4j inst = null;

	public static LGLogger2Log4j getInstance() {
		if (inst == null)
			inst = new LGLogger2Log4j();
		return inst;
	}

	public static void set() {
		com.biglybt.core.logging.Logger.addListener(getInstance());
	}

	@Override
	public void log(LogEvent event) {
		if (event.entryType == LogEvent.LT_ERROR)
			core.error(event.text);
		else if (event.entryType == LogEvent.LT_WARNING)
			core.log(SLevel.CORE_WARNING, event.text);
		else
			core.log(SLevel.CORE_INFO, event.text);
	}
}
