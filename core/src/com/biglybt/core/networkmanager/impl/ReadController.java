/*
 * Created on Oct 16, 2004
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
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.util.*;



/**
 * Processes reads of read-entities and handles the read selector.
 */
public class ReadController implements CoreStatsProvider, AEDiagnosticsEvidenceGenerator {

	static int 		IDLE_SLEEP_TIME  	= 50;
	static boolean	AGGRESIVE_READ		= false;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"network.control.read.idle.time",
				"network.control.read.aggressive",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					IDLE_SLEEP_TIME 	= COConfigurationManager.getIntParameter( "network.control.read.idle.time" );
					AGGRESIVE_READ		= COConfigurationManager.getBooleanParameter( "network.control.read.aggressive" );
				}
			});
	}

  volatile ArrayList<RateControlledEntity> normal_priority_entities = new ArrayList<>();  //copied-on-write
  volatile ArrayList<RateControlledEntity> high_priority_entities 	= new ArrayList<>();  //copied-on-write
  private final AEMonitor entities_mon = new AEMonitor( "ReadController:EM" );
  private int next_normal_position = 0;
  private int next_high_position = 0;

  private long	loop_count;
  private long	wait_count;
  private long	non_progress_count;
  private long	progress_count;

  private long	entity_check_count;
  private long	last_entity_check_count;

  private final EventWaiter 	read_waiter = new EventWaiter();

  private int			entity_count;

  public ReadController() {

    //start read handler processing
    AEThread2 read_processor_thread = new AEThread2( "ReadController:ReadProcessor" ) {
      @Override
      public void run() {
        readProcessorLoop();
      }
    };
    
    read_processor_thread.setPriority( Thread.MAX_PRIORITY - 1 );
    read_processor_thread.start();

    Set	types = new HashSet();

    types.add( CoreStats.ST_NET_READ_CONTROL_LOOP_COUNT );
    types.add( CoreStats.ST_NET_READ_CONTROL_NP_COUNT );
    types.add( CoreStats.ST_NET_READ_CONTROL_P_COUNT );
    types.add( CoreStats.ST_NET_READ_CONTROL_WAIT_COUNT );
    types.add( CoreStats.ST_NET_READ_CONTROL_ENTITY_COUNT );
    types.add( CoreStats.ST_NET_READ_CONTROL_CON_COUNT );
    types.add( CoreStats.ST_NET_READ_CONTROL_READY_CON_COUNT );

    CoreStats.registerProvider(
    	types,
    	this );

    AEDiagnostics.addWeakEvidenceGenerator(this);
  }

	@Override
	public void
	generate(
			IndentWriter writer )
	{
		writer.println( "Read Controller" );

		try{
			writer.indent();

			ArrayList<RateControlledEntity> ref = normal_priority_entities;

			writer.println( "normal - " + ref.size());

			for (int i=0;i<ref.size();i++){

				RateControlledEntity entity = ref.get( i );

				writer.println( entity.getString());
			}

			ref = high_priority_entities;

			writer.println( "priority - " + ref.size());

			for (int i=0;i<ref.size();i++){

				RateControlledEntity entity = ref.get( i );

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
	  if ( types.contains( CoreStats.ST_NET_READ_CONTROL_LOOP_COUNT )){

		  values.put( CoreStats.ST_NET_READ_CONTROL_LOOP_COUNT, new Long( loop_count  ));
	  }

	  if ( types.contains( CoreStats.ST_NET_READ_CONTROL_NP_COUNT )){

		  values.put( CoreStats.ST_NET_READ_CONTROL_NP_COUNT, new Long( non_progress_count  ));
	  }

	  if ( types.contains( CoreStats.ST_NET_READ_CONTROL_P_COUNT )){

		  values.put( CoreStats.ST_NET_READ_CONTROL_P_COUNT, new Long( progress_count  ));
	  }

	  if ( types.contains( CoreStats.ST_NET_READ_CONTROL_WAIT_COUNT )){

		  values.put( CoreStats.ST_NET_READ_CONTROL_WAIT_COUNT, new Long( wait_count  ));
	  }

	  if ( types.contains( CoreStats.ST_NET_READ_CONTROL_ENTITY_COUNT )){

		  values.put( CoreStats.ST_NET_READ_CONTROL_ENTITY_COUNT, new Long( high_priority_entities.size() + normal_priority_entities.size()));
	  }

	  if ( 	types.contains( CoreStats.ST_NET_READ_CONTROL_CON_COUNT ) ||
			types.contains( CoreStats.ST_NET_READ_CONTROL_READY_CON_COUNT )){

		  int	ready_connections	= 0;
		  int	connections			= 0;

		  ArrayList[] refs = { normal_priority_entities, high_priority_entities };

		  for (int i=0;i<refs.length;i++){

			  ArrayList	ref = refs[i];

			  for (int j=0;j<ref.size();j++){

			      RateControlledEntity entity = (RateControlledEntity)ref.get( j );

			      connections 		+= entity.getConnectionCount( read_waiter );

			      ready_connections += entity.getReadyConnectionCount( read_waiter );
			  }
		  }

		  values.put( CoreStats.ST_NET_READ_CONTROL_CON_COUNT, new Long( connections ));
		  values.put( CoreStats.ST_NET_READ_CONTROL_READY_CON_COUNT, new Long( ready_connections ));
	  }
  }




  void readProcessorLoop() {
    boolean check_high_first = true;

    while( true ) {
      loop_count++;
      try {
        if( check_high_first ) {
          check_high_first = false;
          if( !doHighPriorityRead() ) {
            if( !doNormalPriorityRead() ) {
            	if ( read_waiter.waitForEvent( hasConnections()?IDLE_SLEEP_TIME:1000 )){
            		wait_count++;
            	}
            }
          }
        }
        else {
          check_high_first = true;
          if( !doNormalPriorityRead() ) {
            if( !doHighPriorityRead() ) {
            	if ( read_waiter.waitForEvent(hasConnections()?IDLE_SLEEP_TIME:1000 )){
            		wait_count++;
            	}
            }
          }
        }
      }catch( Throwable t ) {
        Debug.out( "readProcessorLoop() EXCEPTION: ", t );
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

		  if ( e.getConnectionCount( read_waiter ) > 0 ){

			  return( true );
		  }
	  }

	  ref = normal_priority_entities;

	  for ( RateControlledEntity e: ref ){

		  if ( e.getConnectionCount( read_waiter ) > 0 ){

			  return( true );
		  }
	  }

	  return( false );
  }

  private boolean
  doNormalPriorityRead()
  {
	  return( doRead( getNextReadyNormalPriorityEntity()));
  }

  private boolean
  doHighPriorityRead()
  {
	  return( doRead( getNextReadyHighPriorityEntity()));
  }

  private boolean
  doRead(
	  RateControlledEntity	  ready_entity )
  {
	  if ( ready_entity != null ){

		  if ( AGGRESIVE_READ ){

			  	// skip over failed readers to find a good one

			  if ( ready_entity.doProcessing( read_waiter, 0 ) > 0 ) {

				  progress_count++;

				  return( true );

			  }else{

				  non_progress_count++;

				  if ( entity_check_count - last_entity_check_count >= normal_priority_entities.size() + high_priority_entities.size() ){

					  last_entity_check_count	= entity_check_count;

					  	// force a wait

					  if ( read_waiter.waitForEvent( IDLE_SLEEP_TIME )){
		            		wait_count++;
					  }

					  return( false);
				  }

				  return( true );
			  }
		  }else{

			  return( ready_entity.doProcessing( read_waiter, 0 ) > 0 );
		  }
	  }

	  return false;
  }


  private RateControlledEntity getNextReadyNormalPriorityEntity() {
    ArrayList<RateControlledEntity> ref = normal_priority_entities;

    int size = ref.size();
    int num_checked = 0;

    while( num_checked < size ) {
      entity_check_count++;
      next_normal_position = next_normal_position >= size ? 0 : next_normal_position;  //make circular
      RateControlledEntity entity = ref.get( next_normal_position );
      next_normal_position++;
      num_checked++;
      if( entity.canProcess( read_waiter ) ) {  //is ready
        return entity;
      }
    }

    return null;  //none found ready
  }


  private RateControlledEntity getNextReadyHighPriorityEntity() {
    ArrayList<RateControlledEntity> ref = high_priority_entities;

    int size = ref.size();
    int num_checked = 0;

    while( num_checked < size ) {
      entity_check_count++;
      next_high_position = next_high_position >= size ? 0 : next_high_position;  //make circular
      RateControlledEntity entity = ref.get( next_high_position );
      next_high_position++;
      num_checked++;
      if( entity.canProcess( read_waiter ) ) {  //is ready
        return entity;
      }
    }

    return null;  //none found ready
  }



  /**
   * Add the given entity to the controller for read processing.
   * @param entity to process reads for
   */
  public void addReadEntity( RateControlledEntity entity ) {
    try {  entities_mon.enter();
      if( entity.getPriority() == RateControlledEntity.PRIORITY_HIGH ) {
        //copy-on-write
        ArrayList<RateControlledEntity> high_new = new ArrayList<>(high_priority_entities.size() + 1);
        high_new.addAll( high_priority_entities );
        high_new.add( entity );
        high_priority_entities = high_new;
      }
      else {
        //copy-on-write
        ArrayList<RateControlledEntity> norm_new = new ArrayList<>(normal_priority_entities.size() + 1);
        norm_new.addAll( normal_priority_entities );
        norm_new.add( entity );
        normal_priority_entities = norm_new;
      }

      entity_count = normal_priority_entities.size() + high_priority_entities.size();
    }
    finally {  entities_mon.exit();  }

    read_waiter.eventOccurred();
  }


  /**
   * Remove the given entity from the controller.
   * @param entity to remove from read processing
   */
  public boolean removeReadEntity( RateControlledEntity entity ) {
	boolean found = false;
    try {  entities_mon.enter();
      if( entity.getPriority() == RateControlledEntity.PRIORITY_HIGH ) {
        //copy-on-write
        ArrayList<RateControlledEntity> high_new = new ArrayList<>(high_priority_entities);
        if ( high_new.remove( entity )){
        	
        	high_priority_entities = high_new;
        	
        	found = true;
        }else{
        	Debug.out( "entity not found" );
        }
      }
      else {
        //copy-on-write
        ArrayList<RateControlledEntity> norm_new = new ArrayList<>(normal_priority_entities);
       
        if ( norm_new.remove( entity )){
        
        	normal_priority_entities = norm_new;
        	
        	found = true;
        }else{
        	Debug.out( "entity not found" );
        }
      }

      entity_count = normal_priority_entities.size() + high_priority_entities.size();
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
