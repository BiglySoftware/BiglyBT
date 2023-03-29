/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.plugin.net.test;

import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.dht.transport.DHTTransportAlternativeContact;
import com.biglybt.core.dht.transport.DHTTransportAlternativeNetwork;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPUtils;
import com.biglybt.core.proxy.impl.AEPluginProxyHandler;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.*;
import com.biglybt.pif.messaging.MessageException;
import com.biglybt.pif.messaging.MessageManager;
import com.biglybt.pif.messaging.generic.*;
import com.biglybt.pif.messaging.generic.GenericMessageConnection.GenericMessageConnectionPropertyHandler;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;
import com.biglybt.pif.ui.config.StringParameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.utils.PooledByteBuffer;

public class 
NetTestPlugin
	implements Plugin
{
	private static final int MT_HANDSHAKE		= 1;
	private static final int MT_KEEP_ALIVE		= 2;
	
	private PluginInterface	pi;
	
	private GenericMessageRegistration msg_registration;
	
	private CopyOnWriteList<Connection>		connections = new CopyOnWriteList<>();
	
	@Override
	public void 
	initialize(
		PluginInterface _pi ) 
				
		throws PluginException
	{
		pi	= _pi;
		
		UIManager	ui_manager = pi.getUIManager();

		BasicPluginConfigModel config_model = ui_manager.createBasicPluginConfigModel( "plugins", "!Net Test!" );
		
		final StringParameter 	command_text_param = config_model.addStringParameter2( "azintnettest.cmd.text", "!Command!", "" );
		command_text_param.setMinimumRequiredUserMode( Parameter.MODE_ADVANCED );

		final ActionParameter	command_exec_param = config_model.addActionParameter2( "!!", "!Execute!" );
		command_exec_param.setMinimumRequiredUserMode( Parameter.MODE_ADVANCED );
	
		command_exec_param.addListener(
			new ParameterListener() 
			{
				@Override
				public void
				parameterChanged(
					Parameter param ) 
				{
					new AEThread2( "cmdrunner" )
					{
						@Override
						public void
						run()
						{
							exec( command_text_param.getValue());
						}
					}.start();
				}
			});

		
		
		pi.addListener(
			new PluginAdapter()
			{
				@Override
				public void 
				initializationComplete()
				{
					initialised();
				}
			});
	}
	
	private void
	initialised()
	{
		try{
			msg_registration =
				pi.getMessageManager().registerGenericMessageType(
					"NetOverlayTest",
					"Net Overlay Test Registration",
					MessageManager.STREAM_ENCRYPTION_NONE,
					new GenericMessageHandler()
					{
						@Override
						public boolean
						accept(
							GenericMessageConnection	gmc )

							throws MessageException
						{
							InetSocketAddress originator = gmc.getEndpoint().getNotionalAddress();
							
							if ( AENetworkClassifier.categoriseAddress( AddressUtils.getHostAddress( originator)) == AENetworkClassifier.AT_PUBLIC ){
								
								gmc.close();
								
								return( false );	
							}							
							
							Connection con = new Connection( gmc );
							
							connections.add( con );
							
							return( true );
						}
					});
			
			SimpleTimer.addPeriodicEvent(
				"NetTest",
				30*1000,
				(ev)->{		
					long now = SystemTime.getMonotonousTime();
					
					for ( Connection con: connections ){
							
						con.timerTick( now );
					}
				});
			
		}catch( Throwable e ){
	
			Debug.out( e );
		}
	}
	
	private void
	exec(
		String	cmd )
	{
		try{
			DHTTransportAlternativeNetwork net = DHTUDPUtils.getAlternativeNetwork( DHTTransportAlternativeNetwork.AT_TOR );
			
			if ( net == null ){
				
				return;
			}
			
			List<DHTTransportAlternativeContact> contacts = DHTUDPUtils.getAlternativeContacts( DHTTransportAlternativeNetwork.AT_TOR, 16 );

			for ( DHTTransportAlternativeContact contact: contacts ){
			
				InetSocketAddress target = net.getNotionalAddress( contact );
				
				if ( target == null ){
					
					continue;
				}

				InetSocketAddress local = AEPluginProxyHandler.getLocalAddress( AddressUtils.getHostAddress(target), target.getPort());
				
				if ( local == null ){
					
					continue;
				}
				
				if ( !local.equals( target )){
					
					log( "Skipping " + target );
					
					continue;
				}
				
				new Connection( target );
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	private void
	log(
		String		str )
	{
		System.out.println( str );
	}
	
	private class
	Connection
		implements GenericMessageConnectionListener, GenericMessageConnectionPropertyHandler
	{
		private final GenericMessageConnection	gmc;
		
		private long	last_received_time	= SystemTime.getMonotonousTime();
		private long	last_sent_time		= 0;
		
		private boolean	connected;
		private boolean failed;
		
		private
		Connection(
			InetSocketAddress		target )
		
			throws Exception
		{
			GenericMessageEndpoint ep = msg_registration.createEndpoint( target );
			
			ep.addTCP( target );
			
			gmc = msg_registration.createConnection( ep );
							
			connections.add( this );

			gmc.addListener( this );
			
			try{
				gmc.connect( this );
				
			}catch( Throwable e ){
				
				failed( e );
			}
		}
		
		private
		Connection(
			GenericMessageConnection		_gmc )
		{
			log( "inbound: connected" );
			
			gmc = _gmc;
							
			connections.add( this );
			
			connected = true;
			
			gmc.addListener( this );
		}
				
		@Override
		public void 
		connected(
			GenericMessageConnection connection )
		{
			log( "outbound: connected" );
			
			connected = true;
			
			Map map = new HashMap<>();
			
			map.put( "t", MT_HANDSHAKE );
			
			send( map );
		}
		
		private void
		timerTick(
			long	now )
		{
			if ( !connected ){
				
				return;
			}
			
			if ( now - last_received_time > 60*1000 ){
				
				failed( new Exception( "timeout" ));
				
			}else if ( now - last_sent_time >= 30*1000 ){
				
				Map map = new HashMap<>();
				
				map.put( "t", MT_KEEP_ALIVE );
				
				send( map );
			}
		}
		
		private void
		send(
			Map		map )
		{
			last_sent_time	= SystemTime.getMonotonousTime();
			
			log( "send " + map );
			
			PooledByteBuffer buffer = null;
			
			try{
				buffer = pi.getUtilities().allocatePooledByteBuffer( BEncoder.encode(map));
				
				gmc.send( buffer );
				
				buffer = null;
				
			}catch( Throwable e ){
								
				if ( buffer != null ){
					
					buffer.returnToPool();
				}
				
				failed( e );
			}
		}
		
		@Override
		public void 
		receive(
			GenericMessageConnection	connection, 
			PooledByteBuffer 			message )
					
			throws MessageException
		{
			last_received_time = SystemTime.getMonotonousTime();
					
			try{
				Map map = BDecoder.decode( message.toByteArray());
					
				log( "received " + map );
				
			}catch( Throwable e ){
										
				failed( e );

			}finally{
				
				message.returnToPool();
			}
		}
		
		@Override
		public void 
		failed(
			GenericMessageConnection	connection, 
			Throwable					error ) 
					
			throws MessageException
		{
			failed( error );
		}
		
		private void
		failed(
			Throwable 	error )
		{
			synchronized( this ){
				
				if ( failed ){
					
					return;
				}
				
				failed = true;
			}
			
			log( "failed: " + Debug.getNestedExceptionMessage(error));
					
			try{
				gmc.close();
				
			}catch( Throwable e ){
			}
							
			connections.remove( this );
		}
		
		@Override
		public Object 
		getConnectionProperty(
			String property_name )
		{
			return( null );
		}
	}
}
