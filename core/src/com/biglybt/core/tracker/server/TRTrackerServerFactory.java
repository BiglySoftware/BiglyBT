/*
 * File    : TRTrackerServerFactory.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.tracker.server;


import java.net.InetAddress;
import java.util.Map;

import com.biglybt.core.tracker.server.impl.TRTrackerServerFactoryImpl;

public class
TRTrackerServerFactory
{
	public static final int PR_TCP	= 1;
	public static final int PR_UDP	= 2;
	public static final int PR_DHT	= 3;

	public static TRTrackerServer
	create(
		int		protocol,
		int		port,
		boolean	apply_ip_filter,
		boolean	main_tracker )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( "<none>", protocol, port, null, false, apply_ip_filter, main_tracker, true, null ));
	}

	public static TRTrackerServer
	createSSL(
		int		protocol,
		int		port,
		boolean apply_ip_filter,
		boolean	main_tracker )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( "<none>", protocol, port, null, true, apply_ip_filter, main_tracker, true, null ));
	}

	public static TRTrackerServer
	create(
		String	name,
		int		protocol,
		int		port,
		boolean	apply_ip_filter,
		boolean	main_tracker )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( name, protocol, port, null, false, apply_ip_filter, main_tracker, true, null ));
	}

	public static TRTrackerServer
	create(
		String	name,
		int		protocol,
		int		port,
		boolean	apply_ip_filter,
		boolean	main_tracker,
		boolean	start_up_ready )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( name, protocol, port, null, false, apply_ip_filter, main_tracker, start_up_ready, null ));
	}

	public static TRTrackerServer
	createSSL(
		String	name,
		int		protocol,
		int		port,
		boolean apply_ip_filter,
		boolean	main_tracker )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( name, protocol, port, null, true, apply_ip_filter, main_tracker, true, null ));
	}

	public static TRTrackerServer
	createSSL(
		String	name,
		int		protocol,
		int		port,
		boolean apply_ip_filter,
		boolean	main_tracker,
		boolean	startup_ready )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( name, protocol, port, null, true, apply_ip_filter, main_tracker, startup_ready, null ));
	}

	public static TRTrackerServer
	create(
		String		name,
		int			protocol,
		int			port,
		InetAddress	bind_ip,
		boolean		apply_ip_filter,
		boolean		main_tracker )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( name, protocol, port, bind_ip, false, apply_ip_filter, main_tracker, true, null ));
	}

	public static TRTrackerServer
	createSSL(
		String		name,
		int			protocol,
		int			port,
		InetAddress	bind_ip,
		boolean 	apply_ip_filter,
		boolean		main_tracker )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( name, protocol, port, bind_ip, true, apply_ip_filter, main_tracker, true, null ));
	}

	public static TRTrackerServer
	create(
		String					name,
		int						protocol,
		int						port,
		InetAddress				bind_ip,
		boolean					apply_ip_filter,
		boolean					main_tracker,
		Map<String,Object>		properties )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( name, protocol, port, bind_ip, false, apply_ip_filter, main_tracker, true, properties ));
	}

	public static TRTrackerServer
	createSSL(
		String					name,
		int						protocol,
		int						port,
		InetAddress				bind_ip,
		boolean					apply_ip_filter,
		boolean					main_tracker,
		Map<String,Object>		properties )

		throws TRTrackerServerException
	{
		return( TRTrackerServerFactoryImpl.create( name, protocol, port, bind_ip, true, apply_ip_filter, main_tracker, true, properties ));
	}

	public static void
	addListener(
			TRTrackerServerFactoryListener	l )
	{
		TRTrackerServerFactoryImpl.addListener( l );
	}

	public static void
	removeListener(
		TRTrackerServerFactoryListener	l )
	{
		TRTrackerServerFactoryImpl.removeListener( l );
	}
}
