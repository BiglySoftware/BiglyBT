/*
 * Created on May 8, 2004
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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.networkmanager.TransportStartpoint;
import com.biglybt.core.networkmanager.impl.*;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.Debug;



/**
 * Represents a peer TCP transport connection (eg. a network socket).
 */
public class TCPTransportImpl extends TransportImpl implements Transport {
	static final LogIDs LOGID = LogIDs.NET;

  private final  ProtocolEndpointTCP		protocol_endpoint;



  TCPConnectionManager.ConnectListener connect_request_key = null;
  String description = "<disconnected>";
  final boolean is_inbound_connection;

  private int transport_mode = TRANSPORT_MODE_NORMAL;

  public volatile boolean has_been_closed = false;

  boolean 	connect_with_crypto;
  private byte[][]	shared_secrets;
  int		fallback_count;
  final boolean fallback_allowed;

  boolean				is_socks;
  volatile PluginProxy	plugin_proxy;

  /**
   * Constructor for disconnected (outbound) transport.
   */
  public
  TCPTransportImpl(
	ProtocolEndpointTCP endpoint,
	boolean use_crypto,
	boolean allow_fallback,
	byte[][] _shared_secrets )
  {
	protocol_endpoint = endpoint;
    is_inbound_connection = false;
    connect_with_crypto = use_crypto;
    shared_secrets		= _shared_secrets;
    fallback_allowed  = allow_fallback;
  }


  /**
   * Constructor for connected (inbound) transport.
   * @param channel connection
   * @param already_read bytes from the channel
   */

  public
  TCPTransportImpl(
	ProtocolEndpointTCP 	endpoint,
	TransportHelperFilter	filter )
  {
	protocol_endpoint = endpoint;

	setFilter( filter );

    is_inbound_connection = true;
    connect_with_crypto = false;  //inbound connections will automatically be using crypto if necessary
    fallback_allowed = false;

    InetSocketAddress address = endpoint.getAddress();

    description = ( is_inbound_connection ? "R" : "L" ) + ": " + AddressUtils.getHostNameNoResolve( address ) + ": " + address.getPort();

  }
  
  public boolean
  isIncoming()
  {
	  return( is_inbound_connection );
  }

  /**
   * Get the socket channel used by the transport.
   * @return the socket channel
   */
  public SocketChannel getSocketChannel() {
  	TransportHelperFilter filter = getFilter();
  	if (filter == null) {
  		return null;
  	}

  	TCPTransportHelper helper = (TCPTransportHelper)filter.getHelper();
  	if (helper == null) {
  		return null;
  	}

  	return helper.getSocketChannel();
  }

  @Override
  public TransportEndpointTCP
  getTransportEndpoint()
  {
	  return( new TransportEndpointTCP( protocol_endpoint, getSocketChannel()));
  }

  @Override
  public TransportStartpoint
  getTransportStartpoint()
  {
	  return( new TransportStartpointTCP( this, getTransportEndpoint()));
  }

  @Override
  public int
  getMssSize()
  {
	  return( TCPNetworkManager.getTcpMssSize());
  }

  @Override
  public boolean
  isTCP()
  {
	  return( true );
  }

  @Override
  public boolean
  isSOCKS()
  {
	  return( is_socks );
  }

	@Override
	public PluginProxy 
	getPluginProxy()
	{
		return( plugin_proxy );
	}

  
  @Override
  public String
  getProtocol()
  {
	  if ( is_socks ){

		  return( "TCP (SOCKS)" );

	  }else{

		  return "TCP";
	  }
  }

  /**
   * Get a textual description for this transport.
   * @return description
   */
  @Override
  public String getDescription() {  return description;  }



  /**
   * Request the transport connection be established.
   * NOTE: Will automatically connect via configured proxy if necessary.
   * @param address remote peer address to connect to
   * @param listener establishment failure/success listener
   */
  @Override
  public void connectOutbound(final ByteBuffer initial_data, final ConnectListener listener, final int priority ) {

	if ( !TCPNetworkManager.TCP_OUTGOING_ENABLED ){

		listener.connectFailure( this, new Throwable( "Outbound TCP connections disabled" ));

		return;
	}

    if( has_been_closed ){

		listener.connectFailure( this, new Throwable( "Connection already closed" ));

    	return;
    }

    if( getFilter() != null ) {  //already connected
      Debug.out( "socket_channel != null" );
      listener.connectSuccess( this, initial_data );
      return;
    }

    final InetSocketAddress	address = protocol_endpoint.getAddress();

    if ( !ProxyLoginHandler.isDefaultProxy( address )){

    			// see if a plugin can handle this connection

		if ( address.isUnresolved()){

			String host = address.getHostName();

			if ( AENetworkClassifier.categoriseAddress( host ) != AENetworkClassifier.AT_PUBLIC ){

				Map<String,Object>	opts = new HashMap<>();

				Object peer_nets = listener.getConnectionProperty( AEProxyFactory.PO_PEER_NETWORKS );

				if ( peer_nets != null ){

					opts.put( AEProxyFactory.PO_PEER_NETWORKS, peer_nets );
				}

				PluginProxy pp = plugin_proxy;

				plugin_proxy = null;

				if ( pp != null ){

						// most likely crypto fallback connection so don't assume it is a bad
						// outcome

					pp.setOK( true );
				}

				plugin_proxy = AEProxyFactory.getPluginProxy( "outbound connection", host, address.getPort(), opts );

			}
		}

		if ( plugin_proxy == null ){

		   	is_socks = COConfigurationManager.getBooleanParameter( "Proxy.Data.Enable" );
		}
    }

    
    InetSocketAddress to_connect;

    PluginProxy pp = plugin_proxy;

    if ( is_socks ){

    	to_connect = ProxyLoginHandler.getProxyAddress( address );

    }else if ( pp != null ){

    	to_connect = (InetSocketAddress)pp.getProxy().address();

    }else{

    	to_connect = address;
    }
    
    final TCPTransportImpl transport_instance = this;


    TCPConnectionManager.ConnectListener connect_listener = new TCPConnectionManager.ConnectListener() {
      @Override
      public int connectAttemptStarted(
    		  int default_connect_timeout ) {
        return( listener.connectAttemptStarted( transport_instance, default_connect_timeout ));
      }

      @Override
      public void connectSuccess(final SocketChannel channel ) {
      	if( channel == null ) {
      		String msg = "connectSuccess:: given channel == null";
      		Debug.out( msg );
      		setConnectResult( false );
      		listener.connectFailure( transport_instance, new Exception( msg ) );
      		return;
      	}

        if( has_been_closed ) {  //closed between select ops
        	TCPNetworkManager.getSingleton().getConnectDisconnectManager().closeConnection( channel );  //just close it

        	setConnectResult( false );

  			listener.connectFailure( transport_instance, new Throwable( "Connection has been closed" ));

          return;
        }

        connect_request_key = null;
        description = ( is_inbound_connection ? "R" : "L" ) + ": " + channel.socket().getInetAddress().getHostAddress() + ": " + channel.socket().getPort();

        PluginProxy pp = plugin_proxy;

        if ( is_socks ){  //proxy server connection established, login
        	if (Logger.isEnabled())
        		Logger.log(new LogEvent(LOGID,"Socket connection established to proxy server [" +description+ "], login initiated..."));

        	// set up a transparent filter for socks negotiation

        	setFilter( TCPTransportHelperFilterFactory.createTransparentFilter( channel ));

        	new ProxyLoginHandler( transport_instance, address, new ProxyLoginHandler.ProxyListener() {
        		@Override
		        public void connectSuccess() {
        			if (Logger.isEnabled())
        				Logger.log(new LogEvent(LOGID, "Proxy [" +description+ "] login successful." ));
        			handleCrypto( address, channel, initial_data, priority, listener );
        		}

        		@Override
		        public void connectFailure(Throwable failure_msg ) {
        			close( "Proxy login failed" );
        			listener.connectFailure( transport_instance, failure_msg );
        		}
        	}, to_connect );
        }else if ( pp != null ){

           	if (Logger.isEnabled()){
        		Logger.log(new LogEvent(LOGID,"Socket connection established via plugin proxy [" +description+ "], login initiated..."));
           	}

           		// set up a transparent filter for socks negotiation

        	setFilter( TCPTransportHelperFilterFactory.createTransparentFilter( channel ));

        	String pp_host = pp.getHost();

        	InetSocketAddress ia_address;

        	if ( AENetworkClassifier.categoriseAddress( pp_host ) == AENetworkClassifier.AT_PUBLIC ){

        		ia_address = new InetSocketAddress( pp.getHost(), pp.getPort());

        	}else{

        		ia_address = InetSocketAddress.createUnresolved( pp_host, pp.getPort());
        	}

        	new ProxyLoginHandler(
        		transport_instance,
        		ia_address,
        		new ProxyLoginHandler.ProxyListener()
        		{
        			@Override
			        public void
        			connectSuccess()
        			{
	        			if (Logger.isEnabled()){
	        				Logger.log(new LogEvent(LOGID, "Proxy [" +description+ "] login successful." ));
	        			}
	        			setConnectResult( true );

	        			handleCrypto( address, channel, initial_data, priority, listener );
        			}

        			@Override
			        public void
        			connectFailure(
        				Throwable failure_msg )
        			{
        				setConnectResult( false );

        				close( "Proxy login failed" );

        				listener.connectFailure( transport_instance, failure_msg );
        			}
        		});

        }else {  //direct connection established, notify
        	handleCrypto( address, channel, initial_data, priority, listener );
        }
      }

      @Override
      public void connectFailure(Throwable failure_msg ) {
        connect_request_key = null;
        
        if ( is_socks ){
        	
        	ProxyLoginHandler.proxyFailed( to_connect, failure_msg );
        }
        
        setConnectResult( false );
        listener.connectFailure( transport_instance, failure_msg );
      }
      
		@Override
		public Object 
		getConnectionProperty(
			String property_name)
		{
			return( listener.getConnectionProperty(property_name));
		}
    };

    connect_request_key = connect_listener;

    TCPNetworkManager.getSingleton().getConnectDisconnectManager().requestNewConnection( to_connect, pp, connect_listener, priority );
  }




  protected void
  handleCrypto(
	final InetSocketAddress 	address,
	final SocketChannel 		channel,
	final ByteBuffer 			initial_data,
	final int	 				priority,
	final ConnectListener 		listener )
  {
  	if( connect_with_crypto ) {
    	//attempt encrypted transport

  		final TransportHelper	helper = new TCPTransportHelper( channel );
    	TransportCryptoManager.getSingleton().manageCrypto( helper, shared_secrets, false, initial_data, new TransportCryptoManager.HandshakeListener() {
    		@Override
		    public void handshakeSuccess(ProtocolDecoder decoder, ByteBuffer remaining_initial_data ) {
    			//System.out.println( description+ " | crypto handshake success [" +_filter.getName()+ "]" );
    			TransportHelperFilter filter = decoder.getFilter();
    			setFilter( filter );
    			if ( Logger.isEnabled()){
    		      Logger.log(new LogEvent(LOGID, "Outgoing TCP stream to " + channel.socket().getRemoteSocketAddress() + " established, type = " + filter.getName(false)));
    			}

    			connectedOutbound( remaining_initial_data, listener );
     		}

    		@Override
		    public void handshakeFailure(Throwable failure_msg ) {
        	if( fallback_allowed && NetworkManager.OUTGOING_HANDSHAKE_FALLBACK_ALLOWED && !has_been_closed ) {
        		if( Logger.isEnabled() ) Logger.log(new LogEvent(LOGID, description+ " | crypto handshake failure [" +failure_msg.getMessage()+ "], attempting non-crypto fallback." ));
        		connect_with_crypto = false;
        		fallback_count++;
         		close( helper, "Handshake failure and retry" );
        		has_been_closed = false;
        		if ( initial_data != null ){

        			initial_data.position( 0 );
        		}
        		connectOutbound( initial_data, listener, priority );
        	}
        	else {
        		close( helper, "Handshake failure" );
        		listener.connectFailure( TCPTransportImpl.this, failure_msg );
        	}
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
    			throw( new RuntimeException());	// this is outgoing
    		}

    		@Override
		    public int
    		matchPlainHeader(
    				ByteBuffer			buffer )
    		{
    			throw( new RuntimeException());	// this is outgoing
    		}
    	});
  	}
  	else {  //no crypto
  		//if( fallback_count > 0 ) {
  		//	System.out.println( channel.socket()+ " | non-crypto fallback successful!" );
  		//}
  		setFilter( TCPTransportHelperFilterFactory.createTransparentFilter( channel ));

		if ( Logger.isEnabled()){
		  Logger.log(new LogEvent(LOGID, "Outgoing TCP stream to " + channel.socket().getRemoteSocketAddress() + " established, type = " + getFilter().getName(false) + ", fallback = " + (fallback_count==0?"no":"yes" )));
		}

		connectedOutbound( initial_data, listener );
  	}
  }




  private void setTransportBuffersSize( int size_in_bytes ) {
  	if( getFilter() == null ) {
  		Debug.out( "socket_channel == null" );
  		return;
  	}

    try{
    	SocketChannel	channel = getSocketChannel();

    	channel.socket().setSendBufferSize( size_in_bytes );
    	channel.socket().setReceiveBufferSize( size_in_bytes );

      int snd_real = channel.socket().getSendBufferSize();
      int rcv_real = channel.socket().getReceiveBufferSize();

      if (Logger.isEnabled())
    	  Logger.log(new LogEvent(LOGID, "Setting new transport [" + description
					+ "] buffer sizes: SND=" + size_in_bytes + " [" + snd_real
					+ "] , RCV=" + size_in_bytes + " [" + rcv_real + "]"));
    }
    catch( Throwable t ) {
      Debug.out( t );
    }
  }


  /**
   * Set the transport to the given speed mode.
   * @param mode to change to
   */
  @Override
  public void setTransportMode(int mode ) {
    if( mode == transport_mode )  return;  //already in mode

    switch( mode ) {
      case TRANSPORT_MODE_NORMAL:
        setTransportBuffersSize( 8 * 1024 );
        break;

      case TRANSPORT_MODE_FAST:
        setTransportBuffersSize( 64 * 1024 );
        break;

      case TRANSPORT_MODE_TURBO:
        setTransportBuffersSize( 512 * 1024 );
        break;

      default:
        Debug.out( "invalid transport mode given: " +mode );
    }

    transport_mode = mode;
  }

  protected void
  connectedOutbound(
	  ByteBuffer			remaining_initial_data,
	  ConnectListener		listener )
  {
	  if ( has_been_closed ){

		TransportHelperFilter	filter = getFilter();

	    if ( filter != null ){

	      filter.getHelper().close( "Connection closed" );

	      setFilter( null );
	    }

    	listener.connectFailure( TCPTransportImpl.this, new Throwable( "Connection closed" ));

	  }else{

		connectedOutbound();

		listener.connectSuccess( this, remaining_initial_data );
	  }
  }

  /**
   * Get the transport's speed mode.
   * @return current mode
   */
  @Override
  public int getTransportMode() {  return transport_mode;  }

  protected void
  close(
	TransportHelper		helper,
	String				reason )
  {
	 helper.close( reason );

	 close( reason );
  }

  void
  setConnectResult(
	boolean		ok )
  {
	 PluginProxy pp = plugin_proxy;
	 
	 if ( pp != null ){
		 
		 plugin_proxy = null;
		 
		 pp.setOK(ok);
	 }
  }

  /**
   * Close the transport connection.
   */
  @Override
  public void close(String reason ) {
    has_been_closed = true;
    setConnectResult( false );
    if( connect_request_key != null ) {
    	TCPNetworkManager.getSingleton().getConnectDisconnectManager().cancelRequest( connect_request_key );
    }

    readyForRead( false );
    readyForWrite( false );

	TransportHelperFilter	filter = getFilter();

    if ( filter != null ){

      filter.getHelper().close( reason );

      setFilter( null );
    }

    	// we need to set it ready for reading so that the other scheduling thread wakes up
    	// and discovers that this connection has been closed

    setReadyForRead();
  }
  
  @Override
  public boolean 
  isClosed()
  {
	  return( has_been_closed );
  }
}
