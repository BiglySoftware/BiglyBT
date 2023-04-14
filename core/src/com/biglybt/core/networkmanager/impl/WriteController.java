/*
 * Created on Sep 27, 2004
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

import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.networkmanager.EventWaiter;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.util.*;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;


/**
 * Processes writes of write-entities and handles the write selector.
 */
public class WriteController implements CoreStatsProvider, AEDiagnosticsEvidenceGenerator {

	static int 		IDLE_SLEEP_TIME  	= 50;
	static boolean	AGGRESIVE_WRITE		= false;
	static int		BOOSTER_GIFT 		= 5*1024;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"network.control.write.idle.time",
				"network.control.write.aggressive",
				"Bias Upload Slack KBs",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					IDLE_SLEEP_TIME 	= COConfigurationManager.getIntParameter( "network.control.write.idle.time" );
					AGGRESIVE_WRITE		= COConfigurationManager.getBooleanParameter( "network.control.write.aggressive" );
					BOOSTER_GIFT 		= COConfigurationManager.getIntParameter( "Bias Upload Slack KBs" )*1024;
				}
			});
	}

  volatile ArrayList<RateControlledEntity> normal_priority_entities = new ArrayList<>();  //copied-on-write
  volatile ArrayList<RateControlledEntity> boosted_priority_entities = new ArrayList<>();  //copied-on-write
  volatile ArrayList<RateControlledEntity> high_priority_entities = new ArrayList<>();  //copied-on-write
  private final AEMonitor entities_mon = new AEMonitor( "WriteController:EM" );
  private int next_normal_position = 0;
  private int next_boost_position = 0;
  private int next_high_position = 0;


  private long	booster_process_time;
  private int	booster_normal_written;
  private int	booster_boost_written;
  private int	booster_stat_index;
  private final int[]	booster_normal_writes 	= new int[5];
  private final int[]	booster_gifts 			= new int[5];

  private MovingImmediateAverage	booster_boost_average 	= AverageFactory.MovingImmediateAverage( 5 );
  private MovingImmediateAverage	booster_normal_average 	= AverageFactory.MovingImmediateAverage( 5 );
  private MovingImmediateAverage	booster_boost_avail_average 	= AverageFactory.MovingImmediateAverage( 5 );
  private MovingImmediateAverage	booster_normal_avail_average 	= AverageFactory.MovingImmediateAverage( 5 );
  private MovingImmediateAverage	booster_boost_data_average 	= AverageFactory.MovingImmediateAverage( 5 );
  private MovingImmediateAverage	booster_normal_data_average 	= AverageFactory.MovingImmediateAverage( 5 );

  private int aggressive_np_normal_priority_count;
  private int aggressive_np_high_priority_count;

  private long	process_loop_time;
  private long	wait_count;
  private long	progress_count;
  private long	non_progress_count;

  private final EventWaiter 	write_waiter = new EventWaiter();

  private NetworkManager	net_man;

  private int	entity_count = 0;

  /**
   * Create a new write controller.
   */
  public WriteController() {

    //start write handler processing
    AEThread2 write_processor_thread = new AEThread2( "WriteController:WriteProcessor" ) {
      @Override
      public void run() {
        writeProcessorLoop();
      }
    };
 
    write_processor_thread.setPriority( Thread.MAX_PRIORITY - 1 );
    write_processor_thread.start();

    Set	types = new HashSet();

    types.add( CoreStats.ST_NET_WRITE_CONTROL_WAIT_COUNT );
    types.add( CoreStats.ST_NET_WRITE_CONTROL_NP_COUNT );
    types.add( CoreStats.ST_NET_WRITE_CONTROL_P_COUNT );
    types.add( CoreStats.ST_NET_WRITE_CONTROL_ENTITY_COUNT );
    types.add( CoreStats.ST_NET_WRITE_CONTROL_CON_COUNT );
    types.add( CoreStats.ST_NET_WRITE_CONTROL_READY_CON_COUNT );
    types.add( CoreStats.ST_NET_WRITE_CONTROL_READY_BYTE_COUNT );

    CoreStats.registerProvider(
    	types,
    	this );

    AEDiagnostics.addWeakEvidenceGenerator(this);
  }

  public String
  getBiasDetails()
  {
	  if ( boosted_priority_entities.size() == 0 ){
		  
		  return( "" );
		  
	  }else{
		  
		  return(
			"n=" + normal_priority_entities.size()+" "+
				DisplayFormatters.formatByteCountToKiBEtc((long)booster_normal_data_average.getAverage()) + "," +
				DisplayFormatters.formatByteCountToKiBEtcPerSec((long)booster_normal_average.getAverage()) + "," +
				DisplayFormatters.formatByteCountToKiBEtcPerSec((long)booster_normal_avail_average.getAverage()) + ";" +
			"b=" + boosted_priority_entities.size()+" "+
				DisplayFormatters.formatByteCountToKiBEtc((long)booster_boost_data_average.getAverage()) + "," +
				DisplayFormatters.formatByteCountToKiBEtcPerSec((long)booster_boost_average.getAverage()) + "," +
				DisplayFormatters.formatByteCountToKiBEtcPerSec((long)booster_boost_avail_average.getAverage()) + ";" +
			"h=" + high_priority_entities.size());
	  }
  }
  
	@Override
	public void
	generate(
			IndentWriter writer )
	{
		writer.println( "Write Controller" );

		try{
			writer.indent();

			ArrayList ref = normal_priority_entities;

			writer.println( "normal - " + ref.size());

			for (int i=0;i<ref.size();i++){

				RateControlledEntity entity = (RateControlledEntity)ref.get( i );

				writer.println( entity.getString());
			}

			ref = boosted_priority_entities;

			writer.println( "boosted - " + ref.size());

			for (int i=0;i<ref.size();i++){

				RateControlledEntity entity = (RateControlledEntity)ref.get( i );

				writer.println( entity.getString());
			}

			ref = high_priority_entities;

			writer.println( "priority - " + ref.size());

			for (int i=0;i<ref.size();i++){

				RateControlledEntity entity = (RateControlledEntity)ref.get( i );

				writer.println( entity.getString());
			}
		}finally{

			writer.exdent();
		}
	}

	@Override
  public void
  updateStats(
		  Set		types,
		  Map		values )
  {
	  if ( types.contains( CoreStats.ST_NET_WRITE_CONTROL_WAIT_COUNT )){

		  values.put( CoreStats.ST_NET_WRITE_CONTROL_WAIT_COUNT, new Long( wait_count ));
	  }

	  if ( types.contains( CoreStats.ST_NET_WRITE_CONTROL_NP_COUNT )){

		  values.put( CoreStats.ST_NET_WRITE_CONTROL_NP_COUNT, new Long( non_progress_count ));
	  }

	  if ( types.contains( CoreStats.ST_NET_WRITE_CONTROL_P_COUNT )){

		  values.put( CoreStats.ST_NET_WRITE_CONTROL_P_COUNT, new Long( progress_count ));
	  }

	  if ( types.contains( CoreStats.ST_NET_WRITE_CONTROL_ENTITY_COUNT )){

		  values.put( CoreStats.ST_NET_WRITE_CONTROL_ENTITY_COUNT, new Long( high_priority_entities.size() + boosted_priority_entities.size() + normal_priority_entities.size()));
	  }

	  if ( 	types.contains( CoreStats.ST_NET_WRITE_CONTROL_CON_COUNT ) ||
			types.contains( CoreStats.ST_NET_WRITE_CONTROL_READY_CON_COUNT ) ||
			types.contains( CoreStats.ST_NET_WRITE_CONTROL_READY_BYTE_COUNT )){

		  long	ready_bytes			= 0;
		  int	ready_connections	= 0;
		  int	connections			= 0;

		  ArrayList[] refs = { normal_priority_entities, boosted_priority_entities, high_priority_entities };

		  for (int i=0;i<refs.length;i++){

			  ArrayList	ref = refs[i];

			  for (int j=0;j<ref.size();j++){

			      RateControlledEntity entity = (RateControlledEntity)ref.get( j );

			      connections 		+= entity.getConnectionCount( write_waiter );

			      ready_connections += entity.getReadyConnectionCount( write_waiter );

			      ready_bytes		+= entity.getBytesReadyToWrite();
			  }
		  }

		  values.put( CoreStats.ST_NET_WRITE_CONTROL_CON_COUNT, new Long( connections ));
		  values.put( CoreStats.ST_NET_WRITE_CONTROL_READY_CON_COUNT, new Long( ready_connections ));
		  values.put( CoreStats.ST_NET_WRITE_CONTROL_READY_BYTE_COUNT, new Long( ready_bytes ));
	  }
  }

  void writeProcessorLoop() {
    boolean check_high_first = true;

    long	last_check = SystemTime.getMonotonousTime();

    net_man = NetworkManager.getSingleton();

    int	tick_count = 0;
    
    while( true ) {

      process_loop_time = SystemTime.getMonotonousTime();

      tick_count++;
      
      try {
        if( check_high_first ) {
          check_high_first = false;
          if( !doHighPriorityWrite() ) {
            if( !doNormalPriorityWrite( tick_count ) ) {
              if ( write_waiter.waitForEvent( hasConnections()?IDLE_SLEEP_TIME:1000 )){
            	  wait_count++;
              }
            }
          }
        }
        else {
          check_high_first = true;
          if( !doNormalPriorityWrite( tick_count ) ) {
            if( !doHighPriorityWrite() ) {
            	if ( write_waiter.waitForEvent( hasConnections()?IDLE_SLEEP_TIME:1000 )){
            		wait_count++;
            	}
            }
          }
        }
      }catch( Throwable t ){
    	  
        Debug.out( "writeProcessorLoop() EXCEPTION: ", t );
      }

      if ( process_loop_time - last_check > 5000 ){

    	  last_check = process_loop_time;

    	  boolean	changed = false;

    	  ArrayList<RateControlledEntity> ref = normal_priority_entities;

    	  for ( RateControlledEntity e: ref ){

    		 if ( e.getPriorityBoost()){

    			 changed = true;

    			 break;
    		 }
    	  }

    	  if ( !changed ){

    	  	 ref = boosted_priority_entities;

        	 for ( RateControlledEntity e: ref ){

        		 if ( !e.getPriorityBoost()){

        			 changed = true;

        			 break;
        		 }
        	  }
    	  }

    	  if ( changed ){

 		    try{
 		    	entities_mon.enter();

 		    	ArrayList<RateControlledEntity> new_normal 	= new ArrayList<>();
 		    	ArrayList<RateControlledEntity> new_boosted = new ArrayList<>();

 		    	for ( RateControlledEntity e: normal_priority_entities ){

 		    		if ( e.getPriorityBoost()){

 		    			new_boosted.add( e );

 		    		}else{

 		    			new_normal.add( e );
 		    		}
 		    	}

		    	for ( RateControlledEntity e: boosted_priority_entities ){

 		    		if ( e.getPriorityBoost()){

 		    			new_boosted.add( e );

 		    		}else{

 		    			new_normal.add( e );
 		    		}
 		    	}

		    	normal_priority_entities 	= new_normal;
		    	boosted_priority_entities	= new_boosted;

 		    }finally{

 		    	entities_mon.exit();
 		    }
    	  }
      }
    }
  }

  private boolean
  hasConnections()
  {
	  if ( entity_count == 0 ){

		  return( false );
	  }

	  List<RateControlledEntity> ref = high_priority_entities;

	  for ( RateControlledEntity e: ref ){

		  if ( e.getConnectionCount( write_waiter ) > 0 ){

			  return( true );
		  }
	  }

	  ref = boosted_priority_entities;

	  for ( RateControlledEntity e: ref ){

		  if ( e.getConnectionCount( write_waiter ) > 0 ){

			  return( true );
		  }
	  }

	  ref = normal_priority_entities;

	  for ( RateControlledEntity e: ref ){

		  if ( e.getConnectionCount( write_waiter ) > 0 ){

			  return( true );
		  }
	  }

	  return( false );
  }

  private boolean
  doNormalPriorityWrite(
	 int tick_count )
  {
    int result = processNextReadyNormalPriorityEntity( tick_count );

    if ( result > 0 ){

    	progress_count++;

    	return true;

    }else if ( result == 0 ){

    	non_progress_count++;

    	if ( AGGRESIVE_WRITE ){

    		aggressive_np_normal_priority_count++;

    		if ( aggressive_np_normal_priority_count < ( normal_priority_entities.size() + boosted_priority_entities.size())){

    			return( true );

    		}else{

    			aggressive_np_normal_priority_count = 0;
    		}
    	}
    }

    return false;
  }

  private boolean doHighPriorityWrite() {
    RateControlledEntity ready_entity = getNextReadyHighPriorityEntity();
    if( ready_entity != null ){
    	if ( ready_entity.doProcessing( write_waiter, 0 ) > 0 ) {

    		progress_count++;

    		return true;

    	}else{

    		non_progress_count++;

    		if ( AGGRESIVE_WRITE ){

    			aggressive_np_high_priority_count++;

    			if ( aggressive_np_high_priority_count < high_priority_entities.size()){

    				return( true );

    			}else{

    				aggressive_np_high_priority_count = 0;
    			}
    		}
    	}
    }
    return false;
  }


  private int
  processNextReadyNormalPriorityEntity(
		int	tick_count )
  {
	  ArrayList<RateControlledEntity> boosted_ref 	= boosted_priority_entities;
	  ArrayList<RateControlledEntity> normal_ref	= normal_priority_entities;

	  final int boosted_size 	= boosted_ref.size();
	  final int	normal_size		= normal_ref.size();
	  
	  boolean	frozen = false;
	  
	  boolean do_boosting = boosted_size > 0;
	  
	  /*
	   * This attempt to not impact on non-boosted uploaders so much fails as they still manage to swamp lonely
	   * boosted uploaders :(
	   
	  if ( do_boosting ){
		  
		  if ( normal_size > 0 ){
			  
			  int ratio = boosted_size / normal_size;
			  
			  if ( ratio > 5 ){
				  
				  ratio = 5;
				  
			  }else if ( ratio < 2 ){
				  
				  ratio = 2;
			  }
			  
			  if ( tick_count % ratio == 0 ){
				  
				  do_boosting = false;
			  }
		  }
	  }
	  */
	  
	  try{
		  if ( do_boosting ){

			  if ( process_loop_time - booster_process_time >= 1000 ){

				  booster_process_time = process_loop_time;

				  booster_gifts[ booster_stat_index ] 			= BOOSTER_GIFT;
				  booster_normal_writes[ booster_stat_index]	= booster_normal_written;

				  booster_stat_index++;

				  if ( booster_stat_index >= booster_gifts.length ){

					  booster_stat_index = 0;
				  }

				  booster_boost_average.update(booster_boost_written);
				  booster_normal_average.update(booster_normal_written);
				  
				  booster_normal_written 	= 0;
				  booster_boost_written		= 0;
				  
				  int max_normal 	= 0;
				  int normal_data	= 0;
				  
				  for ( RateControlledEntity e: normal_ref ){
					  
					  if ( e.canProcess( write_waiter )){
						  
						  normal_data += e.getBytesReadyToWrite();
						  
						  int max = e.getRateHandler().getCurrentNumBytesAllowed()[0];
						  
						  if ( max > max_normal ){
							  
							  max_normal = max;
						  }
					  }
				  }
				  
				  booster_normal_data_average.update( normal_data );
				  booster_normal_avail_average.update( max_normal );
				  
				  int max_booster 	= 0;
				  int booster_data	= 0;
				  
				  for ( RateControlledEntity e: boosted_ref ){
					  
					  if ( e.canProcess( write_waiter )){
						  
						  booster_data += e.getBytesReadyToWrite();
						  
						  int max = e.getRateHandler().getCurrentNumBytesAllowed()[0];
						  
						  if ( max > max_booster ){
							  
							  max_booster = max;
						  }
					  }
				  }
				  
				  booster_boost_data_average.update( booster_data );
				  booster_boost_avail_average.update( max_booster );
			  }
		  }
		  
		  if ( do_boosting && booster_boost_data_average.getAverage() == 0 ){
			
			  	// no data queued for boosted peers on average so don't bother attempting to
			  	// do anything. crank through one just to keep things turning over
			  
			  next_boost_position = next_boost_position >= boosted_size ? 0 : next_boost_position;  //make circular
			  
			  RateControlledEntity entity = boosted_ref.get( next_boost_position );
			  
			  next_boost_position++;
			  
			  if ( entity.canProcess( write_waiter )){ 
				  
				  int boosted = entity.doProcessing( write_waiter, 0 );
				  
				  if ( boosted > 0 ){
					  
					  booster_boost_written += boosted;
				  }
			  }
			  
			  do_boosting = false;
		  }
		  
		  if ( do_boosting ){

			  int	total_gifts 		= 0;
			  int	total_normal_writes	= booster_normal_written;	// current accumulated normal writes

			  for (int i=0;i<booster_gifts.length;i++){

				  total_gifts			+= booster_gifts[i];
				  total_normal_writes 	+= booster_normal_writes[i];
			  }
			  
			  int	effective_gift = total_gifts - total_normal_writes;

			  if ( effective_gift > 0 ){

				  int num_checked = 0;

				  int gift_remaining = effective_gift;
				  
				  while( num_checked < normal_size && gift_remaining > 0 ) {
					  next_normal_position = next_normal_position >= normal_size ? 0 : next_normal_position;  //make circular
					  RateControlledEntity entity = (RateControlledEntity)normal_ref.get( next_normal_position );
					  next_normal_position++;
					  num_checked++;
					  if ( entity.canProcess( write_waiter )){

						  int gift_used = entity.doProcessing( write_waiter, gift_remaining );

						  if ( gift_used > 0 ){
							  
							  //  System.out.println( "gifted: " + gift_used );
							  
							  booster_normal_written += gift_used;

							  gift_remaining -= gift_used;
						  }
					  }
				  }
				  
				  int gift_used = effective_gift - gift_remaining;
				  
				  if ( gift_used > 0 ){
	
					  for ( int i=booster_stat_index; gift_used > 0 && i<booster_stat_index+booster_gifts.length; i++){

						  int	avail = booster_gifts[i%booster_gifts.length];

						  if ( avail > 0 ){

							  int	temp = Math.min( avail, gift_used );

							  avail 	-= temp;
							  gift_used -= temp;

							  booster_gifts[i%booster_gifts.length] = avail;
						  }
					  }
				  }
			  }

			  int num_checked = 0;

			  while( num_checked < boosted_size ) {
				  next_boost_position = next_boost_position >= boosted_size ? 0 : next_boost_position;  //make circular
				  RateControlledEntity entity = boosted_ref.get( next_boost_position );
				  next_boost_position++;
				  num_checked++;
				  if( entity.canProcess( write_waiter ) ) {  //is ready
					  int boosted = entity.doProcessing( write_waiter, 0 );
					  	
					  	// if no progress is made we give others a chance otherwise this non-progress
					  	// can prevent us from ever getting onto the normal entities below
					  
					  if ( boosted > 0 ){
						  //  System.out.println( "boosted: " + boosted );
						  
						  booster_boost_written += boosted;
						  
						  return( boosted );
					  }
				  }
			  }

			  	// give remaining normal peers a chance to use the bandwidth boosted peers couldn't, but prevent
			  	// more from being allocated while doing so to prevent them from grabbing more than they should

			  frozen = true;
			  
			  net_man.getUploadProcessor().setRateLimiterFreezeState( true );

		  }else{

			  booster_normal_written 	= 0;
			  booster_boost_written		= 0;
		  }

		  int num_checked = 0;
		  
		  while( num_checked < normal_size ) {
			  next_normal_position = next_normal_position >= normal_size ? 0 : next_normal_position;  //make circular
			  RateControlledEntity entity = (RateControlledEntity)normal_ref.get( next_normal_position );
			  next_normal_position++;
			  num_checked++;
			  if( entity.canProcess( write_waiter ) ) {  //is ready
				  int bytes = entity.doProcessing( write_waiter, 0 );

				  if ( bytes > 0 ){

					  booster_normal_written += bytes;
				  }
				  
				  return( bytes );
			  }
		  }

		  return( -1 );

	  }finally{

		  if ( frozen ){

			  net_man.getUploadProcessor().setRateLimiterFreezeState( false );
		  }
	  }
  }


  private RateControlledEntity getNextReadyHighPriorityEntity() {
    ArrayList ref = high_priority_entities;

    int size = ref.size();
    int num_checked = 0;

    while( num_checked < size ) {
      next_high_position = next_high_position >= size ? 0 : next_high_position;  //make circular
      RateControlledEntity entity = (RateControlledEntity)ref.get( next_high_position );
      next_high_position++;
      num_checked++;
      if( entity.canProcess( write_waiter ) ) {  //is ready
        return entity;
      }
    }

    return null;  //none found ready
  }



  /**
   * Add the given entity to the controller for write processing.
   * @param entity to process writes for
   */
  public void addWriteEntity( RateControlledEntity entity ) {
    try {  entities_mon.enter();
      if( entity.getPriority() == RateControlledEntity.PRIORITY_HIGH ) {
        //copy-on-write
        ArrayList high_new = new ArrayList( high_priority_entities.size() + 1 );
        high_new.addAll( high_priority_entities );
        high_new.add( entity );
        high_priority_entities = high_new;
      }
      else {
        if ( entity.getPriorityBoost()){
	        ArrayList boost_new = new ArrayList( boosted_priority_entities.size() + 1 );
	        boost_new.addAll( boosted_priority_entities );
	        boost_new.add( entity );
	        boosted_priority_entities = boost_new;
        }else{
	        ArrayList norm_new = new ArrayList( normal_priority_entities.size() + 1 );
	        norm_new.addAll( normal_priority_entities );
	        norm_new.add( entity );
	        normal_priority_entities = norm_new;
        }
      }

      entity_count = normal_priority_entities.size() + boosted_priority_entities.size() + high_priority_entities.size();
    }
    finally {  entities_mon.exit();  }

    write_waiter.eventOccurred();
  }


  /**
   * Remove the given entity from the controller.
   * @param entity to remove from write processing
   */
  public boolean removeWriteEntity( RateControlledEntity entity ) {
	boolean found = false;
    try {  entities_mon.enter();
      if( entity.getPriority() == RateControlledEntity.PRIORITY_HIGH ) {
        //copy-on-write
        ArrayList high_new = new ArrayList( high_priority_entities );
        if ( high_new.remove( entity )){
        	high_priority_entities = high_new;
        	
        	found = true;
        }else{
        	Debug.out( "entity not found" );
        }
      }
      else {
        //copy-on-write
    	if ( boosted_priority_entities.contains( entity )){
	        ArrayList boosted_new = new ArrayList( boosted_priority_entities );
	        boosted_new.remove( entity );
	        boosted_priority_entities = boosted_new;
	        
	        found = true;
    	}else{
	        ArrayList norm_new = new ArrayList( normal_priority_entities );
	        if ( norm_new.remove( entity )){
	        	normal_priority_entities = norm_new;
	        	
	        	found = true;
	        }else{
	        	Debug.out( "entity not found" );
	        }
    	}
      }

      entity_count = normal_priority_entities.size() + boosted_priority_entities.size() + high_priority_entities.size();
    }
    finally {  entities_mon.exit();  }
    
    return( found );
  }

  public int
  getEntityCount()
  {
	  return( entity_count );
  }
}
