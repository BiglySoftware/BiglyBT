/*
 * Created on Feb 1, 2005
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxySelector;
import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.SystemTime;


/**
 * Handles the process of proxy login/authentication/setup.
 */
public class ProxyLoginHandler {

  private static final int	READ_DONE			= 0;
  private static final int	READ_NOT_DONE		= 1;
  private static final int	READ_NO_PROGRESS	= 2;

  private static Object								proxy_lock			= new Object();
  private static List<ProxyInfo> 					proxies 			= new ArrayList<>();
  private static Map<InetSocketAddress,ProxyInfo>	proxy_address_map 	= new HashMap<>();
  private static int								proxy_index;
  
  static{
	  COConfigurationManager.addListener(
			 new COConfigurationListener()
			 {
					@Override
					public void
					configurationSaved()
					{
						readConfig();
					}
			 });

	  readConfig();
  }

  static void
  readConfig()
  {
	  boolean socks_same = COConfigurationManager.getBooleanParameter( "Proxy.Data.Same" );
	  
	  String socks_host = COConfigurationManager.getStringParameter( socks_same ? "Proxy.Host" : "Proxy.Data.Host" );
	  int socks_port = 0;
	  try{
		  String socks_port_str = COConfigurationManager.getStringParameter( socks_same ? "Proxy.Port" : "Proxy.Data.Port" );

		  socks_port_str = socks_port_str.trim();

		  if ( socks_port_str.length() > 0 ){

			  socks_port = Integer.parseInt( socks_port_str );
		  }
	  }catch( Throwable e ){  Debug.printStackTrace(e);  }

	  List<ProxyInfo>	latest_proxies = new ArrayList<>();
	  
	  InetSocketAddress address = new InetSocketAddress( socks_host, socks_port );

	  String version = COConfigurationManager.getStringParameter( "Proxy.Data.SOCKS.version" );
	  String user = COConfigurationManager.getStringParameter( socks_same ? "Proxy.Username" : "Proxy.Data.Username" );
	  if ( user.trim().equalsIgnoreCase("<none>")){
		  user = "";
	  }
	  String password = COConfigurationManager.getStringParameter( socks_same ? "Proxy.Password" : "Proxy.Data.Password" );
	  
	  latest_proxies.add( new ProxyInfo( address, version, user, password ));
	  
	  if ( !socks_same ){
		  
		  for ( int i=2;i<=COConfigurationManager.MAX_DATA_SOCKS_PROXIES;i++ ){
			  
			  socks_host = COConfigurationManager.getStringParameter( "Proxy.Data.Host." + i ).trim();
			  socks_port = 0;
			  try{
				  String socks_port_str = COConfigurationManager.getStringParameter( "Proxy.Data.Port." + i );

				  socks_port_str = socks_port_str.trim();

				  if ( socks_port_str.length() > 0 ){

					  socks_port = Integer.parseInt( socks_port_str );
				  }
			  }catch( Throwable e ){  Debug.printStackTrace(e);  }

			  if ( !( socks_host.isEmpty() || socks_port == 0 )){

				  address = new InetSocketAddress( socks_host, socks_port );
	
				  user = COConfigurationManager.getStringParameter( "Proxy.Data.Username." + i );
				  if ( user.trim().equalsIgnoreCase("<none>")){
					  user = "";
				  }
				  password = COConfigurationManager.getStringParameter( "Proxy.Data.Password." + i );
				  
				  latest_proxies.add( new ProxyInfo( address, version, user, password ));
			  }
		  }
	  }
	  
	  synchronized( proxy_lock ){
		  
		  int size = latest_proxies.size();
		  
		  boolean	changed = false;
		  
		  if ( size == proxies.size()){
		  
			  for ( int i=0;i<size;i++){
				  
				  if ( !latest_proxies.get(i).sameAs( proxies.get( i ))){
					  
					  changed = true;
					  
					  break;
				  }
			  }
		  }else{
			  
			  changed = true;
		  }
		  
		  if ( changed ){
			  
			  proxies = latest_proxies;
			  
			  proxy_address_map.clear();
			  
			  for ( ProxyInfo p: proxies ){
				  
				  proxy_address_map.put( p.address, p );
			  }
		  }
	  }
  }

  protected static boolean
  isDefaultProxy(
	InetSocketAddress	a )
  {
	  synchronized( proxy_lock ){
		
		  return( proxy_address_map.get( a ) != null );
	  }
  }
  
  protected static void
  proxyFailed(
	  InetSocketAddress		address,
	  Throwable				error )
  {
	  
  }
  
  private static final AEProxySelector	proxy_selector = AEProxySelectorFactory.getSelector();

  private final TCPTransportImpl proxy_connection;
  private final InetSocketAddress remote_address;
  private final ProxyListener proxy_listener;

  private String mapped_ip;
  private int socks5_handshake_phase = 0;
  private int socks5_address_length;

  private long read_start_time = 0;

  private final String socks_version;
  private final String socks_user;
  private final String socks_password;

  /**
   * Do proxy login.
   * @param proxy_connection transport connected to proxy server
   * @param remote_address address to proxy to
   * @param listener for proxy login success or failure
   */

  public
  ProxyLoginHandler(
	TCPTransportImpl 	_proxy_connection,
	InetSocketAddress	_remote_address,
	ProxyListener 		_listener )
  {
	  proxy_connection 	= _proxy_connection;
	  remote_address 	= _remote_address;
	  proxy_listener 	= _listener;
	  socks_version 	= "V4a";
	  socks_user		= "";
	  socks_password	= "";
  
	  connect();
  }
  
  public
  ProxyLoginHandler(
	TCPTransportImpl 	_proxy_connection,
	InetSocketAddress	_remote_address,
	ProxyListener 		_listener,
	InetSocketAddress	socks_address )
  {
	  proxy_connection 	= _proxy_connection;
	  remote_address 	= _remote_address;
	  proxy_listener 	= _listener;
	   
	  ProxyInfo proxy;
	  
	  synchronized( proxy_lock ){
		  
		  proxy = proxy_address_map.get( socks_address );
	  }
	  
	  if ( proxy == null ){
		  
		  socks_version 	= "V4a";
		  socks_user		= "";
		  socks_password	= "";
		  
	  }else{
		  socks_version 	= proxy.version;
		  socks_user		= proxy.user;
		  socks_password	= proxy.password;
	  }
	  
	  connect();
  }

  public
  ProxyLoginHandler(
	TCPTransportImpl 	_proxy_connection,
	InetSocketAddress	_remote_address,
	ProxyListener 		_listener,
	String				_socks_version,
	String				_socks_user,
	String				_socks_password )
  {
    proxy_connection 	= _proxy_connection;
    remote_address 		= _remote_address;
    proxy_listener 		= _listener;
    socks_version		= _socks_version;
    socks_user			= _socks_user;
    socks_password		= _socks_password;
    
    connect();
  }
  
  private void
  connect()
  {

    if ( remote_address.isUnresolved() || remote_address.getAddress() == null ){
      // deal with long "hostnames" that we get for, e.g., I2P destinations
    	mapped_ip = AEProxyFactory.getAddressMapper().internalise( remote_address.getHostName() );
    }
    else{

    	mapped_ip = AddressUtils.getHostNameNoResolve( remote_address );
    }

    if( socks_version.equals( "V4" ) ) {
      try{
        doSocks4Login( createSocks4Message() );
      }
      catch( Throwable t ) {
        //Debug.out( t );
        proxy_listener.connectFailure( t );
      }
    }
    else if( socks_version.equals( "V4a" ) ) {
      try{
        doSocks4Login( createSocks4aMessage() );
      }
      catch( Throwable t ) {
        //Debug.out( t );
        proxy_listener.connectFailure( t );
      }
    }
    else {  //"V5"
      doSocks5Login();
    }

  }

  public static InetSocketAddress
  getProxyAddress(
	InetSocketAddress	target )
  {
	  synchronized( proxy_lock ){
		  
		  int size = proxies.size();
		  
		  if ( size == 0 ){
			  
			  throw( new RuntimeException( "No proxies" ));
		  }
		  
		  ProxyInfo  proxy = proxies.get( proxy_index++ % size );
	  
		  Proxy p = proxy_selector.getSOCKSProxy( proxy.address, target  );

		  	// always use a proxy here as the calling code should know what it is doing...

		  if ( p.type() == Proxy.Type.SOCKS ){
	
			  SocketAddress sa = p.address();
	
			  if ( sa instanceof InetSocketAddress ){
	
				  InetSocketAddress isa = (InetSocketAddress)sa;
				  
				  proxy_address_map.put( isa, proxy );
				  
				  return( isa );
			  }
		  }

		  return( proxy.address );
	  }
  }

  private void doSocks4Login( final ByteBuffer[] data ) {
    try {
    	sendMessage( data[0] );  //send initial handshake to get things started

      //register for read ops
    	TCPNetworkManager.getSingleton().getReadSelector().register( proxy_connection.getSocketChannel(), new VirtualChannelSelector.VirtualSelectorListener() {
        @Override
        public boolean selectSuccess(VirtualChannelSelector selector, SocketChannel sc, Object attachment ) {
          try {
            int result = readMessage( data[1] );

            if( result == READ_DONE ) {
            	TCPNetworkManager.getSingleton().getReadSelector().cancel( proxy_connection.getSocketChannel() );
              parseSocks4Reply( data[1] );  //will throw exception on error
              proxy_listener.connectSuccess();
            }
            else {
            	TCPNetworkManager.getSingleton().getReadSelector().resumeSelects( proxy_connection.getSocketChannel() );  //resume read ops
            }

            return( result != READ_NO_PROGRESS );
          }
          catch( Throwable t ) {
          	//Debug.out( t );
        	  TCPNetworkManager.getSingleton().getReadSelector().cancel( proxy_connection.getSocketChannel() );
            proxy_listener.connectFailure( t );
            return false;
          }
         }

        @Override
        public void selectFailure(VirtualChannelSelector selector, SocketChannel sc, Object attachment, Throwable msg ) {
          //Debug.out( msg );
          TCPNetworkManager.getSingleton().getReadSelector().cancel( proxy_connection.getSocketChannel() );
          proxy_listener.connectFailure( msg );
        }
      }, null );
    }
    catch( Throwable t ) {
      //Debug.out( t );
      SocketChannel chan = proxy_connection.getSocketChannel();
      if ( chan != null ){
    	  TCPNetworkManager.getSingleton().getReadSelector().cancel( chan );
      }
      proxy_listener.connectFailure( t );
    }
  }



  private void doSocks5Login() {
    try {
      final ArrayList data = new ArrayList(2);

      ByteBuffer[] header = createSocks5Message();
      data.add( header[0] );  //message
      data.add( header[1] );  //reply buff

      sendMessage( (ByteBuffer)data.get(0) );  //send initial handshake to get things started

      //register for read ops
      TCPNetworkManager.getSingleton().getReadSelector().register( proxy_connection.getSocketChannel(), new VirtualChannelSelector.VirtualSelectorListener() {
        @Override
        public boolean selectSuccess(VirtualChannelSelector selector, SocketChannel sc, Object attachment ) {
          try {
            int result = readMessage( (ByteBuffer)data.get(1) );

            if( result == READ_DONE ) {
              boolean done = parseSocks5Reply( (ByteBuffer)data.get(1) );  //will throw exception on error

              if( done ) {
            	  TCPNetworkManager.getSingleton().getReadSelector().cancel( proxy_connection.getSocketChannel() );
                proxy_listener.connectSuccess();
              }
              else {
                ByteBuffer[] raw = createSocks5Message();
                data.set( 0, raw[0] );
                data.set( 1, raw[1] );

                if( raw[0] != null )  sendMessage( raw[0] );
                TCPNetworkManager.getSingleton().getReadSelector().resumeSelects( proxy_connection.getSocketChannel() );  //resume read ops
              }
            }
            else {
            	TCPNetworkManager.getSingleton().getReadSelector().resumeSelects( proxy_connection.getSocketChannel() );  //resume read ops
            }

            return( result != READ_NO_PROGRESS );
          }
          catch( Throwable t ) {
            //Debug.out( t );
        	  TCPNetworkManager.getSingleton().getReadSelector().cancel( proxy_connection.getSocketChannel() );
            proxy_listener.connectFailure( t );
            return false;
          }
        }

        @Override
        public void selectFailure(VirtualChannelSelector selector, SocketChannel sc, Object attachment, Throwable msg ) {
          //Debug.out( msg );
          TCPNetworkManager.getSingleton().getReadSelector().cancel( proxy_connection.getSocketChannel() );
          proxy_listener.connectFailure( msg );
        }
      }, null );
    }
    catch( Throwable t ) {
      //Debug.out( t );
      SocketChannel chan = proxy_connection.getSocketChannel();
      if ( chan != null ){
    	  TCPNetworkManager.getSingleton().getReadSelector().cancel( chan );
      }
      proxy_listener.connectFailure( t );
    }
  }




  void parseSocks4Reply( ByteBuffer reply ) throws IOException {
      byte ver = reply.get();
      byte resp = reply.get();

      if( ver != 0 || resp != 90 ) {
        throw new IOException( "SOCKS 4(a): connection declined [" +ver+ "/" + resp + "]" );
      }
  }





  void sendMessage( ByteBuffer msg ) throws IOException {
    long start_time = SystemTime.getCurrentTime();

    while( msg.hasRemaining() ) {
      if( proxy_connection.write( new ByteBuffer[]{ msg }, 0, 1 ) < 1 ) {
        if( SystemTime.getCurrentTime() - start_time > 30*1000 ) {
          String error = "proxy handshake message send timed out after 30sec";
          //Debug.out( error );
          throw new IOException( error );
        }

        try {   Thread.sleep( 10 );   }catch( Throwable t ) {t.printStackTrace();}
      }
    }
  }



  int readMessage( ByteBuffer msg ) throws IOException {
    if( read_start_time == 0 )  read_start_time = SystemTime.getCurrentTime();

    long bytes_read = proxy_connection.read( new ByteBuffer[]{ msg }, 0, 1 );

    if( !msg.hasRemaining() ) {
      msg.position( 0 );
      read_start_time = 0;  //reset for next round
      return READ_DONE;
    }

    if( SystemTime.getCurrentTime() - read_start_time > 30*1000 ) {
      String error = "proxy message read timed out after 30sec";
      //Debug.out( error );
      throw new IOException( error );
    }

    return( bytes_read==0?READ_NO_PROGRESS:READ_NOT_DONE);
  }




  private ByteBuffer[] createSocks4Message() throws Exception {
    ByteBuffer handshake = ByteBuffer.allocate( 256 + mapped_ip.length() );   //TODO convert to direct?

    handshake.put( (byte)4 ); // socks 4(a)
    handshake.put( (byte)1 ); // command = CONNECT
    handshake.putShort( (short)remote_address.getPort() );

    	// for v4 we have to resolve the address locally

    InetAddress ia = remote_address.getAddress();

    String host_str;

    if ( ia == null ){

    		// unresolved

    	host_str = remote_address.getHostName();

    }else{

    	host_str = ia.getHostAddress();
    }

    InetAddress address = HostNameToIPResolver.syncResolve( host_str );

    if ( address == null ){

    	throw( new UnknownHostException( host_str ));
    }

    byte[] ip_bytes = address.getAddress();

    if ( ip_bytes.length != 4 ){

    	throw( new Exception( "Unsupported IPv6 address: " + remote_address ));
    }

    handshake.put( ip_bytes[ 0 ] );
    handshake.put( ip_bytes[ 1 ] );
    handshake.put( ip_bytes[ 2 ] );
    handshake.put( ip_bytes[ 3 ] );

    if( socks_user.length() > 0 ){

      handshake.put( socks_user.getBytes() );
    }

    handshake.put( (byte)0 );

    handshake.flip();

    return new ByteBuffer[] { handshake, ByteBuffer.allocate( 8 ) };     //TODO convert to direct?
  }



  private ByteBuffer[] createSocks4aMessage() {
    ByteBuffer handshake = ByteBuffer.allocate( 256 + mapped_ip.length() );   //TODO convert to direct?

    handshake.put( (byte)4 ); // socks 4(a)
    handshake.put( (byte)1 ); // command = CONNECT
    handshake.putShort( (short)remote_address.getPort() ); // port
    handshake.put( (byte)0 );
    handshake.put( (byte)0 );
    handshake.put( (byte)0 );
    handshake.put( (byte)1 ); // indicates socks 4a

    if( socks_user.length() > 0 ) {
      handshake.put( socks_user.getBytes() );
    }

    handshake.put( (byte)0 );
    handshake.put( mapped_ip.getBytes() );
    handshake.put( (byte)0 );

    handshake.flip();

    return new ByteBuffer[] { handshake, ByteBuffer.allocate( 8 ) };   //TODO convert to direct?
  }




  ByteBuffer[] createSocks5Message() {
    ByteBuffer handshake = ByteBuffer.allocate( 256 + mapped_ip.length() );   //TODO convert to direct?

    if( socks5_handshake_phase == 0 ) {  // say hello
      //System.out.println( "socks5 write phase 0" );

      handshake.put( (byte)5 ); // socks 5
      handshake.put( (byte)2 ); // 2 methods
      handshake.put( (byte)0 ); // no auth
      handshake.put( (byte)2 ); // user/pw

      handshake.flip();
      socks5_handshake_phase = 1;

      return new ByteBuffer[] { handshake, ByteBuffer.allocate( 2 ) };   //TODO convert to direct?
    }

    if( socks5_handshake_phase == 1 ) {  // user/password auth
      //System.out.println( "socks5 write phase 1" );

      handshake.put( (byte)1 ); // user/pw version
      handshake.put( (byte)socks_user.length() ); // user length
      handshake.put( socks_user.getBytes() );
      handshake.put( (byte)socks_password.length() ); // password length
      handshake.put( socks_password.getBytes() );

      handshake.flip();
      socks5_handshake_phase = 2;

      return new ByteBuffer[] { handshake, ByteBuffer.allocate( 2 ) };   //TODO convert to direct?
    }

    if( socks5_handshake_phase == 2 ) {  // request
      //System.out.println( "socks5 write phase 2" );

      handshake.put( (byte)5 ); // version
      handshake.put( (byte)1 ); // connect
      handshake.put( (byte)0 ); // reserved

      // use the mapped ip for dns resolution so we don't leak the
      // actual address if this is a secure one (e.g. I2P one)
      try {
        byte[] ip_bytes = HostNameToIPResolver.syncResolve( mapped_ip ).getAddress();

        handshake.put( (byte)1 ); // IP4

        handshake.put( ip_bytes[ 0 ] );
        handshake.put( ip_bytes[ 1 ] );
        handshake.put( ip_bytes[ 2 ] );
        handshake.put( ip_bytes[ 3 ] );

      }
      catch (Throwable e) {
        handshake.put( (byte)3 );  // address type = domain name
        handshake.put( (byte)mapped_ip.length() );  // address type = domain name
        handshake.put( mapped_ip.getBytes() );
      }

      handshake.putShort( (short)remote_address.getPort() ); // port

      handshake.flip();
      socks5_handshake_phase = 3;

      return new ByteBuffer[] { handshake, ByteBuffer.allocate( 5 ) };   //TODO convert to direct?
    }

    //System.out.println( "socks5 write phase 3..." );

    //reply has to be processed in two parts as it has variable length component at the end
    //socks5_handshake_phase == 3, part two
    socks5_handshake_phase = 4;
    return new ByteBuffer[] { null, ByteBuffer.allocate( socks5_address_length ) };       //TODO convert to direct?
  }



  private boolean parseSocks5Reply( ByteBuffer reply ) throws IOException {
    if( socks5_handshake_phase == 1 ) { // reply from hello
      //System.out.println( "socks5 read phase 1" );

      reply.get();  // version byte
      byte method = reply.get();

      if( method != 0 && method != 2 ) {
        throw new IOException( "SOCKS 5: no valid method [" + method + "]" );
      }

      // no auth -> go to request phase
      if( method == 0 ) {
        socks5_handshake_phase = 2;
      }

      return false;
    }

    if( socks5_handshake_phase == 2 ) {  // reply from auth
      //System.out.println( "socks5 read phase 2" );

      reply.get();  // version byte
      byte status = reply.get();

      if( status != 0 ) {
        throw new IOException( "SOCKS 5: authentication fails [status=" +status+ "]" );
      }

      return false;
    }


    if( socks5_handshake_phase == 3 ) {   // reply from request, first part
      //System.out.println( "socks5 read phase 3" );

      reply.get();  // version byte
      byte rep = reply.get();

      if( rep != 0 ) {
        String error_msgs[] = {
            "",
            "General SOCKS server failure",
            "connection not allowed by ruleset",
            "Network unreachable",
            "Host unreachable",
            "Connection refused (authentication failure?)",
            "TTL expired (can mean authentication failure)",
            "Command not supported",
            "Address type not supported" };
        String error_msg = rep < error_msgs.length ? error_msgs[ rep ] : "Unknown error";
        throw new IOException( "SOCKS request failure [" + error_msg + "/" + rep + "]" );
      }

      reply.get();  // reserved byte
      byte atype = reply.get();
      byte first_address_byte = reply.get();

      if( atype == 1 ) {
        socks5_address_length = 3; // already read one
      }
      else if( atype == 3 ) {
        socks5_address_length = first_address_byte;  // domain name, first byte gives length of remainder
      }
      else {
        socks5_address_length = 15; // already read one
      }

      socks5_address_length += 2;  // 2 bytes for port
      return false;
    }

    //System.out.println( "socks5 read phase 4..." );

    //socks5_handshake_phase 4
    //reply from request, last part
    return true;  //only done AFTER last part of request reply has been read from stream
  }




  public interface ProxyListener {
    /**
     * The proxied connection attempt succeeded.
     */
    public void connectSuccess() ;

    /**
     * The proxied connection attempt failed.
     * @param failure_msg failure reason
     */
    public void connectFailure( Throwable failure_msg );
  }

  private static class
  ProxyInfo
  {
	  private final InetSocketAddress 	address;
	  private final String 				version;
	  private final String 				user;
	  private final String 				password;
	  
	  private
	  ProxyInfo(
		InetSocketAddress		_address,
		String					_version,
		String					_user,
		String					_password )
	  {
		  address		= _address;
		  version		= _version;
		  user			= _user;
		  password		= _password;
	  }
	  
	  private boolean
	  sameAs(
		ProxyInfo	other )
	  {
		  return( address.equals( other.address ) &&
				  version.equals( other.version ) &&
				  user.equals( other.user ) &&
				  password.equals( other.password ));
	  }
  }
}
