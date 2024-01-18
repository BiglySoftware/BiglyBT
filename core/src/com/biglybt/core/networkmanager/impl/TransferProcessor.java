/*
 * Created on Oct 7, 2004
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

package com.biglybt.core.networkmanager.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.NetworkConnectionBase;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.RateHandler;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;




/**
 *
 */
public class
TransferProcessor
{
  private static final boolean RATE_LIMIT_LAN_TOO	= false;

  static boolean	RATE_LIMIT_UP_INCLUDES_PROTOCOL 	= false;
  static boolean	RATE_LIMIT_DOWN_INCLUDES_PROTOCOL 	= false;

  static{
	  if ( RATE_LIMIT_LAN_TOO ){

		  System.err.println( "**** TransferProcessor: RATE_LIMIT_LAN_TOO enabled ****" );
	  }

	  COConfigurationManager.addAndFireParameterListeners(
			  new String[]{
				  "Up Rate Limits Include Protocol",
				  "Down Rate Limits Include Protocol"
			  },
			  new ParameterListener() {

				@Override
				public void parameterChanged(String parameterName) {
					RATE_LIMIT_UP_INCLUDES_PROTOCOL = COConfigurationManager.getBooleanParameter( "Up Rate Limits Include Protocol" );
					RATE_LIMIT_DOWN_INCLUDES_PROTOCOL = COConfigurationManager.getBooleanParameter( "Down Rate Limits Include Protocol" );
				}
			});
  }

  public static final int TYPE_UPLOAD   = 0;
  public static final int TYPE_DOWNLOAD = 1;

  final int processor_type;
  final LimitedRateGroup max_rate;

  private final RateHandler	main_rate_handler;
  final ByteBucket main_bucket;
  private final EntityHandler main_controller;

  private final HashMap<LimitedRateGroup,GroupData> 			group_buckets 	= new HashMap<>();
  private final HashMap<NetworkConnectionBase,ConnectionData> 	connections 	= new HashMap<>();

  final AEMonitor connections_mon;

  private final boolean	multi_threaded;

  /**
   * Create new transfer processor for the given read/write type, limited to the given max rate.
   * @param processor_type read or write processor
   * @param max_rate_limit to use
   */
  public TransferProcessor( NetworkManager	net_man,  final int _processor_type, LimitedRateGroup max_rate_limit, boolean multi_threaded ) {
	this.processor_type = _processor_type;
    this.max_rate 		= max_rate_limit;
    this.multi_threaded	= multi_threaded;

    connections_mon = new AEMonitor( "TransferProcessor:" +processor_type );

    main_bucket = createBucket( max_rate.getRateLimitBytesPerSecond() );

    main_rate_handler =
    	new RateHandler()
    	{
    		final int pt = _processor_type;

    		@Override
		    public int[]
    		getCurrentNumBytesAllowed()
    		{
    			if ( main_bucket.getRate() != max_rate.getRateLimitBytesPerSecond() ) { //sync rate
    				main_bucket.setRate( max_rate.getRateLimitBytesPerSecond() );
    			}

    			int special;

    			if ( pt == TYPE_UPLOAD ){

    				if ( RATE_LIMIT_UP_INCLUDES_PROTOCOL ){

    					special = 0;

    				}else{

    					special = Integer.MAX_VALUE;
    				}
    			}else{

     				if ( RATE_LIMIT_DOWN_INCLUDES_PROTOCOL ){

    					special = 0;

    				}else{

    					special = Integer.MAX_VALUE;
    				}
    			}

    			return( new int[]{ main_bucket.getAvailableByteCount(), special });
    		}

    		@Override
		    public void
    		bytesProcessed( int data_bytes, int protocol_bytes )
    		{
    			//System.out.println( (pt == TYPE_UPLOAD?"Up":"Down") + ": " + data_bytes + "/" + protocol_bytes );

    			int num_bytes_written;

    			if ( pt == TYPE_UPLOAD ){

    				num_bytes_written = RATE_LIMIT_UP_INCLUDES_PROTOCOL?data_bytes + protocol_bytes:data_bytes;

    			}else{

       				num_bytes_written = RATE_LIMIT_DOWN_INCLUDES_PROTOCOL?data_bytes + protocol_bytes:data_bytes;
    			}

    			main_bucket.setBytesUsed( num_bytes_written );
    			max_rate.updateBytesUsed( num_bytes_written );
    		}
    	};

    main_controller = new EntityHandler( net_man, processor_type, main_rate_handler );
  }




  /**
   * Register peer connection for upload handling.
   * NOTE: The given max rate limit is ignored until the connection is upgraded.
   * @param connection to register
   * @param group rate limit group
   */
  public void registerPeerConnection( NetworkConnectionBase connection, boolean upload ) {
	  
	if ( connection.isClosed()){
		
		// happens sometimes when a connection is closed during the connect process
		// Debug.out( "Connection is closed: " + connection.getString());
		
		return;
	}
	
    final ConnectionData conn_data = new ConnectionData();

    try {  connections_mon.enter();

      LimitedRateGroup[]	groups = connection.getRateLimiters( upload );
      //do group registration
      GroupData[]	group_datas = new GroupData[groups.length];

      for (int i=0;i<groups.length;i++){
    	  LimitedRateGroup group = groups[i];

		  // boolean log = group.getName().contains("parg");

    	  GroupData group_data = (GroupData)group_buckets.get( group );
	      if( group_data == null ) {
	        int limit = NetworkManagerUtilities.getGroupRateLimit( group );
	        group_data = new GroupData( createBucket( limit ) );
	        group_buckets.put( group, group_data );

	        /*
	        if ( log ){
	    	  System.out.println( "Creating RL1: " + group.getName() + " -> " + group_data );
	        }
	        */
	      }
	      group_data.group_size++;

	      group_datas[i] = group_data;

	      /*
	      if ( log ){
	    	  System.out.println( "Applying RL1: " + group.getName() + " -> " + connection );
	      }
	      */
      }
      conn_data.groups = groups;
      conn_data.group_datas = group_datas;
      conn_data.state = ConnectionData.STATE_NORMAL;


      connections.put( connection, conn_data );
    }
    finally {  connections_mon.exit();  }

    main_controller.registerPeerConnection( connection );
  }

  public List<NetworkConnectionBase>
  getConnections()
  {
	  try{
		  connections_mon.enter();

		  return(new ArrayList<>(connections.keySet()));

	  }finally{

		  connections_mon.exit();
	  }
  }

  public boolean isRegistered( NetworkConnectionBase connection ){
    try{ connections_mon.enter();
      return( connections.containsKey( connection ));
    }
    finally{ connections_mon.exit(); }
  }

  /**
   * Cancel upload handling for the given peer connection.
   * @param connection to cancel
   */
  public boolean deregisterPeerConnection( NetworkConnectionBase connection ) {
    try{ connections_mon.enter();
      ConnectionData conn_data = (ConnectionData)connections.remove( connection );

      if( conn_data != null ) {

    	GroupData[]	group_datas = conn_data.group_datas;

  			//do groups de-registration

    	for (int i=0;i<group_datas.length;i++){

    		GroupData	group_data = group_datas[i];

    		if( group_data.group_size == 1 ) {  //last of the group

    			group_buckets.remove( conn_data.groups[i] ); //so remove

    		}else {

    			group_data.group_size--;
    		}
        }
      }
    }
    finally{ connections_mon.exit(); }


    return( main_controller.cancelPeerConnection( connection ));
  }

  public void
  setRateLimiterFreezeState(
		boolean	frozen )
  {
	  main_bucket.setFrozen( frozen );
  }

  public void
  addRateLimiter(
	NetworkConnectionBase 	connection,
	LimitedRateGroup		group )
  {
	  try{
		  connections_mon.enter();

	      ConnectionData conn_data = (ConnectionData)connections.get( connection );

	      if ( conn_data != null ){

			  LimitedRateGroup[]	groups 		= conn_data.groups;

			  for (int i=0;i<groups.length;i++){

				  if ( groups[i] == group ){

					  return;
				  }
			  }

			  // boolean log = group.getName().contains("parg");

	    	  GroupData group_data = (GroupData)group_buckets.get( group );

		      if ( group_data == null ){

		    	  int limit = NetworkManagerUtilities.getGroupRateLimit( group );

		    	  group_data = new GroupData( createBucket( limit ) );

		    	  /*
		    	  if ( log ){
		    		  System.out.println( "Creating RL2: " + group.getName() + " -> " + group_data );
		    	  }
				  */

		    	  group_buckets.put( group, group_data );
		      }

		      /*
		      if ( log ){
		    	  System.out.println( "Applying RL2: " + group.getName() + " -> " + connection );
		      }
			  */

		      group_data.group_size++;

			  GroupData[]			group_datas = conn_data.group_datas;

		      int	len = groups.length;

		      LimitedRateGroup[]	new_groups = new LimitedRateGroup[ len + 1 ];

		      System.arraycopy( groups, 0, new_groups, 0, len );
		      new_groups[len] = group;

		      conn_data.groups 		= new_groups;

		      GroupData[]	new_group_datas = new GroupData[ len + 1 ];

		      System.arraycopy( group_datas, 0, new_group_datas, 0, len );
		      new_group_datas[len] = group_data;

		      conn_data.group_datas = new_group_datas;
	      }
	  }finally{

		  connections_mon.exit();
	  }
  }

  public void
  removeRateLimiter(
	NetworkConnectionBase 	connection,
	LimitedRateGroup		group )
  {
	   try{
		   connections_mon.enter();

		   ConnectionData conn_data = (ConnectionData)connections.get( connection );

		   if ( conn_data != null ){

			   LimitedRateGroup[]	groups 		= conn_data.groups;
			   GroupData[]			group_datas = conn_data.group_datas;

			   int	len = groups.length;

			   if ( len == 0 ){

				   return;
			   }

			   LimitedRateGroup[]	new_groups 		= new LimitedRateGroup[ len - 1 ];
			   GroupData[]			new_group_datas = new GroupData[ len - 1 ];

			   int	pos = 0;

			   for (int i=0;i<groups.length;i++){

				   if ( groups[i] == group ){

					   GroupData	group_data = conn_data.group_datas[i];

					   if ( group_data.group_size == 1 ){  //last of the group

						   group_buckets.remove( conn_data.groups[i] ); //so remove

					   }else {

						   group_data.group_size--;
					   }
				   }else{

					   if ( pos == new_groups.length ){

						   return;
					   }

					   new_groups[pos]		= groups[i];
					   new_group_datas[pos]	= group_datas[i];

					   pos++;
				   }
			   }

			   conn_data.groups 		= new_groups;
			   conn_data.group_datas 	= new_group_datas;
		   }
	   }finally{

		   connections_mon.exit();
	   }
  }


  // private static long last_log = 0;

  /**
   * Upgrade the given connection to a high-speed transfer handler.
   * @param connection to upgrade
   */
  public void
  upgradePeerConnection(
	final NetworkConnectionBase 	connection,
	int 							partition_id )
  {
	  if ( connection.isClosed()){

		  // Debug.out( "Connection is closed" );

		  return;
	  }
		
    ConnectionData connection_data = null;

    try{
    	connections_mon.enter();

    	connection_data = (ConnectionData)connections.get( connection );

    }finally{

    	connections_mon.exit();
    }

    if ( connection_data != null && connection_data.state == ConnectionData.STATE_NORMAL ){

      final ConnectionData conn_data = connection_data;

      main_controller.upgradePeerConnection(
    		connection,
    		new RateHandler()
    		{
    	   		final int pt = processor_type;

    			@Override
			    public int[]
    			getCurrentNumBytesAllowed()
    			{
    	   			int special;

        			if ( pt == TYPE_UPLOAD ){

        				if ( RATE_LIMIT_UP_INCLUDES_PROTOCOL ){

        					special = 0;

        				}else{

        					special = Integer.MAX_VALUE;
        				}
        			}else{

         				if ( RATE_LIMIT_DOWN_INCLUDES_PROTOCOL ){

        					special = 0;

        				}else{

        					special = Integer.MAX_VALUE;
        				}
        			}

    					// sync global rate

    				if ( main_bucket.getRate() != max_rate.getRateLimitBytesPerSecond() ) {

    					main_bucket.setRate( max_rate.getRateLimitBytesPerSecond() );
    				}

    				int allowed = main_bucket.getAvailableByteCount();

    					// reserve bandwidth for the general pool

    				allowed -= connection.getMssSize();

    				if ( allowed < 0 )allowed = 0;

	    				// only apply group rates to non-lan local connections

	    				// ******* READ ME *******
	    				// If you ever come here looking for an explanation as to why on torrent startup some peers
	    				// appear to be ignoring rate limits for the first few pieces of a download
	    				// REMEMBER that fast-start extension pieces are downloaded while be peer is choking us and hence
	    				// in a non-upgraded state WHICH MEANS that rate limits are NOT APPLIED


    				if ( RATE_LIMIT_LAN_TOO || !( connection.isLANLocal() && NetworkManager.isLANRateEnabled())){

    						// sync group rates

    					LimitedRateGroup[]	groups 		= conn_data.groups;
    					GroupData[]			group_datas = conn_data.group_datas;

    					if ( groups.length != group_datas.length ){
    							// yeah, I know....
    						try{
    							connections_mon.enter();

    							groups 		= conn_data.groups;
    							group_datas	= conn_data.group_datas;
    						}finally{
    							connections_mon.exit();
    						}
    					}

    					try{
    						for (int i=0;i<group_datas.length;i++){

    							//boolean log = group.getName().contains("parg");

    							int group_rate = NetworkManagerUtilities.getGroupRateLimit(  groups[i] );

    							ByteBucket group_bucket = group_datas[i].bucket;

    							/*
						          if ( log ){
						        	  long now = SystemTime.getCurrentTime();
						        	  if ( now - last_log > 500 ){
						        		  last_log = now;
						        		  System.out.println( "    " + group.getName() + " -> " + group_rate + "/" + group_bucket.getAvailableByteCount());
						        	  }
						          }
    							 */

    							if ( group_bucket.getRate() != group_rate ){

    								group_bucket.setRate( group_rate );
    							}

    							int 	group_allowed = group_bucket.getAvailableByteCount();

    							if ( group_allowed < allowed ){

    								allowed = group_allowed;
    							}
    						}
    					}catch( Throwable e ){

    						// conn_data.group stuff is not synchronized for speed but can cause borkage if new
    						// limiters added so trap here

    						if (!( e instanceof IndexOutOfBoundsException )){

    							Debug.printStackTrace(e);
    						}
    					}
    				}

    				return( new int[]{ allowed, special });
    			}

    			@Override
			    public void
    			bytesProcessed(
    				int data_bytes,
    				int protocol_bytes )
    			{
        			//System.out.println( (pt == TYPE_UPLOAD?"Up":"Down") + ": " + data_bytes + "/" + protocol_bytes );

    				int num_bytes_written;

        			if ( pt == TYPE_UPLOAD ){

        				num_bytes_written = RATE_LIMIT_UP_INCLUDES_PROTOCOL?data_bytes + protocol_bytes:data_bytes;

        			}else{

           				num_bytes_written = RATE_LIMIT_DOWN_INCLUDES_PROTOCOL?data_bytes + protocol_bytes:data_bytes;
        			}

    				if ( RATE_LIMIT_LAN_TOO || !( connection.isLANLocal() && NetworkManager.isLANRateEnabled())){

      					LimitedRateGroup[]	groups 		= conn_data.groups;
    					GroupData[]			group_datas = conn_data.group_datas;

    					if ( groups.length != group_datas.length ){
    							// yeah, I know....
    						try{
    							connections_mon.enter();

    							groups 		= conn_data.groups;
    							group_datas	= conn_data.group_datas;
    						}finally{
    							connections_mon.exit();
    						}
    					}

    					for (int i=0;i<group_datas.length;i++){

    						group_datas[i].bucket.setBytesUsed( num_bytes_written );

    						groups[i].updateBytesUsed( num_bytes_written );
    					}
    				}

    				main_bucket.setBytesUsed( num_bytes_written );
    			}
    		},
    		partition_id );

      conn_data.state = ConnectionData.STATE_UPGRADED;
    }
  }


  /**
   * Downgrade the given connection back to a normal-speed transfer handler.
   * @param connection to downgrade
   */
  public void downgradePeerConnection( NetworkConnectionBase connection ) {
    ConnectionData conn_data = null;

    try{ connections_mon.enter();
      conn_data = (ConnectionData)connections.get( connection );
    }
    finally{ connections_mon.exit(); }

    if( conn_data != null && conn_data.state == ConnectionData.STATE_UPGRADED ) {
      main_controller.downgradePeerConnection( connection );
      conn_data.state = ConnectionData.STATE_NORMAL;
    }
  }

  public RateHandler
  getRateHandler()
  {
	  return( main_rate_handler );
  }

  public RateHandler
  getRateHandler(
	NetworkConnectionBase	connection )
  {
	return( main_controller.getRateHandler( connection ));
  }

  private ByteBucket
  createBucket(
	int	bytes_per_sec )
  {
	  if ( multi_threaded ){

		  return( new ByteBucketMT( bytes_per_sec ));

	  }else{

		  return( new ByteBucketST( bytes_per_sec ));
	  }
  }

  private static class ConnectionData {
    private static final int STATE_NORMAL   = 0;
    private static final int STATE_UPGRADED = 1;

    int state;
    LimitedRateGroup[] groups;
    GroupData[] group_datas;
  }


  private static class GroupData {
    final ByteBucket bucket;
    int group_size = 0;

    GroupData( ByteBucket bucket ) {
      this.bucket = bucket;
    }
  }


}
