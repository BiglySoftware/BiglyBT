/*
 * File    : NatCheckerServer.java
 * Created : 12 oct. 2003 19:05:09
 * By      : Olivier
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

package com.biglybt.core.ipchecker.natchecker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.networkmanager.impl.TransportHelper;
import com.biglybt.core.networkmanager.impl.http.HTTPNetworkManager;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.peermanager.messaging.MessageStreamFactory;
import com.biglybt.core.peermanager.messaging.azureus.AZMessageDecoder;
import com.biglybt.core.peermanager.messaging.azureus.AZMessageEncoder;
import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;



/**
 *
 *
 */
public class NatCheckerServer extends AEThread {
	static final LogIDs LOGID = LogIDs.NET;
    private static final String incoming_handshake = "NATCHECK_HANDSHAKE";

    private final InetAddress	bind_ip;
    private boolean				bind_ip_set;
    private final String 		check;
    private final boolean		http_test;

    private ServerSocket server;

    private volatile boolean bContinue = true;
    private final boolean use_incoming_router;
    private NetworkManager.ByteMatcher matcher;


    public
    NatCheckerServer(
    	InetAddress 	_bind_ip,
    	int 			_port,
    	String 			_check,
    	boolean 		_http_test )

    	throws Exception
    {
      super("Nat Checker Server");

      bind_ip		= _bind_ip;
      check		 	= _check;
      http_test		= _http_test;

      if ( http_test ){

    	  HTTPNetworkManager	net_man = HTTPNetworkManager.getSingleton();

    	  if ( net_man.isHTTPListenerEnabled()){

    		  use_incoming_router = _port == net_man.getHTTPListeningPortNumber();

    	  }else{

    		  use_incoming_router = false;
    	  }

    	  if ( use_incoming_router ){

    		  if ( !net_man.isEffectiveBindAddress( bind_ip )){

    			  net_man.setExplicitBindAddress( bind_ip );

    			  bind_ip_set	= true;
    		  }
    	  }
      }else{

    	  TCPNetworkManager	net_man = TCPNetworkManager.getSingleton();

      	  if ( net_man.isDefaultTCPListenerEnabled()){

    		  use_incoming_router = _port == net_man.getDefaultTCPListeningPortNumber();

    	  }else{

    		  use_incoming_router = false;
    	  }

	      if ( use_incoming_router ) {

 		 	if ( !net_man.getDefaultIncomingSocketManager().isEffectiveBindAddress( bind_ip )){

    			  net_man.getDefaultIncomingSocketManager().setExplicitBindAddress( bind_ip );

    			  bind_ip_set	= true;
    		  }

	    	  	//test port and currently-configured listening port are the same,
	    	  	//so register for incoming connection routing

	        matcher = new NetworkManager.ByteMatcher() {
			  @Override
			  public int matchThisSizeOrBigger(){ return( maxSize()); }
	          @Override
	          public int maxSize() {  return incoming_handshake.getBytes().length;  }
	          @Override
	          public int minSize(){ return maxSize(); }

	          @Override
	          public Object matches(TransportHelper transport, ByteBuffer to_compare, int port ) {
	            int old_limit = to_compare.limit();
	            to_compare.limit( to_compare.position() + maxSize() );
	            boolean matches = to_compare.equals( ByteBuffer.wrap( incoming_handshake.getBytes() ) );
	            to_compare.limit( old_limit );  //restore buffer structure
	            return matches?"":null;
	          }
	          @Override
	          public Object minMatches(TransportHelper transport, ByteBuffer to_compare, int port ) { return( matches( transport, to_compare, port )); }
	          @Override
	          public byte[][] getSharedSecrets(){ return( null ); }
	  	   	  @Override
		        public int getSpecificPort(){return( -1 );
	  	   	  }
		    	 @Override
		    	 public String 
		    	 getDescription()
		    	 {
		    		 return( "NatChecker" );
		    	 }
	        };

	        NetworkManager.getSingleton().requestIncomingConnectionRouting(
	            matcher,
	            new NetworkManager.RoutingListener() {
	              @Override
	              public void
	              connectionRouted(
	            	NetworkConnection 	connection,
	            	Object 				routing_data )
	              {
	            	  if (Logger.isEnabled())
	            		  Logger.log(new LogEvent(LOGID, "Incoming connection from ["
	            				  + connection + "] successfully routed to NAT CHECKER"));

	            	  try{
	            		  ByteBuffer	msg = getMessage();

	            		  Transport transport = connection.getTransport();

	            		  long	start = SystemTime.getCurrentTime();

	            		  while( msg.hasRemaining()){

	            			  transport.write( new ByteBuffer[]{ msg }, 0, 1 );

	            			  if ( msg.hasRemaining()){

	            				  long now = SystemTime.getCurrentTime();

	            				  if ( now < start ){

	            					  start = now;

	            				  }else{

	            					  if ( now - start > 30000 ){

	            						  throw( new Exception( "Timeout" ));
	            					  }
	            				  }

	            				  Thread.sleep( 50 );
	            			  }
	            		  }
	            	  }catch( Throwable t ) {

	            		  Debug.out( "Nat check write failed", t );
	            	  }

	            	  connection.close( null );
	              }

	              @Override
	              public boolean
	          	  autoCryptoFallback()
	              {
	            	  return( true );
	              }
	            },
	            new MessageStreamFactory() {
	              @Override
	              public MessageStreamEncoder createEncoder() {  return new AZMessageEncoder(AZMessageEncoder.PADDING_MODE_NONE);  /* unused */}
	              @Override
	              public MessageStreamDecoder createDecoder() {  return new AZMessageDecoder();  /* unused */}
	            });
	      }

	      if (Logger.isEnabled())
  				Logger.log(new LogEvent(LOGID, "NAT tester using central routing for "
  						+ "server socket"));
      }

      if ( !use_incoming_router ){

    	  //different port than already listening on, start new listen server

        try{

          server = new ServerSocket();  //unbound
          server.setReuseAddress( true );  //set SO_REUSEADDR

          InetSocketAddress address;

          if( bind_ip != null ) {

        	  address = new InetSocketAddress( bind_ip, _port );

          }else {

        	  address = new InetSocketAddress( _port );
          }

  	      server.bind( address );

  	      if (Logger.isEnabled())	Logger.log(new LogEvent(LOGID, "NAT tester server socket bound to " +address ));


        }catch(Exception e) {

        	Logger.log(new LogEvent(LOGID, "NAT tester failed to setup listener socket", e ));

        	throw( e );
        }
      }
    }

    protected ByteBuffer
    getMessage()

    	throws IOException
    {
		  Map	map = new HashMap();

		  map.put( "check", check );

		  byte[]	map_bytes = BEncoder.encode( map );

		  ByteBuffer msg = ByteBuffer.allocate( 4 + map_bytes.length );

		  msg.putInt( map_bytes.length );
		  msg.put( map_bytes );

		  msg.flip();

		  return( msg );
    }

    @Override
    public void runSupport() {
      while(bContinue) {
        try {
          if (use_incoming_router) {
            //just NOOP loop sleep while waiting for routing
            Thread.sleep(20);
          }
          else {
            //listen for accept
          	Socket sck = server.accept();

          	try{
          		sck.getOutputStream().write( getMessage().array());

          		sck.close();

          		sck = null;
          	}finally{
        	  if ( sck != null ){
        		  try{
        			  sck.close();
        		  }catch( Throwable e ){
        		  }
        	  }
          	}
          }
        } catch(Exception e) {
        	//Debug.printStackTrace(e);
        	bContinue = false;
        }
      }
    }

    public void stopIt() {
      bContinue = false;

      if( use_incoming_router ) {

    	  if ( http_test ){

    		  if ( bind_ip_set ){

    			  HTTPNetworkManager.getSingleton().clearExplicitBindAddress();
    		  }
    	  }else{

    		  NetworkManager.getSingleton().cancelIncomingConnectionRouting( matcher );

    		  if ( bind_ip_set ){

    			  TCPNetworkManager.getSingleton().getDefaultIncomingSocketManager().clearExplicitBindAddress();
    		  }
    	  }
      }
      else if( server != null ) {
        try {
          server.close();
        }
        catch(Throwable t) { t.printStackTrace(); }
      }
    }


  }
