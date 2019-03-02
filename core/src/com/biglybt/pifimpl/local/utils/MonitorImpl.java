/*
 * Created on May 20, 2005
 * Created by Alon Rohter
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
 *
 */

package com.biglybt.pifimpl.local.utils;

import com.biglybt.core.util.AEMonitorOld;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.utils.Monitor;


public class MonitorImpl implements Monitor {

	private static long	next_mon_id;

	private AEMonitorOld mon;

	protected
	MonitorImpl(
		PluginInterface		pi )
	{
		synchronized( MonitorImpl.class ){

		  mon = new AEMonitorOld("Plugin " + pi.getPluginID() + ":" + next_mon_id++ );
		}
	}


	@Override
	public void enter() {
	   mon.enter();
	}

	@Override
	public void exit(){
	    mon.exit();
	}

	 @Override
	 public boolean isOwned()
	 {
		 return( mon.isHeld());
	 }

	 @Override
	 public boolean hasWaiters()
	 {
		 return( mon.hasWaiters());
	 }
}
