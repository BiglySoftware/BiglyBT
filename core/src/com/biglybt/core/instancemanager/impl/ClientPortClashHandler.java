/*
 * Created on 03-Jan-2006
 * Created by Paul Gardner
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

package com.biglybt.core.instancemanager.impl;

import java.net.InetAddress;

import com.biglybt.core.instancemanager.ClientInstance;
import com.biglybt.core.instancemanager.ClientInstanceManagerListener;
import com.biglybt.core.instancemanager.ClientInstanceTracked;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.RandomUtils;

public class
ClientPortClashHandler
	implements ClientInstanceManagerListener
{
	private final ClientInstance my_instance;

	private int	last_warned_tcp;
	private int	last_warned_udp;
	private int	last_warned_udp2;

	protected ClientPortClashHandler(
		ClientInstanceManagerImpl inst_man )
	{
		my_instance = inst_man.getMyInstance();

		inst_man.addListener( this );
	}

	protected void
	check(
		ClientInstance instance )
	{
		if ( instance == my_instance ){

			return;
		}

		InetAddress	my_ext 		= my_instance.getExternalAddress();
		InetAddress	other_ext 	= instance.getExternalAddress();

		if ( 	my_ext.isLoopbackAddress() ||
				other_ext.isLoopbackAddress() ||
				my_ext.equals( other_ext )){

			String	warning = null;

			int	my_tcp = my_instance.getTCPListenPort();

			if ( my_tcp != 0 && my_tcp != last_warned_tcp && my_tcp == instance.getTCPListenPort()){

				warning = "TCP " + my_tcp;

				last_warned_tcp	= my_tcp;
			}

			int	my_udp 	= my_instance.getUDPListenPort();
			int my_udp2	= my_instance.getUDPNonDataListenPort();

			int	other_udp 	= instance.getUDPListenPort();
			int other_udp2	= instance.getUDPNonDataListenPort();

			if ( my_udp != 0 && my_udp != last_warned_udp && ( my_udp == other_udp || my_udp == other_udp2 )){

				warning = (warning==null?"":(warning + ", ")) + "UDP " + my_udp;

				last_warned_udp	= my_udp;
			}

			if ( my_udp != my_udp2 && my_udp2 != 0 && my_udp2 != last_warned_udp2 && ( my_udp2 == other_udp || my_udp2 == other_udp2 )){

				warning = (warning==null?"":(warning + ", ")) + "UDP " + my_udp2;

				last_warned_udp2	= my_udp2;
			}


			if ( warning != null ){

				Logger.logTextResource(
					new LogAlert(true, LogAlert.AT_WARNING,"azinstancehandler.alert.portclash"),
					new String[]{
						warning,
						String.valueOf(RandomUtils.LISTEN_PORT_MIN ),
						String.valueOf(RandomUtils.LISTEN_PORT_MAX)});
			}
		}
	}

	@Override
	public void
	instanceFound(
		ClientInstance instance )
	{
		check( instance );
	}

	@Override
	public void
	instanceChanged(
		ClientInstance instance )
	{
		check( instance );
	}

	@Override
	public void
	instanceLost(
		ClientInstance instance )
	{
	}

	@Override
	public void
	instanceTracked(
		ClientInstanceTracked instance )
	{
	}
}
