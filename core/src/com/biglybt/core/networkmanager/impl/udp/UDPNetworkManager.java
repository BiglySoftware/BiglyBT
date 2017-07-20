/*
 * Created on 22 Jun 2006
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

package com.biglybt.core.networkmanager.impl.udp;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.impl.ProtocolDecoderPHE;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.net.udp.uc.PRUDPPacket;

public class
UDPNetworkManager
{
	public static final boolean	MINIMISE_OVERHEADS	= true;

	public static final int MIN_INCOMING_INITIAL_PACKET_SIZE = ProtocolDecoderPHE.MIN_INCOMING_INITIAL_PACKET_SIZE;
	public static final int MAX_INCOMING_INITIAL_PACKET_SIZE = ProtocolDecoderPHE.getMaxIncomingInitialPacketSize(MINIMISE_OVERHEADS);

	private static final int MIN_MSS = 128;
	private static final int MAX_MSS = PRUDPPacket.MAX_PACKET_SIZE;

	private static int udp_mss_size;

	public static boolean UDP_INCOMING_ENABLED;
	public static boolean UDP_OUTGOING_ENABLED;

	static{
		COConfigurationManager.addAndFireParameterListener(
				"UDP.Listen.Port.Enable",
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String name )
					{
						UDP_INCOMING_ENABLED = UDP_OUTGOING_ENABLED = COConfigurationManager.getBooleanParameter( name );
					}
				});
	}

	public static int getUdpMssSize() {  return udp_mss_size;  }

	public static void
	refreshRates(
		int		min_rate )
	{
		udp_mss_size = COConfigurationManager.getIntParameter( "network.udp.mtu.size" ) - 40;

	    if( udp_mss_size > min_rate )  udp_mss_size = min_rate - 1;

	    if( udp_mss_size < MIN_MSS )  udp_mss_size = MIN_MSS;

	    if ( udp_mss_size > MAX_MSS ) udp_mss_size = MAX_MSS;
	}

	private static UDPNetworkManager	singleton;


	public static UDPNetworkManager
	getSingleton()
	{
		synchronized( UDPNetworkManager.class ){

			if ( singleton == null ){

				singleton = new UDPNetworkManager();
			}
		}

		return( singleton );
	}

	int udp_listen_port	= -1;
	int udp_non_data_listen_port = -1;

	private UDPConnectionManager	_connection_manager;

	protected
	UDPNetworkManager()
	{
		COConfigurationManager.addAndFireParameterListener(
			   "UDP.Listen.Port",
			   new ParameterListener()
			   {
				   @Override
				   public void
				   parameterChanged(String name)
				   {
					   int port = COConfigurationManager.getIntParameter( name );

					   if ( port == udp_listen_port ){

						   return;
					   }

					   if ( port < 0 || port > 65535 || port == Constants.INSTANCE_PORT ) {

					        String msg = "Invalid incoming UDP listen port configured, " +port+ ". The port has been reset. Please check your config!";

					        Debug.out( msg );

					        Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg));

					        udp_listen_port = RandomUtils.generateRandomNetworkListenPort();

					        COConfigurationManager.setParameter( name, udp_listen_port );

					    }else{

					    	udp_listen_port	= port;
					    }
				   }
			   });

		COConfigurationManager.addAndFireParameterListener(
				   "UDP.NonData.Listen.Port",
				   new ParameterListener()
				   {
					   @Override
					   public void
					   parameterChanged(String name)
					   {
						   int port = COConfigurationManager.getIntParameter( name );

						   if ( port == udp_non_data_listen_port ){

							   return;
						   }

						   if ( port < 0 || port > 65535 || port == Constants.INSTANCE_PORT ) {

						        String msg = "Invalid incoming UDP non-data listen port configured, " +port+ ". The port has been reset. Please check your config!";

						        Debug.out( msg );

						        Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg));

						        udp_non_data_listen_port = RandomUtils.generateRandomNetworkListenPort();

						        COConfigurationManager.setParameter( name, udp_non_data_listen_port );

						    }else{

						    	udp_non_data_listen_port	= port;
						    }
					   }
				   });
	}

	public boolean
	isUDPListenerEnabled()
	{
		return( UDP_INCOMING_ENABLED );
	}

	public int
	getUDPListeningPortNumber()
	{
		return( udp_listen_port );
	}

	public boolean
	isUDPNonDataListenerEnabled()
	{
		return( UDP_INCOMING_ENABLED );
	}

	public int
	getUDPNonDataListeningPortNumber()
	{
		return( udp_non_data_listen_port );
	}

	public UDPConnectionManager
	getConnectionManager()
	{
		synchronized( this ){

			if ( _connection_manager == null ){

				_connection_manager = new UDPConnectionManager();
			}
		}

		return( _connection_manager );
	}
}
