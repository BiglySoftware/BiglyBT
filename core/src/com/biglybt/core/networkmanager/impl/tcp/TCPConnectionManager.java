/*
 * Created on Sep 13, 2004
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

import java.net.*;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.util.*;




/**
 * Manages new connection establishment and ended connection termination.
 */
public class TCPConnectionManager {
  private static final LogIDs LOGID = LogIDs.NWMAN;

  private static final int CONNECT_TIMEOUT_MIN	= 5000;
  
  static int CONNECT_SELECT_LOOP_TIME			= 100;
  static int CONNECT_SELECT_LOOP_MIN_TIME		= 0;

  static int MIN_SIMULTANEOUS_CONNECT_ATTEMPTS = 3;
  public static int MAX_SIMULTANEOUS_CONNECT_ATTEMPTS;

  static int max_outbound_connections;

  static {
    MAX_SIMULTANEOUS_CONNECT_ATTEMPTS = COConfigurationManager.getIntParameter( "network.max.simultaneous.connect.attempts" );

    if( MAX_SIMULTANEOUS_CONNECT_ATTEMPTS < 1 ) { //should never happen, but hey
   	 MAX_SIMULTANEOUS_CONNECT_ATTEMPTS = 1;
   	 COConfigurationManager.setParameter( "network.max.simultaneous.connect.attempts", 1 );
    }

    MIN_SIMULTANEOUS_CONNECT_ATTEMPTS = MAX_SIMULTANEOUS_CONNECT_ATTEMPTS - 2;

    if( MIN_SIMULTANEOUS_CONNECT_ATTEMPTS < 1 ) {
      MIN_SIMULTANEOUS_CONNECT_ATTEMPTS = 1;
    }

    COConfigurationManager.addParameterListener( "network.max.simultaneous.connect.attempts", new ParameterListener() {
      @Override
      public void parameterChanged(String parameterName ) {
        MAX_SIMULTANEOUS_CONNECT_ATTEMPTS = COConfigurationManager.getIntParameter( "network.max.simultaneous.connect.attempts" );
        MIN_SIMULTANEOUS_CONNECT_ATTEMPTS = MAX_SIMULTANEOUS_CONNECT_ATTEMPTS - 2;
        if( MIN_SIMULTANEOUS_CONNECT_ATTEMPTS < 1 ) {
          MIN_SIMULTANEOUS_CONNECT_ATTEMPTS = 1;
        }
      }
    });

	COConfigurationManager.addAndFireParameterListeners(
			new String[]{
			    "network.tcp.max.connections.outstanding",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					max_outbound_connections = COConfigurationManager.getIntParameter( "network.tcp.max.connections.outstanding" );
				}
			});

	COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"network.tcp.connect.select.time",
				"network.tcp.connect.select.min.time",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					CONNECT_SELECT_LOOP_TIME 		= COConfigurationManager.getIntParameter(  "network.tcp.connect.select.time" );
					CONNECT_SELECT_LOOP_MIN_TIME 	= COConfigurationManager.getIntParameter(  "network.tcp.connect.select.min.time" );
				}
			});
  }

  int 		rcv_size;
  int 		snd_size;
  String 	ip_tos;
  int 		local_bind_port;
  boolean	ignore_bind_for_lan_addresses;
  
  {
	  COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"network.tcp.socket.SO_RCVBUF",
				"network.tcp.socket.SO_SNDBUF",
				"network.tcp.socket.IPDiffServ",
				"network.bind.local.port",
				ConfigKeys.Connection.BCFG_NETWORK_IGNORE_BIND_FOR_LAN,
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					rcv_size = COConfigurationManager.getIntParameter( "network.tcp.socket.SO_RCVBUF" );

					snd_size = COConfigurationManager.getIntParameter( "network.tcp.socket.SO_SNDBUF" );

					ip_tos = COConfigurationManager.getStringParameter( "network.tcp.socket.IPDiffServ" );

					local_bind_port = COConfigurationManager.getIntParameter( "network.bind.local.port" );
					
					ignore_bind_for_lan_addresses = COConfigurationManager.getBooleanParameter(ConfigKeys.Connection.BCFG_NETWORK_IGNORE_BIND_FOR_LAN );
				}
			});
  }

  private static final int CONNECT_ATTEMPT_TIMEOUT = 15*1000;  // parg: reduced from 30 sec as almost never see worthwhile connections take longer that this
  private static final int CONNECT_ATTEMPT_STALL_TIME = 3*1000;  //3sec
  private static final boolean SHOW_CONNECT_STATS = false;

  private final VirtualChannelSelector connect_selector = new VirtualChannelSelector( "TCP Connect/Disconnect Manager", VirtualChannelSelector.OP_CONNECT, true );

  private long connection_request_id_next;

  final Set<ConnectionRequest> new_requests =
		  new TreeSet<>(
				  new Comparator<ConnectionRequest>() {
					  @Override
					  public int
					  compare(
							  ConnectionRequest r1,
							  ConnectionRequest r2) {
						  if (r1 == r2) {

							  return (0);
						  }

						  int res = r1.getPriority() - r2.getPriority();

						  if (res == 0) {

							  // check for duplicates
							  // erm, no, think of the socks data proxy connections luke

						/*
						InetSocketAddress a1 = r1.address;
						InetSocketAddress a2 = r2.address;

						if ( a1.getPort() == a2.getPort()){

							if ( Arrays.equals( a1.getAddress().getAddress(), a2.getAddress().getAddress())){

								return( 0 );
							}
						}
						*/

							  res = r1.getRandom() - r2.getRandom();

							  if (res == 0) {

								  long l = r1.getID() - r2.getID();

								  if (l < 0) {

									  res = -1;

								  } else if (l > 0) {

									  res = 1;

								  } else {

									  Debug.out("arghhh, borkage");
								  }
							  }
						  }

						  return (res);
					  }
				  });

  final List<ConnectListener> 	canceled_requests 	= new ArrayList<>();

  final AEMonitor	new_canceled_mon	= new AEMonitor( "ConnectDisconnectManager:NCM");

  final Map<ConnectionRequest,Object> pending_attempts 		= new HashMap<>();
  
  	// plugin-proxies will connect at the network layer immediately even though their target connection has yet to even start
  	// They do use a socks protocol to further establish connectivity which only completes once the actual connection
  	// is established so we wait for this to occur in order to limit 'pending attempts' correctly
  
  final Map<ConnectionRequest,Object> pending_pp_attempts 	= new HashMap<>();

  final LinkedList<SocketChannel> 	pending_closes 	= new LinkedList<>();

  private final Map<SocketChannel,Long>		delayed_closes	= new HashMap<>();

  private final AEMonitor	pending_closes_mon = new AEMonitor( "ConnectDisconnectManager:PC");

  private boolean max_conn_exceeded_logged;


  public
  TCPConnectionManager()
  {
	  Set<String>	types = new HashSet<>();

	  types.add( CoreStats.ST_NET_TCP_OUT_CONNECT_QUEUE_LENGTH );
	  types.add( CoreStats.ST_NET_TCP_OUT_CANCEL_QUEUE_LENGTH );
	  types.add( CoreStats.ST_NET_TCP_OUT_CLOSE_QUEUE_LENGTH );
	  types.add( CoreStats.ST_NET_TCP_OUT_PENDING_QUEUE_LENGTH );
	  types.add( CoreStats.ST_NET_TCP_OUT_PENDING_PP_QUEUE_LENGTH );

	  CoreStats.registerProvider(
			  types,
			  new CoreStatsProvider()
			  {
					@Override
					public void
					updateStats(
						Set<String>				types,
						Map<String,Object>		values )
					{
						if ( types.contains( CoreStats.ST_NET_TCP_OUT_CONNECT_QUEUE_LENGTH )){

							values.put( CoreStats.ST_NET_TCP_OUT_CONNECT_QUEUE_LENGTH, new Long( new_requests.size()));
						}

						if ( types.contains( CoreStats.ST_NET_TCP_OUT_CANCEL_QUEUE_LENGTH )){

							values.put( CoreStats.ST_NET_TCP_OUT_CANCEL_QUEUE_LENGTH, new Long( canceled_requests.size()));
						}

						if ( types.contains( CoreStats.ST_NET_TCP_OUT_CLOSE_QUEUE_LENGTH )){

							values.put( CoreStats.ST_NET_TCP_OUT_CLOSE_QUEUE_LENGTH, new Long( pending_closes.size()));
						}

						if ( types.contains( CoreStats.ST_NET_TCP_OUT_PENDING_QUEUE_LENGTH )){

							values.put( CoreStats.ST_NET_TCP_OUT_PENDING_QUEUE_LENGTH, new Long( pending_attempts.size()));
						}
						
						if ( types.contains( CoreStats.ST_NET_TCP_OUT_PENDING_PP_QUEUE_LENGTH )){

							values.put( CoreStats.ST_NET_TCP_OUT_PENDING_PP_QUEUE_LENGTH, new Long( pending_pp_attempts.size()));
						}
					}
			  });

	  new AEThread2( "ConnectDisconnectManager", true )
	  {
		  @Override
		  public void
		  run()
		  {
			  while( true ){

				  addNewOutboundRequests();

				  runSelect();

				  doClosings();
			  }
		  }
	  }.start();
  }

  public int
  getMaxOutboundPermitted()
  {
	  return( Math.max( max_outbound_connections - new_requests.size(), 0 ));
  }

  void
  addNewOutboundRequests()
  {
	  while( pending_attempts.size() + pending_pp_attempts.size() < MIN_SIMULTANEOUS_CONNECT_ATTEMPTS){

		  ConnectionRequest cr = null;

		  try{
			  new_canceled_mon.enter();

			  if( new_requests.isEmpty() )  break;

			  Iterator<ConnectionRequest> it = new_requests.iterator();

			  cr = it.next();

			  it.remove();

		  }finally{

			  new_canceled_mon.exit();
		  }

		  if( cr != null ){

			  addNewRequest( cr );
		  }
	  }
  }



  private void
  addNewRequest(
	final ConnectionRequest request )
  {
	  request.setConnectTimeout( request.listener.connectAttemptStarted( request.getConnectTimeout()));


	  boolean 	ipv6problem 	= false;
	  boolean	bind_failed		= false;
	  boolean	explicit_bind	= false;
	  
	  try {

		  request.channel = SocketChannel.open();

		  InetAddress bindIP = null;

		  try {  //advanced socket options
			  if( rcv_size > 0 ) {
				  if (Logger.isEnabled())
					  Logger.log(new LogEvent(LOGID, "Setting socket receive buffer size"
							  + " for outgoing connection [" + request.address + "] to: "
							  + rcv_size));
				  request.channel.socket().setReceiveBufferSize( rcv_size );
			  }

			  if( snd_size > 0 ) {
				  if (Logger.isEnabled())
					  Logger.log(new LogEvent(LOGID, "Setting socket send buffer size "
							  + "for outgoing connection [" + request.address + "] to: "
							  + snd_size));
				  request.channel.socket().setSendBufferSize( snd_size );
			  }

			  if( ip_tos.length() > 0 ) {
				  if (Logger.isEnabled())
					  Logger.log(new LogEvent(LOGID, "Setting socket TOS field "
							  + "for outgoing connection [" + request.address + "] to: "
							  + ip_tos));
				  request.channel.socket().setTrafficClass( Integer.decode( ip_tos ).intValue() );
			  }

			  if( local_bind_port > 0 ) {
				  request.channel.socket().setReuseAddress( true );
			  }

			  bindIP = (InetAddress)request.listener.getConnectionProperty( AEProxyFactory.PO_EXPLICIT_BIND );
			  
			  try {
				  if ( bindIP != null ){
					
					  explicit_bind = true;
					  
					  if (Logger.isEnabled()) 	Logger.log(new LogEvent(LOGID, "Binding outgoing connection [" + request.address + "] to local explicit IP address: " + bindIP+":"+local_bind_port));
					  
					  request.channel.socket().bind( new InetSocketAddress( bindIP, local_bind_port ) );

				  }else{
					  
					  InetSocketAddress isa = request.address;
							  
					  if ( 	ignore_bind_for_lan_addresses &&
							AddressUtils.isLANLocalAddress( isa ) == AddressUtils.LAN_LOCAL_YES && 
							!AddressUtils.isExplicitLANRateLimitAddress(isa)){
						  
						  bindIP = null;
						  
					  }else{
					  
						  bindIP = NetworkAdmin.getSingleton().getMultiHomedOutgoingRoundRobinBindAddress(isa.getAddress());
					  }
					  
					  if ( bindIP != null ) {
						  	// ignore bind for plugin proxies as we connect directly to them - if they need to
						  	// enforce any bindings on delegated connections then that is their job to implement
	
						  if ( bindIP.isAnyLocalAddress() || !AEProxyFactory.isPluginProxy( request.address )){
							  
							  if (Logger.isEnabled()) 	Logger.log(new LogEvent(LOGID, "Binding outgoing connection [" + request.address + "] to local IP address: " + bindIP+":"+local_bind_port));
							  
							  request.channel.socket().bind( new InetSocketAddress( bindIP, local_bind_port ) );
						  }
					  }else if( local_bind_port > 0 ) {
						  
						  if (Logger.isEnabled()) Logger.log(new LogEvent(LOGID, "Binding outgoing connection [" + request.address + "] to local port #: " +local_bind_port));
						  
						  request.channel.socket().bind( new InetSocketAddress( local_bind_port ) );
					  }
				  }
			  }catch( SocketException e ){

				  bind_failed = true;

				  String msg = e.getMessage().toLowerCase();

				  if (	( msg.contains( "address family not supported by protocol family") || msg.contains( "protocol family unavailable" )) &&
						!NetworkAdmin.getSingleton().hasIPV6Potential(true)){

					  ipv6problem = true;

				  }

				  throw e;
			  }

		  }catch( Throwable t ) {

			  if ( bind_failed && ( explicit_bind || NetworkAdmin.getSingleton().mustBind())){

				  	// force binding is set but failed, must bail here - can happen during VPN disconnect for
				  	// example, before we switch to a localhost bind

				  throw( t );

			  }else if(!ipv6problem){

				  //dont pass the exception outwards, so we will continue processing connection without advanced options set

				  String msg = "Error while processing advanced socket options (rcv=" + rcv_size + ", snd=" + snd_size + ", tos=" + ip_tos + ", port=" + local_bind_port + ", bind=" + bindIP + ")";
				  //Debug.out( msg, t );
				  Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, t));

			  }else{
				  	// can't support NIO + ipv6 on this system, pass on and don't raise an alert

				  throw( t );
			  }
		  }		  

		  request.channel.configureBlocking( false );
		  request.connect_start_time = SystemTime.getMonotonousTime();

		  PluginProxy plugin_proxy = request.plugin_proxy;
		  
		  if ( plugin_proxy != null ){
			  
			  plugin_proxy.addListener((pp)->{
				  
				 try{
					  new_canceled_mon.enter();
					  
					  pending_pp_attempts.remove( request );
					  					  
				 }finally{
						
					  new_canceled_mon.exit();
				  }  
			  });
		  }

		  
		  if ( request.channel.connect( request.address ) ) {  //already connected

			  if ( plugin_proxy != null ){
				  
				  try{
					  new_canceled_mon.enter();
	
					  pending_pp_attempts.put( request, null );
						  
				  }finally{
	
					  new_canceled_mon.exit();
				  }
			  }
			  
			  finishConnect( request );

		  }else{

			  //not yet connected, so register for connect selection

			  try{
				  new_canceled_mon.enter();

				  pending_attempts.put( request, null );

				  if ( plugin_proxy != null ){
					  
					  pending_pp_attempts.put( request, null );
				  }
			  }finally{

				  new_canceled_mon.exit();
			  }

			  connect_selector.register(
					  request.channel,
					  new VirtualChannelSelector.VirtualSelectorListener()
					  {
						  @Override
						  public boolean
						  selectSuccess(
								  VirtualChannelSelector 	selector,
								  SocketChannel 			sc,
								  Object 					attachment )
						  {
							  try{
								  new_canceled_mon.enter();

								  pending_attempts.remove( request );

							  }finally{

								  new_canceled_mon.exit();
							  }

							  finishConnect( request );

							  return true;
						  }

						  @Override
						  public void
						  selectFailure(
								  VirtualChannelSelector 	selector,
								  SocketChannel 			sc,
								  Object 					attachment,
								  Throwable 				msg )
						  {
							  try{
								  new_canceled_mon.enter();

								  pending_attempts.remove( request );

							  }finally{

								  new_canceled_mon.exit();
							  }

							  closeConnection( request.channel );

							  request.listener.connectFailure( msg );
						  }
					  }, null );
		  }
	  }catch( Throwable t ){

		  String full = request.address.toString();
		  String hostname = request.address.getHostName();
		  int port = request.address.getPort();
		  boolean unresolved = request.address.isUnresolved();
		  InetAddress	inet_address = request.address.getAddress();
		  String full_sub = inet_address==null?request.address.toString():inet_address.toString();
		  String host_address = inet_address==null?request.address.toString():inet_address.getHostAddress();

		  String msg = "ConnectDisconnectManager::address exception: full="+full+ ", hostname="+hostname+ ", port="+port+ ", unresolved="+unresolved+ ", full_sub="+full_sub+ ", host_address="+host_address;

		  if( request.channel != null ) {
			  String channel = request.channel.toString();
			  Socket socket = request.channel.socket();
			  String socket_string = socket.toString();
			  InetAddress local_address = socket.getLocalAddress();
			  String local_address_string = local_address == null ? "<null>" : local_address.toString();
			  int local_port = socket.getLocalPort();
			  SocketAddress ra = socket.getRemoteSocketAddress();
			  String remote_address;
			  if( ra != null )  remote_address = ra.toString();
			  else remote_address = "<null>";
			  int remote_port = socket.getPort();

			  msg += "\n channel="+channel+ ", socket="+socket_string+ ", local_address="+local_address_string+ ", local_port="+local_port+ ", remote_address="+remote_address+ ", remote_port="+remote_port;
		  }
		  else {
			  msg += "\n channel=<null>";
		  }

		  if (ipv6problem || t instanceof UnresolvedAddressException || t instanceof NoRouteToHostException ){

			  Logger.log(new LogEvent(LOGID,LogEvent.LT_WARNING,msg));

		  }else{

			  Logger.log(new LogEvent(LOGID,LogEvent.LT_ERROR,msg,t));
		  }

		  if( request.channel != null ){

			  closeConnection( request.channel );
		  }

		  request.listener.connectFailure( t );
	  }
  }




  void
  finishConnect(
	ConnectionRequest request )
  {
	  try {
		  if( request.channel.finishConnect() ) {

			  if( SHOW_CONNECT_STATS ) {
				  long queue_wait_time = request.connect_start_time - request.request_start_time;
				  long connect_time = SystemTime.getMonotonousTime() - request.connect_start_time;
				  int num_queued = new_requests.size();
				  int num_connecting = pending_attempts.size();
				  System.out.println("S: queue_wait_time="+queue_wait_time+
						  ", connect_time="+connect_time+
						  ", num_queued="+num_queued+
						  ", num_connecting="+num_connecting);
			  }

			  //ensure the request hasn't been canceled during the select op
			  boolean canceled = false;

			  try{  new_canceled_mon.enter();

			  	canceled = canceled_requests.contains( request.listener );

			  }finally{

				  new_canceled_mon.exit();
			  }

			  if( canceled ){

				  closeConnection( request.channel );

			  }else{

				  connect_selector.cancel( request.channel );

				  request.listener.connectSuccess( request.channel );
			  }
		  }else{

			  		//should never happen

			  Debug.out( "finishConnect() failed" );

			  request.listener.connectFailure( new Throwable( "finishConnect() failed" ) );

			  closeConnection( request.channel );
		  }
	  }catch( Throwable t ) {

		  if( SHOW_CONNECT_STATS ) {
			  long queue_wait_time = request.connect_start_time - request.request_start_time;
			  long connect_time = SystemTime.getMonotonousTime() - request.connect_start_time;
			  int num_queued = new_requests.size();
			  int num_connecting = pending_attempts.size();
			  System.out.println("F: queue_wait_time="+queue_wait_time+
					  ", connect_time="+connect_time+
					  ", num_queued="+num_queued+
					  ", num_connecting="+num_connecting);
		  }

		  request.listener.connectFailure( t );

		  closeConnection( request.channel );
	  }
  }



  void runSelect() {
    //do cancellations
    try{
      new_canceled_mon.enter();

      for (Iterator<ConnectListener> can_it = canceled_requests.iterator(); can_it.hasNext();){

        ConnectListener key =can_it.next();

        for (Iterator<ConnectionRequest> pen_it = pending_attempts.keySet().iterator(); pen_it.hasNext();) {

          ConnectionRequest request =pen_it.next();

          if ( request.listener == key ){

            connect_selector.cancel(request.channel);

            closeConnection(request.channel);

            pen_it.remove();

            break;
          }
        }
      }

      canceled_requests.clear();

    }finally{

      new_canceled_mon.exit();
    }

    	//run select

    try{
		if ( CONNECT_SELECT_LOOP_MIN_TIME > 0 ){

			long	start = SystemTime.getHighPrecisionCounter();

			connect_selector.select( CONNECT_SELECT_LOOP_TIME );

			long duration = SystemTime.getHighPrecisionCounter() - start;

			duration = duration/1000000;

			long	sleep = CONNECT_SELECT_LOOP_MIN_TIME - duration;

			if ( sleep > 0 ){

				try{
					Thread.sleep( sleep );

				}catch( Throwable e ){
				}
			}
		}else{
			connect_selector.select( CONNECT_SELECT_LOOP_TIME );
		}
    }
    catch( Throwable t ) {
      Debug.out("connnectSelectLoop() EXCEPTION: ", t);
    }

    //do connect attempt timeout checks
    int num_stalled_requests =0;

    final long now =SystemTime.getMonotonousTime();

    List<ConnectionRequest> timeouts = null;
    try{
        new_canceled_mon.enter();

	    for (Iterator<ConnectionRequest> i =pending_attempts.keySet().iterator(); i.hasNext();) {

	      final ConnectionRequest request =i.next();

	      final long waiting_time =now -request.connect_start_time;

	      if( waiting_time > request.connect_timeout ) {

	        i.remove();

	        SocketChannel channel = request.channel;

	        connect_selector.cancel( channel );

	        closeConnection( channel );

	        if ( timeouts == null ){

	        	timeouts = new ArrayList<>();
	        }

	        timeouts.add( request );

	      }else if( waiting_time >= CONNECT_ATTEMPT_STALL_TIME ) {

	        num_stalled_requests++;

	      }else if( waiting_time < 0 ) {  //time went backwards

	        request.connect_start_time =now;
	      }
	    }
	    
	    	// shouldn't really need to prune this as entries should always be removed, lol 
	    
	    for (Iterator<ConnectionRequest> i=pending_pp_attempts.keySet().iterator(); i.hasNext();) {

		      ConnectionRequest request =i.next();
		      
		      long waiting_time = now - request.connect_start_time;
		      
		      if ( waiting_time > 1*60*1000 ){
		    	
		    	  SocketChannel sc = request.channel;
		    	  
		    	  if ( sc == null || sc.socket().isClosed()){
		    		  
		    		  i.remove();
		    		  
		    	  }else if ( waiting_time > 3*60*1000 ){
		    	  
		    		  Debug.outNoStack( "Removing stale pending plugin-proxy record: " + request.address );
		    	  
		    		  i.remove();
		    	  }
		      }
	    }
    }finally{

        new_canceled_mon.exit();
    }

    if ( timeouts != null ){

    	for ( ConnectionRequest request: timeouts ){

    		InetSocketAddress	sock_address = request.address;

	       	InetAddress a = sock_address.getAddress();

	       	String	target;

	       	if ( a != null ){

	        	target = a.getHostAddress() + ":" + sock_address.getPort();

	        }else{

	        	target = sock_address.toString();
	        }

	        request.listener.connectFailure( new SocketTimeoutException( "Connection attempt to " + target + " aborted: timed out after " + request.connect_timeout/1000+ "sec" ) );
    	}
    }

    	//check if our connect queue is stalled, and expand if so
    
    if ( 	num_stalled_requests == pending_attempts.size() && 
    		pending_attempts.size() + pending_pp_attempts.size() < MAX_SIMULTANEOUS_CONNECT_ATTEMPTS){

    	ConnectionRequest cr =null;

    	try{
    		new_canceled_mon.enter();

    		if( !new_requests.isEmpty()){

    			Iterator<ConnectionRequest> it = new_requests.iterator();

    			cr = it.next();

    			it.remove();
    		}
    	}finally{
    		new_canceled_mon.exit();
    	}

    	if( cr != null ) {
    		addNewRequest( cr );
    	}
    }
  }


  void doClosings() {
    try{
    	pending_closes_mon.enter();

    	long	now = SystemTime.getMonotonousTime();

    	if ( delayed_closes.size() > 0 ){

    		Iterator<Map.Entry<SocketChannel,Long>>	it = delayed_closes.entrySet().iterator();

    		while( it.hasNext()){

    			Map.Entry<SocketChannel,Long>	entry = (Map.Entry<SocketChannel,Long>)it.next();

    			long	wait = ((Long)entry.getValue()).longValue() - now;

    			if ( wait < 0 || wait > 60*1000 ){

    				pending_closes.addLast( entry.getKey());

    				it.remove();
    			}
    		}
    	}

    	while( !pending_closes.isEmpty() ) {

    		SocketChannel channel = pending_closes.removeFirst();

    		if( channel != null ) {

    			connect_selector.cancel( channel );

    			try{
    				channel.close();

    			}catch( Throwable t ){

    				/*Debug.printStackTrace(t);*/
    			}
    		}
    	}
    }finally{

    	pending_closes_mon.exit();
    }
  }


  /**
   * Request that a new connection be made out to the given address.
   * @param address remote ip+port to connect to
   * @param listener to receive notification of connect attempt success/failure
   */
  public void 
  requestNewConnection( 
	  InetSocketAddress 	address,
	  PluginProxy			plugin_proxy,
	  ConnectListener 		listener, 
	  int 					priority ) 
  {
	  requestNewConnection( address, plugin_proxy, listener, CONNECT_ATTEMPT_TIMEOUT, priority  );
  }
  
  public void
  requestNewConnection(
	  InetSocketAddress 	address,
	  ConnectListener 		listener,
	  int					connect_timeout,
	  int 					priority )
  {
	  requestNewConnection( address, null, listener, connect_timeout, priority );
  }
  
  private void
  requestNewConnection(
	  InetSocketAddress 	address,
	  PluginProxy			plugin_proxy,
	  ConnectListener 		listener,
	  int					connect_timeout,
	  int 					priority )
  {
	  if ( address.getPort() == 0 ){

		  try{
			  listener.connectFailure( new Exception( "Invalid port, connection to " + address + " abandoned" ));

		  }catch( Throwable e ){

			  Debug.out( e );
		  }

		  return;
	  }

	  List<ConnectionRequest>	kicked 		= null;
	  boolean					duplicate	= false;

	  try{
		  new_canceled_mon.enter();

		  //insert at a random position because new connections are usually added in 50-peer
		  //chunks, i.e. from a tracker announce reply, and we want to evenly distribute the
		  //connect attempts if there are multiple torrents running

		  ConnectionRequest cr = new ConnectionRequest( connection_request_id_next++, address, plugin_proxy, listener, connect_timeout, priority );

		  	// this comparison is via Comparator and will weed out same address being added > once

		  if ( new_requests.contains( cr )){

			  duplicate = true;

		  }else{

			  new_requests.add( cr );

			  if ( new_requests.size() >= max_outbound_connections ){

				  if ( !max_conn_exceeded_logged ){

					  max_conn_exceeded_logged = true;

					  Debug.out( "TCPConnectionManager: max outbound connection limit reached (" + max_outbound_connections + ")" );
				  }
			  }

			  if ( priority == ProtocolEndpoint.CONNECT_PRIORITY_HIGHEST ){

				  for (Iterator<ConnectionRequest> pen_it = pending_attempts.keySet().iterator(); pen_it.hasNext();){

					  ConnectionRequest request =(ConnectionRequest) pen_it.next();

					  if ( request.priority == ProtocolEndpoint.CONNECT_PRIORITY_LOW ){

						  if ( !canceled_requests.contains( request.listener )){

							  canceled_requests.add( request.listener );

							  if ( kicked == null ){

								  kicked = new ArrayList<>();
							  }

							  kicked.add( request );
						  }
					  }
				  }
			  }
		  }
	  }finally{

		  new_canceled_mon.exit();
	  }

	  if ( duplicate ){

		  try{
			  listener.connectFailure( new Exception( "Connection request already queued for " + address ));

		  }catch( Throwable e ){

			  Debug.out( e );
		  }
	  }

	  if ( kicked != null ){

		  for (int i=0;i<kicked.size();i++){

			  try{
				  ((ConnectionRequest)kicked.get(i)).listener.connectFailure(
						 new Exception( "Low priority connection request abandoned in favour of high priority" ));

			  }catch( Throwable e ){

				  Debug.printStackTrace( e );
			  }
		  }
	  }
  }

  /**
   * Close the given connection.
   * @param channel to close
   */
  public void
  closeConnection(
	SocketChannel channel )
  {
	  closeConnection( channel, 0 );
  }

  public void
  closeConnection(
		SocketChannel channel,
		int delay )
  {
	  try{
		  pending_closes_mon.enter();

		  if ( delay == 0 ){

			  if ( !delayed_closes.containsKey( channel )){

				  if ( !pending_closes.contains( channel )){

					  pending_closes.addLast( channel );
				  }
			  }
		  }else{

			  delayed_closes.put( channel, new Long( SystemTime.getMonotonousTime() + delay ));
		  }
	  }finally{

		  pending_closes_mon.exit();
	  }
  }


  /**
   * Cancel a pending new connection request.
   * @param listener_key used in the initial connect request
   */
  public void cancelRequest( ConnectListener listener_key ) {
    try{
      new_canceled_mon.enter();

      //check if we can cancel it right away
      for( Iterator<ConnectionRequest> i = new_requests.iterator(); i.hasNext(); ) {
        ConnectionRequest request = i.next();
        if( request.listener == listener_key ) {
          i.remove();
          return;
        }
      }

      canceled_requests.add( listener_key ); //else add for later removal during select
    }
    finally{
      new_canceled_mon.exit();
    }
  }



  private static class
  ConnectionRequest
  {
    private final short		rand;
    private final long		id;

    private final InetSocketAddress address;
    private final PluginProxy		plugin_proxy;
    private final ConnectListener 	listener;
    private final long 				request_start_time;
    private final int				priority;
    	
   
    private long 			connect_start_time;
    private int 			connect_timeout;
    private SocketChannel 	channel;

    ConnectionRequest( long _id, InetSocketAddress _address, PluginProxy pp, ConnectListener _listener, int _connect_timeout, int _priority  ) {

      id					= _id;
      address 				= _address;
      plugin_proxy 			= pp;
      listener 				= _listener;
      request_start_time 	= SystemTime.getMonotonousTime();
      rand 					= (short)( RandomUtils.nextInt( Short.MAX_VALUE ));
      priority 				= _priority;

      setConnectTimeout( _connect_timeout );
    }

    int
    getConnectTimeout()
    {
    	return( connect_timeout );
    }

    void
    setConnectTimeout(
    	int		ct )
    {
        if ( ct < CONNECT_TIMEOUT_MIN ){
            
        	connect_timeout		= CONNECT_TIMEOUT_MIN;
      	  
        	if ( Constants.IS_CVS_VERSION ){
        		
        		Debug.out( "Connect timeout too small: " + ct );
        	}
        }else{
      	  
        	connect_timeout = ct;
        }
    }

    long
    getID()
    {
    	return( id );
    }

    int
    getPriority()
    {
    	return( priority );
    }

    short
    getRandom()
    {
    	return( rand );
    }
  }


///////////////////////////////////////////////////////////

  /**
   * Listener for notification of connection establishment.
   */
  public interface ConnectListener {
     /**
      * The connection establishment process has started,
      * i.e. the connection is actively being attempted.
      * @return adjusted connect timeout
      */
     public int connectAttemptStarted( int default_timeout );

     /**
      * The connection attempt succeeded.
      * @param channel connected socket channel
      */
     public void connectSuccess( SocketChannel channel ) ;


    /**
     * The connection attempt failed.
     * @param failure_msg failure reason
     */
    public void connectFailure( Throwable failure_msg );
    
    /**
     * See AEProxyFactory for properties
     * @param property_name
     * @return
     */
    
    public default Object
    getConnectionProperty(
    	String property_name )
    {
    	return( null );
    }
  }

/////////////////////////////////////////////////////////////

}
