/*
 * Created on Jan 18, 2005
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

package com.biglybt.core.networkmanager.impl.tcp;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.*;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.biglybt.core.networkmanager.impl.IncomingConnectionManager;
import com.biglybt.core.networkmanager.impl.ProtocolDecoder;
import com.biglybt.core.networkmanager.impl.TransportCryptoManager;
import com.biglybt.core.networkmanager.impl.TransportHelperFilter;
import com.biglybt.core.proxy.AEProxyAddressMapper;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.*;


/**
 * Accepts new incoming socket connections and manages routing of them
 * to registered handlers.
 */
public class IncomingSocketChannelManager
{
  static final LogIDs LOGID = LogIDs.NWMAN;

  final String	port_config_key;
  private final String	port_enable_config_key;

  int tcp_listen_port;

  int so_rcvbuf_size = COConfigurationManager.getIntParameter( "network.tcp.socket.SO_RCVBUF" );

  InetAddress[] 	default_bind_addresses = NetworkAdmin.getSingleton().getMultiHomedServiceBindAddresses(true);
  private InetAddress 	explicit_bind_address;
  private boolean		explicit_bind_address_set;

  VirtualServerChannelSelector[] serverSelectors = new VirtualServerChannelSelector[0];
  int listenFailCounts[] = new int[0];

  final IncomingConnectionManager	incoming_manager = IncomingConnectionManager.getSingleton();

  protected final AEMonitor	this_mon	= new AEMonitor( "IncomingSocketChannelManager" );

  long	last_non_local_connection_time;

  private final AEProxyAddressMapper proxy_address_mapper = AEProxyFactory.getAddressMapper();

  /**
   * Create manager and begin accepting and routing new connections.
   */
  public IncomingSocketChannelManager( String _port_config_key, String _port_enable_config_key ) {

	port_config_key 		= _port_config_key;
	port_enable_config_key	= _port_enable_config_key;

	tcp_listen_port = COConfigurationManager.getIntParameter( port_config_key );

    //allow dynamic port number changes
    COConfigurationManager.addParameterListener( port_config_key, new ParameterListener() {
      @Override
      public void parameterChanged(String parameterName) {
        int port = COConfigurationManager.getIntParameter( port_config_key );
        if( port != tcp_listen_port ) {
        	tcp_listen_port = port;
        	restartProcessing();
        }
      }
    });

    COConfigurationManager.addParameterListener( port_enable_config_key, new ParameterListener() {
        @Override
        public void parameterChanged(String parameterName) {
        	restartProcessing();
        }
      });

    //allow dynamic receive buffer size changes
    COConfigurationManager.addParameterListener( "network.tcp.socket.SO_RCVBUF", new ParameterListener() {
      @Override
      public void parameterChanged(String parameterName) {
        int size = COConfigurationManager.getIntParameter( "network.tcp.socket.SO_RCVBUF" );
        if( size != so_rcvbuf_size ) {
          so_rcvbuf_size = size;
          restartProcessing();
        }
      }
    });

    //allow dynamic bind address changes

    NetworkAdmin.getSingleton().addPropertyChangeListener(
    	new NetworkAdminPropertyChangeListener()
    	{
    		@Override
		    public void
    		propertyChanged(
    			String		property )
    		{
    			if ( property == NetworkAdmin.PR_DEFAULT_BIND_ADDRESS ){

			        InetAddress[] addresses = NetworkAdmin.getSingleton().getMultiHomedServiceBindAddresses(true);

			        if ( !Arrays.equals(addresses, default_bind_addresses)) {

			        	default_bind_addresses = addresses;

			        	restartProcessing();
			        }
    			}
    		}
      });


    //start processing
    startProcessing();


    	//run a daemon thread to poll listen port for connectivity
    	//it seems that sometimes under OSX that listen server sockets sometimes stop accepting incoming connections for some unknown reason
    	//this checker tests to make sure the listen socket is still accepting connections, and if not, recreates the socket

    SimpleTimer.addPeriodicEvent("IncomingSocketChannelManager:concheck", 60 * 1000, new TimerEventPerformer()
		{
			@Override
			public void
			perform(
				TimerEvent ev )
			{
				COConfigurationManager.setParameter( "network.tcp.port." + tcp_listen_port + ".last.nonlocal.incoming", last_non_local_connection_time );

				for (int i = 0; i < serverSelectors.length; i++)
				{
					VirtualServerChannelSelector server_selector = serverSelectors[i];

					if (server_selector != null && server_selector.isRunning())
					{ //ensure it's actually running
						long accept_idle = SystemTime.getCurrentTime() - server_selector.getTimeOfLastAccept();
						if (accept_idle > 10 * 60 * 1000)
						{ //the socket server hasn't accepted any new connections in the last 10min
							//so manually test the listen port for connectivity
							InetAddress inet_address = server_selector.getBoundToAddress();
							try
							{
								if (inet_address == null)
									inet_address = InetAddress.getByName("127.0.0.1"); //failback
								Socket sock = new Socket(inet_address, tcp_listen_port, inet_address, 0);
								sock.close();
								listenFailCounts[i] = 0;
							} catch (Throwable t)
							{
								//ok, let's try again without the explicit local bind
								try
								{
									Socket sock = new Socket(InetAddress.getByName("127.0.0.1"), tcp_listen_port);
									sock.close();
									listenFailCounts[i] = 0;
								} catch (Throwable x)
								{
									listenFailCounts[i]++;
									Debug.out(new Date() + ": listen port on [" + inet_address + ": " + tcp_listen_port + "] seems CLOSED [" + listenFailCounts[i] + "x]");
									if (listenFailCounts[i] > 4)
									{
										String error = t.getMessage() == null ? "<null>" : t.getMessage();
										String msg = "Listen server socket on [" + inet_address + ": " + tcp_listen_port + "] does not appear to be accepting inbound connections.\n[" + error + "]\nAuto-repairing listen service....\n";
										Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING, msg));
										restartProcessing();
										listenFailCounts[i] = 0;
									}
								}
							}
						} else
						{ //it's recently accepted an inbound connection
							listenFailCounts[i] = 0;
						}
					}
				}
			}
		});
	}

  public boolean
  isEnabled()
  {
	  return( COConfigurationManager.getBooleanParameter(port_enable_config_key ));
  }

  /**
	 * Get port that the TCP server socket is listening for incoming connections
	 * on.
	 *
	 * @return port number
	 */
  
  public int 
  getTCPListeningPortNumber() 
  {  
	  return tcp_listen_port;  
  }

  public void
  setExplicitBindAddress(
	InetAddress	address )
  {
	  explicit_bind_address 	= address;
	  explicit_bind_address_set	= true;

	  restartProcessing();
  }

  public void
  clearExplicitBindAddress()
  {
	  explicit_bind_address		= null;
	  explicit_bind_address_set	= false;

	  restartProcessing();
  }

  protected InetAddress[]
  getEffectiveBindAddresses()
  {
	  if ( explicit_bind_address_set ){

		  return( new InetAddress[] {explicit_bind_address});

	  }else{

		  return( default_bind_addresses );
      }
  }

  public boolean
  isEffectiveBindAddress(
	InetAddress	address )
  {
	  InetAddress[]	effective = getEffectiveBindAddresses();

	  return Arrays.asList(effective).contains(address);
  }


  private final class
  TcpSelectListener implements
  VirtualServerChannelSelector.SelectListener
  {
	  @Override
	  public void newConnectionAccepted(final ServerSocketChannel server, final SocketChannel channel ) {

		  InetAddress remote_ia = channel.socket().getInetAddress();

		  if ( !( remote_ia.isLoopbackAddress() || remote_ia.isLinkLocalAddress() || remote_ia.isSiteLocalAddress())){

			  last_non_local_connection_time = SystemTime.getCurrentTime();
		  }

		  //check for encrypted transport
		  final TCPTransportHelper	helper = new TCPTransportHelper( channel );

		  TransportCryptoManager.getSingleton().manageCrypto( helper, null, true, null, new TransportCryptoManager.HandshakeListener() {
			  @Override
			  public void handshakeSuccess(ProtocolDecoder decoder, ByteBuffer remaining_initial_data ) {
				  process( server.socket().getLocalPort(), decoder.getFilter());
			  }

			  @Override
			  public void
			  handshakeFailure(
					  Throwable failure_msg )
			  {

				  if (Logger.isEnabled()) 	Logger.log(new LogEvent(LOGID, "incoming crypto handshake failure: " + Debug.getNestedExceptionMessage( failure_msg )));

				  /*
          		// we can have problems with sockets stuck in a TIME_WAIT state if we just
          		// close an incoming channel - to clear things down properly the client needs
          		// to initiate the close. So what we do is send some random bytes to the client
          		// under the assumption this will cause them to close, and we delay our socket close
          		// for 10 seconds to give them a chance to do so.
          		try{
          			Random	random = new Random();

          			byte[]	random_bytes = new byte[68+random.nextInt(128-68)];

          			random.nextBytes( random_bytes );

          			channel.write( ByteBuffer.wrap( random_bytes ));

          		}catch( Throwable e ){
          			// ignore anything here
          		}
          		NetworkManager.getSingleton().closeSocketChannel( channel, 10*1000 );
				   */

				  helper.close( "Handshake failure: " + Debug.getNestedExceptionMessage( failure_msg ));
			  }

			  @Override
			  public void
			  gotSecret(
					  byte[]				session_secret )
			  {
			  }

			  @Override
			  public int
			  getMaximumPlainHeaderLength()
			  {
				  return( incoming_manager.getMaxMinMatchBufferSize());
			  }

			  @Override
			  public int
			  matchPlainHeader(
					  ByteBuffer			buffer )
			  {
				  Object[]	match_data = incoming_manager.checkForMatch( helper, server.socket().getLocalPort(), buffer, true );

				  if ( match_data == null ){

					  return( TransportCryptoManager.HandshakeListener.MATCH_NONE );

				  }else{

					  IncomingConnectionManager.MatchListener match = (IncomingConnectionManager.MatchListener)match_data[0];

					  if ( match.autoCryptoFallback()){

						  return( TransportCryptoManager.HandshakeListener.MATCH_CRYPTO_AUTO_FALLBACK );

					  }else{

						  return( TransportCryptoManager.HandshakeListener.MATCH_CRYPTO_NO_AUTO_FALLBACK );

					  }
				  }
			  }
		  });
	  }
  }


  private final VirtualServerChannelSelector.SelectListener selectListener = new TcpSelectListener();


  private void 
  startProcessing()
  {  
		try
		{
			this_mon.enter();

			if (tcp_listen_port < 0 || tcp_listen_port > 65535 || tcp_listen_port == Constants.INSTANCE_PORT )
			{
				String msg = "Invalid incoming TCP listen port configured, " + tcp_listen_port + ". Port reset to default. Please check your config!";
				Debug.out(msg);
				Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg));
				tcp_listen_port = RandomUtils.generateRandomNetworkListenPort();
				COConfigurationManager.setParameter(port_config_key, tcp_listen_port);
			}

			if (COConfigurationManager.getBooleanParameter(port_enable_config_key))
			{
				last_non_local_connection_time = COConfigurationManager.getLongParameter( "network.tcp.port." + tcp_listen_port + ".last.nonlocal.incoming", 0 );

				if ( last_non_local_connection_time > SystemTime.getCurrentTime()){

					last_non_local_connection_time = SystemTime.getCurrentTime();
				}

				if (serverSelectors.length == 0)
				{
					InetSocketAddress address;
					InetAddress[] bindAddresses = getEffectiveBindAddresses();

					List tempSelectors = new ArrayList(bindAddresses.length);


					listenFailCounts = new int[bindAddresses.length];
					for (int i = 0; i < bindAddresses.length; i++)
					{
						InetAddress bindAddress = bindAddresses[i];

						if(!NetworkAdmin.getSingleton().hasIPV6Potential(true) && bindAddress instanceof Inet6Address)
							continue;

						if (bindAddress != null)
							address = new InetSocketAddress(bindAddress, tcp_listen_port);
						else
							address = new InetSocketAddress(tcp_listen_port);

						VirtualServerChannelSelector serverSelector;

						if(bindAddresses.length == 1)
							serverSelector = VirtualServerChannelSelectorFactory.createBlocking(address, so_rcvbuf_size, selectListener);
						else
							serverSelector = VirtualServerChannelSelectorFactory.createNonBlocking(address, so_rcvbuf_size, selectListener);
						serverSelector.startProcessing();

						tempSelectors.add(serverSelector);
					}

					if(tempSelectors.size() == 0)
						Logger.log(new LogAlert(true,LogAlert.AT_WARNING,MessageText.getString("network.bindError")));

					serverSelectors = (VirtualServerChannelSelector[])tempSelectors.toArray(new VirtualServerChannelSelector[tempSelectors.size()]);
				}
			} else
			{
				Logger.log(new LogEvent(LOGID, "Not starting TCP listener on port " + tcp_listen_port + " as protocol disabled"));
			}
		} finally
		{
			this_mon.exit();
		}
	}


  protected void
  process(
	int						local_port,
	TransportHelperFilter 	filter )

  {

    SocketChannel	channel = ((TCPTransportHelper)filter.getHelper()).getSocketChannel();

    Socket socket = channel.socket();

    //set advanced socket options
    try {
      int so_sndbuf_size = COConfigurationManager.getIntParameter( "network.tcp.socket.SO_SNDBUF" );
      if( so_sndbuf_size > 0 )  socket.setSendBufferSize( so_sndbuf_size );

      String ip_tos = COConfigurationManager.getStringParameter( "network.tcp.socket.IPDiffServ" );
      if( ip_tos.length() > 0 )  socket.setTrafficClass( Integer.decode( ip_tos ).intValue() );
    }
    catch( Throwable t ) {
      t.printStackTrace();
    }

    AEProxyAddressMapper.AppliedPortMapping applied_mapping = proxy_address_mapper.applyPortMapping( socket.getInetAddress(), socket.getPort());

    InetSocketAddress	tcp_address = applied_mapping.getRemoteAddress();

	ConnectionEndpoint	co_ep = new ConnectionEndpoint(tcp_address);

	Map<String,Object>	properties = applied_mapping.getProperties();

	if ( properties != null ){

		co_ep.addProperties( properties );
	}

	ProtocolEndpointTCP	pe_tcp = (ProtocolEndpointTCP)ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_TCP, co_ep, tcp_address );

	Transport transport = new TCPTransportImpl( pe_tcp, filter );

    incoming_manager.addConnection( local_port, filter, transport );
  }


  protected long
  getLastNonLocalConnectionTime()
  {
	  return( last_non_local_connection_time );
  }

  void restartProcessing() {
	  // Usually called because of config change (often from UI thread)
	  new AEThread2("Restart ISCM", true) {
		  @Override
		  public void run() {
			  try{
				  this_mon.enter();

				  for(int i=0;i<serverSelectors.length;i++)
					  serverSelectors[i].stopProcessing();
				  serverSelectors = new VirtualServerChannelSelector[0];
			  }finally{

				  this_mon.exit();
			  }

			  try{ Thread.sleep( 1000 );  }catch( Throwable t ) { t.printStackTrace();  }

			  startProcessing();
		  }
	  }.start();
  }



}
