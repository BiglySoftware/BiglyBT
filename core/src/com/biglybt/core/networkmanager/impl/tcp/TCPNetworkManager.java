/*
 * Created on 21 Jun 2006
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

package com.biglybt.core.networkmanager.impl.tcp;


import java.nio.channels.CancelledKeyException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

public class
TCPNetworkManager
{
	static int WRITE_SELECT_LOOP_TIME 		= 25;
	static int WRITE_SELECT_MIN_LOOP_TIME 	= 0;
	static int READ_SELECT_LOOP_TIME		= 25;
	static int READ_SELECT_MIN_LOOP_TIME	= 0;

	protected static int tcp_mss_size;

	private static final TCPNetworkManager instance = new TCPNetworkManager();

	public static TCPNetworkManager getSingleton(){ return( instance ); }

	public static boolean TCP_INCOMING_ENABLED;
	public static boolean TCP_OUTGOING_ENABLED;

	static{
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"TCP.Listen.Port.Enable",
					"network.tcp.connect.outbound.enable"
				},
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String name )
					{
						TCP_INCOMING_ENABLED = TCP_OUTGOING_ENABLED = COConfigurationManager.getBooleanParameter( "TCP.Listen.Port.Enable" );

						if ( TCP_OUTGOING_ENABLED ){

							TCP_OUTGOING_ENABLED = COConfigurationManager.getBooleanParameter( "network.tcp.connect.outbound.enable" );
						}
					}
				});

		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"network.tcp.read.select.time",
					"network.tcp.read.select.min.time",
					"network.tcp.write.select.time",
					"network.tcp.write.select.min.time",
					},
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String name )
					{
						WRITE_SELECT_LOOP_TIME 		= COConfigurationManager.getIntParameter(  "network.tcp.write.select.time" );
						WRITE_SELECT_MIN_LOOP_TIME 	= COConfigurationManager.getIntParameter(  "network.tcp.write.select.min.time" );

						READ_SELECT_LOOP_TIME 		= COConfigurationManager.getIntParameter(  "network.tcp.read.select.time" );
						READ_SELECT_MIN_LOOP_TIME 	= COConfigurationManager.getIntParameter(  "network.tcp.read.select.min.time" );
					}
				});
	}

	 /**
	   * Get the configured TCP MSS (Maximum Segment Size) unit, i.e. the max (preferred) packet payload size.
	   * NOTE: MSS is MTU-40bytes for TCPIP headers, usually 1460 (1500-40) for standard ethernet
	   * connections, or 1452 (1492-40) for PPPOE connections.
	   * @return mss size in bytes
	   */

	public static int getTcpMssSize() {  return tcp_mss_size;  }

	public static void
	refreshRates(
		int		min_rate )
	{
		 tcp_mss_size = COConfigurationManager.getIntParameter( "network.tcp.mtu.size" ) - 40;

	    if( tcp_mss_size > min_rate )  tcp_mss_size = min_rate - 1;

	    if( tcp_mss_size < 512 )  tcp_mss_size = 512;
	}

	final VirtualChannelSelector read_selector 	=
			new VirtualChannelSelector( "TCP network manager reader", VirtualChannelSelector.OP_READ, true );
	final VirtualChannelSelector write_selector =
			new VirtualChannelSelector( "TCP network manager writer", VirtualChannelSelector.OP_WRITE, true );

	private final TCPConnectionManager connect_disconnect_manager = new TCPConnectionManager();

	private final IncomingSocketChannelManager default_incoming_socketchannel_manager = 
			new IncomingSocketChannelManager( "TCP.Listen.Port", "TCP.Listen.Port.Enable" );

	{
		COConfigurationManager.setParameter( "TCP.Listen.AdditionalPorts", new ArrayList<>());
	}
	
	private List<IncomingSocketChannelManager>	additional_incoming_socketchannel_managers = new ArrayList<>();
	
	long	read_select_count;
	long	write_select_count;


	protected
	TCPNetworkManager()
	{
		Set	types = new HashSet();

		types.add( CoreStats.ST_NET_TCP_SELECT_READ_COUNT );
		types.add( CoreStats.ST_NET_TCP_SELECT_WRITE_COUNT );

		CoreStats.registerProvider(
			types,
			new CoreStatsProvider()
			{
				@Override
				public void
				updateStats(
					Set		types,
					Map		values )
				{
					if ( types.contains( CoreStats.ST_NET_TCP_SELECT_READ_COUNT )){

						values.put( CoreStats.ST_NET_TCP_SELECT_READ_COUNT, new Long( read_select_count ));
					}
					if ( types.contains( CoreStats.ST_NET_TCP_SELECT_WRITE_COUNT )){

						values.put( CoreStats.ST_NET_TCP_SELECT_WRITE_COUNT, new Long( write_select_count ));
					}
				}
			});

		   //start read selector processing

		AEThread2 read_selector_thread =
	    	new AEThread2( "ReadController:ReadSelector", true )
	    	{
		    	@Override
			    public void
		    	run()
		    	{
		    		while( true ) {

		    			try{
		    				if ( READ_SELECT_MIN_LOOP_TIME > 0 ){

		    					long	start = SystemTime.getHighPrecisionCounter();

		    					read_selector.select( READ_SELECT_LOOP_TIME );

		    					long duration = SystemTime.getHighPrecisionCounter() - start;

		    					duration = duration/1000000;

		    					long	sleep = READ_SELECT_MIN_LOOP_TIME - duration;

		    					if ( sleep > 0 ){

		    						try{
		    							Thread.sleep( sleep );

		    						}catch( Throwable e ){
		    						}
		    					}
		    				}else{

			    				read_selector.select( READ_SELECT_LOOP_TIME );
		    				}

			    			read_select_count++;

		    			}catch( Throwable t ) {

		    					// filter out the boring ones

		    				if (!( t instanceof CancelledKeyException )){

		    					Debug.out( "readSelectorLoop() EXCEPTION: ", t );
		    				}
		    			}
		    		}
		    	}
	    	};

	    read_selector_thread.setPriority( Thread.MAX_PRIORITY - 2 );
	    read_selector_thread.start();

	    	//start write selector processing

	    AEThread2 write_selector_thread =
	    	new AEThread2( "WriteController:WriteSelector", true )
	    	{
		    	@Override
			    public void
		    	run()
		    	{
		    	    while( true ){

		    	    	try{
		    	    		if ( WRITE_SELECT_MIN_LOOP_TIME > 0 ){

		    					long	start = SystemTime.getHighPrecisionCounter();

		    					write_selector.select( WRITE_SELECT_LOOP_TIME );

		    					long duration = SystemTime.getHighPrecisionCounter() - start;

		    					duration = duration/1000000;

		    					long	sleep = WRITE_SELECT_MIN_LOOP_TIME - duration;

		    					if ( sleep > 0 ){

		    						try{
		    							Thread.sleep( sleep );

		    						}catch( Throwable e ){
		    						}
		    					}
		    	    		}else{

		    	    			write_selector.select( WRITE_SELECT_LOOP_TIME );

		    	    			write_select_count++;
		    	    		}
		    	    	}catch( Throwable t ) {

		    	    		Debug.out( "writeSelectorLoop() EXCEPTION: ", t );
		    	    	}
		  		    }
		    	}
	    	};

	    write_selector_thread.setPriority( Thread.MAX_PRIORITY - 2 );
	    write_selector_thread.start();
	}

	public IncomingSocketChannelManager
	getDefaultIncomingSocketManager()
	{
		return( default_incoming_socketchannel_manager );
	}

		/**
		 * Get the socket channel connect / disconnect manager.
		 * @return connect manager
		 */

	public TCPConnectionManager
	getConnectDisconnectManager()
	{
		return connect_disconnect_manager;
	}

	/**
	 * Get the virtual selector used for socket channel read readiness.
	 * @return read readiness selector
	 */
	public VirtualChannelSelector getReadSelector() {  return read_selector;  }


	/**
	 * Get the virtual selector used for socket channel write readiness.
	 * @return write readiness selector
	 */
	public VirtualChannelSelector getWriteSelector() {  return write_selector;  }


	public boolean
	isDefaultTCPListenerEnabled()
	{
		return( default_incoming_socketchannel_manager.isEnabled());
	}

	/**
	 * Get port that the TCP server socket is listening for incoming connections on.
	 * @return port number
	 */

	public int
	getDefaultTCPListeningPortNumber()
	{
		return( default_incoming_socketchannel_manager.getTCPListeningPortNumber());
	}

	public long
	getLastIncomingNonLocalConnectionTime()
	{
		return( default_incoming_socketchannel_manager.getLastNonLocalConnectionTime());
	}
	
	public int
	getAdditionalTCPListeningPortNumber(
		List<Integer>		excluded_ports )
	{
		synchronized( additional_incoming_socketchannel_managers ){
			
			for ( IncomingSocketChannelManager x: additional_incoming_socketchannel_managers ){
				
				int port = x.getTCPListeningPortNumber();
				
				if ( !excluded_ports.contains( port )){
					
					return( port );
				}
			}
			
			try{
				int new_port = NetworkAdmin.getSingleton().getBindablePort( 0 );
				
				int	new_id = additional_incoming_socketchannel_managers.size() + 1;
				
				String key = "TCP.Listen.AdditionalPort." + new_id;
				
				COConfigurationManager.setParameter( key, new_port );
				
				IncomingSocketChannelManager manager = new IncomingSocketChannelManager( key, "TCP.Listen.Port.Enable" );
				
				additional_incoming_socketchannel_managers.add( manager );
				
				List<Long> port_list = (List<Long>)COConfigurationManager.getListParameter( "TCP.Listen.AdditionalPorts", new ArrayList<>());
				
				port_list.add( new Long( new_port ));
				
				COConfigurationManager.setParameter( "TCP.Listen.AdditionalPorts", port_list );
				
				return( new_port );
				
			}catch( Throwable e ){
				
				Debug.out( e );
				
				return( 0 );
			}
		}
	}
}
