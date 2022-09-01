/*
 * Created on Feb 24, 2005
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

package com.biglybt.pifimpl.local.messaging;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.nat.NATTraversalHandler;
import com.biglybt.core.nat.NATTraverser;
import com.biglybt.core.networkmanager.ConnectionEndpoint;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.impl.TransportHelper;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.peermanager.messaging.MessageStreamFactory;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SHA1Simple;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadManagerListener;
import com.biglybt.pif.download.DownloadPeerListener;
import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.messaging.MessageException;
import com.biglybt.pif.messaging.MessageManager;
import com.biglybt.pif.messaging.MessageManagerListener;
import com.biglybt.pif.messaging.generic.GenericMessageConnection;
import com.biglybt.pif.messaging.generic.GenericMessageEndpoint;
import com.biglybt.pif.messaging.generic.GenericMessageHandler;
import com.biglybt.pif.messaging.generic.GenericMessageRegistration;
import com.biglybt.pif.peers.*;


/**
 *
 */
public class MessageManagerImpl implements MessageManager, NATTraversalHandler {

  private static MessageManagerImpl instance;

  private final HashMap compat_checks = new HashMap();

  private final DownloadManagerListener download_manager_listener = new DownloadManagerListener() {
    @Override
    public void downloadAdded(Download dwnld ) {
      dwnld.addPeerListener( new DownloadPeerListener() {
        @Override
        public void peerManagerAdded(final Download download, PeerManager peer_manager ) {
        	peer_manager.addListener(new PeerManagerListener2() {
		        @Override
		        public void eventOccurred(PeerManagerEvent event) {
			        final Peer peer = event.getPeer();
			        if (event.getType() == PeerManagerEvent.ET_PEER_ADDED) {
			        	peer.addListener(new PeerListener2() {
					        @Override
					        public void eventOccurred(PeerEvent event) {
						        if (event.getType() == PeerEvent.ET_STATE_CHANGED) {
						        	int new_state = (Integer) event.getData();

							        if( new_state == Peer.TRANSFERING ) {  //the peer handshake has completed
								        if( peer.supportsMessaging() ) {  //if it supports advanced messaging
									        //see if it supports any registered message types
									        Message[] messages = peer.getSupportedMessages();

									        for( int i=0; i < messages.length; i++ ) {
										        Message msg = messages[i];

										        for( Iterator it = compat_checks.entrySet().iterator(); it.hasNext(); ) {
											        Map.Entry entry = (Map.Entry)it.next();
											        Message message = (Message)entry.getKey();

											        if( msg.getID().equals( message.getID() ) ) {  //it does !
												        MessageManagerListener listener = (MessageManagerListener)entry.getValue();

												        listener.compatiblePeerFound( download, peer, message );
											        }
										        }
									        }
								        }
							        }
						        }
					        }
				        });
			        } else if (event.getType() == PeerManagerEvent.ET_PEER_REMOVED) {
				        for( Iterator i = compat_checks.values().iterator(); i.hasNext(); ) {
					        MessageManagerListener listener = (MessageManagerListener)i.next();

					        listener.peerRemoved( download, peer );
				        }
			        }
		        }
	        });
        }

        @Override
        public void peerManagerRemoved(Download download, PeerManager peer_manager ) { /* nothing */ }
      });
    }

    @Override
    public void downloadRemoved(Download download ) { /* nothing */ }
  };


  public static int
  adjustCrypto(
	GenericMessageEndpointImpl	endpoint,
	int							crypto )
  {		
	  return( adjustCrypto( endpoint.getConnectionEndpoint(), crypto ));
  }  
  
  public static int
  adjustCrypto(
	 ConnectionEndpoint		endpoint,
	int						crypto )
  {		
	  ProtocolEndpoint[] pes = endpoint.getProtocols();
		
	  if ( pes.length == 1 && pes[0].getType() == ProtocolEndpoint.PROTOCOL_TCP ){

		  InetSocketAddress isa = pes[0].getAddress();

		  if ( AENetworkClassifier.categoriseAddress( isa ) != AENetworkClassifier.AT_PUBLIC ){

			  crypto = MessageManager.STREAM_ENCRYPTION_NONE;
		  }
	  }

	  return( crypto );
  }

  

  public static synchronized MessageManagerImpl
  getSingleton(Core core)
  {
	  if ( instance == null ){

		  instance = new MessageManagerImpl( core );
	  }

	  return instance;
  }

  private Core core;

  private Map			message_handlers = new HashMap();

  private MessageManagerImpl(Core _core ) {

	  core	= _core;

	  core.getNATTraverser().registerHandler( this );
  }

  public NATTraverser
  getNATTraverser()
  {
	  return( core.getNATTraverser());
  }

  @Override
  public void registerMessageType(Message message ) throws MessageException {
    try {
      com.biglybt.core.peermanager.messaging.MessageManager.getSingleton().registerMessageType( new MessageAdapter( message ) );
    }
    catch( com.biglybt.core.peermanager.messaging.MessageException me ) {
      throw new MessageException( me.getMessage() );
    }
  }

  @Override
  public void deregisterMessageType(Message message ) {
    com.biglybt.core.peermanager.messaging.MessageManager.getSingleton().deregisterMessageType( new MessageAdapter( message ) );
  }



  @Override
  public void locateCompatiblePeers(PluginInterface plug_interface, Message message, MessageManagerListener listener ) {
    compat_checks.put( message, listener );  //TODO need to copy-on-write?

    if( compat_checks.size() == 1 ) {  //only register global peer locator listener once
      plug_interface.getDownloadManager().addListener( download_manager_listener );
    }
  }


  @Override
  public void cancelCompatiblePeersLocation(MessageManagerListener orig_listener ) {
    for( Iterator it = compat_checks.values().iterator(); it.hasNext(); ) {
      MessageManagerListener listener = (MessageManagerListener)it.next();

      if( listener == orig_listener ) {
        it.remove();
        break;
      }
    }
  }

  @Override
  public GenericMessageRegistration
  registerGenericMessageType(
	 final String					_type,
	 final String					description,
	 final int						stream_crypto,
	 final GenericMessageHandler	handler )

  	throws MessageException
  {
	final String	type 		= "AEGEN:" + _type;
	final byte[]	type_bytes 	= type.getBytes();

	final byte[][]	shared_secrets = new byte[][]{ new SHA1Simple().calculateHash( type_bytes ) };

	synchronized( message_handlers ){

		message_handlers.put( type, handler );
	}

	final NetworkManager.ByteMatcher matcher =
			new NetworkManager.ByteMatcher()
			{
				@Override
				public int
				matchThisSizeOrBigger()
				{
					return( maxSize());
				}

				@Override
				public int
				maxSize()
				{
					return type_bytes.length;
				}

				@Override
				public int
				minSize()
				{
					return maxSize();
				}

				@Override
				public Object
				matches(
					TransportHelper transport, ByteBuffer to_compare, int port )
				{
					int old_limit = to_compare.limit();

					to_compare.limit( to_compare.position() + maxSize() );

					boolean matches = to_compare.equals( ByteBuffer.wrap( type_bytes ) );

					to_compare.limit( old_limit );  //restore buffer structure

					return matches?"":null;
				}

				@Override
				public Object
				minMatches(
					TransportHelper transport, ByteBuffer to_compare, int port )
				{
					return( matches( transport, to_compare, port ));
				}

				@Override
				public byte[][]
				getSharedSecrets()
				{
					return( shared_secrets );
				}

			   	@Override
			    public int
				getSpecificPort()
				{
					return( -1 );
				}
			   	
		    	 @Override
		    	 public String 
		    	 getDescription()
		    	 {
		    		 return( type );
		    	 }
			};

	NetworkManager.getSingleton().requestIncomingConnectionRouting(
				matcher,
				new NetworkManager.RoutingListener()
				{
					@Override
					public void
					connectionRouted(
						final NetworkConnection connection, Object routing_data )
					{
						try{
							ByteBuffer[]	skip_buffer = { ByteBuffer.allocate(type_bytes.length) };

							connection.getTransport().read( skip_buffer, 0, 1 );

							if ( skip_buffer[0].remaining() != 0 ){

								Debug.out( "incomplete read" );
							}

							GenericMessageEndpointImpl endpoint		= new GenericMessageEndpointImpl( connection.getEndpoint());

							GenericMessageConnectionDirect direct_connection =
								GenericMessageConnectionDirect.receive(
										endpoint,
										type,
										description,
										stream_crypto,
										shared_secrets );

							GenericMessageConnectionImpl new_connection = new GenericMessageConnectionImpl( MessageManagerImpl.this, direct_connection );

							direct_connection.connect( connection );

							if ( handler.accept( new_connection )){

								new_connection.accepted();

							}else{

								connection.close( "connection not accepted" );
							}

						}catch( Throwable e ){

							Debug.printStackTrace(e);

							connection.close( e==null?null:Debug.getNestedExceptionMessage(e));
						}
					}

					@Override
					public boolean
					autoCryptoFallback()
					{
						return( stream_crypto != MessageManager.STREAM_ENCRYPTION_RC4_REQUIRED );
					}
				},
				new MessageStreamFactory() {
					@Override
					public MessageStreamEncoder createEncoder() {  return new GenericMessageEncoder();}
					@Override
					public MessageStreamDecoder createDecoder() {  return new GenericMessageDecoder(type, description);}
				});

	return(
		new GenericMessageRegistration()
		{
			@Override
			public GenericMessageEndpoint
			createEndpoint(
				InetSocketAddress	notional_target )
			{
				return( new GenericMessageEndpointImpl( notional_target ));
			}

			@Override
			public GenericMessageConnection
			createConnection(
				GenericMessageEndpoint	endpoint )

				throws MessageException
			{
				return( new GenericMessageConnectionImpl( MessageManagerImpl.this, type, description, (GenericMessageEndpointImpl)endpoint, stream_crypto, shared_secrets ));
			}

			@Override
			public void
			cancel()
			{
				NetworkManager.getSingleton().cancelIncomingConnectionRouting( matcher );

				synchronized( message_handlers ){

					message_handlers.remove( type );
				}
			}
		});
  }


  protected GenericMessageHandler
  getHandler(
		 String	type )
  {
		synchronized( message_handlers ){

			return((GenericMessageHandler)message_handlers.get( type ));
		}
  }

  	/* NATTraversalHandler methods
  	 */

	@Override
	public int
	getType()
	{
		return( NATTraverser.TRAVERSE_REASON_GENERIC_MESSAGING );
	}

	@Override
	public String
	getName()
	{
		return( "Generic Messaging" );
	}

	@Override
	public Map
	process(
		InetSocketAddress	originator,
		Map					message )
	{
		return( GenericMessageConnectionIndirect.receive( this, originator, message ));
	}
}
