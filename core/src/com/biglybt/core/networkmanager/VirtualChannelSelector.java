/*
 * Created on Jun 5, 2005
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

package com.biglybt.core.networkmanager;

import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.impl.tcp.VirtualChannelSelectorImpl;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;


public class VirtualChannelSelector {
  private static final LogIDs LOGID = LogIDs.NWMAN;
  public static final int OP_ACCEPT  = SelectionKey.OP_ACCEPT;
  public static final int OP_CONNECT  = SelectionKey.OP_CONNECT;
  public static final int OP_READ   = SelectionKey.OP_READ;
  public static final int OP_WRITE  = SelectionKey.OP_WRITE;

  private boolean SAFE_SELECTOR_MODE_ENABLED = TEST_SAFE_MODE;

  private static final boolean TEST_SAFE_MODE	= false;

  private static final int MAX_CHANNELS_PER_SAFE_SELECTOR	= COConfigurationManager.getIntParameter( "network.tcp.safe_selector_mode.chunk_size" );

  private static final int MAX_SAFEMODE_SELECTORS = 20000 / MAX_CHANNELS_PER_SAFE_SELECTOR;

  private final String		name;

  private VirtualChannelSelectorImpl selector_impl;

  private volatile boolean	destroyed;

  //ONLY USED IN FAULTY MODE
  private HashMap<VirtualChannelSelectorImpl,ArrayList<AbstractSelectableChannel>> selectors;
  private HashSet<VirtualChannelSelectorImpl> selectors_keyset_cow;
  private AEMonitor selectors_mon;
  private final int op;
  private final boolean pause;

  private boolean randomise_keys;


  /**
   * Create a new virtual selectable-channel selector, selecting over the given interest-op.
   * @param interest_op operation set of OP_CONNECT, OP_ACCEPT, OP_READ, or OP_WRITE
   * @param pause_after_select whether or not to auto-disable interest op after select
   */
  public VirtualChannelSelector( String name, int interest_op, boolean pause_after_select ) {
	this.name = name;
    this.op = interest_op;
    this.pause = pause_after_select;

    if( SAFE_SELECTOR_MODE_ENABLED ) {
      initSafeMode();
    }
    else {
      selector_impl = new VirtualChannelSelectorImpl( this, op, pause, randomise_keys );
      selectors = null;
      selectors_keyset_cow	= null;
      selectors_mon = null;
    }
  }

  public String
  getName()
  {
	  return( name );
  }

  private void initSafeMode() {
	//System.out.println( "***************** SAFE SOCKET SELECTOR MODE ENABLED *****************" );

    if (Logger.isEnabled()) {
    	Logger.log(new LogEvent(LOGID, "***************** SAFE SOCKET SELECTOR MODE ENABLED *****************"));
    }

    selector_impl = null;
    selectors = new HashMap<>();
    selectors_mon = new AEMonitor( "VirtualChannelSelector:FM" );
    selectors.put( new VirtualChannelSelectorImpl( this, op, pause, randomise_keys ), new ArrayList<AbstractSelectableChannel>() );
    selectors_keyset_cow = new HashSet<>(selectors.keySet());
  }


  public void register( SocketChannel channel, VirtualSelectorListener listener, Object attachment ) {
	  registerSupport( channel, listener, attachment );
  }
  public void register( ServerSocketChannel channel, VirtualAcceptSelectorListener listener, Object attachment ) {
	  registerSupport( channel, listener, attachment );
  }


  /**
   * Register the given selectable channel, using the given listener for notification
   * of completed select operations.
   * NOTE: For OP_CONNECT and OP_WRITE -type selectors, once a selection request op
   * completes, the channel's op registration is automatically disabled (paused); any
   * future wanted selection notification requires re-enabling via resume.  For OP_READ selectors,
   * it stays enabled until actively paused, no matter how many times it is selected.
   * @param channel socket to listen for
   * @param listener op-complete listener
   * @param attachment object to be passed back with listener notification
   */
  protected void registerSupport( AbstractSelectableChannel channel, VirtualAbstractSelectorListener listener, Object attachment ) {
    if( SAFE_SELECTOR_MODE_ENABLED ) {
      try{  selectors_mon.enter();
      	//System.out.println( "register - " + channel.hashCode()  + " - " + Debug.getCompressedStackTrace());
        for( Map.Entry<VirtualChannelSelectorImpl, ArrayList<AbstractSelectableChannel>> entry: selectors.entrySet()) {

          VirtualChannelSelectorImpl 			sel 		= entry.getKey();
          ArrayList<AbstractSelectableChannel> 	channels 	= entry.getValue();

          if( channels.size() >= ( TEST_SAFE_MODE?0:MAX_CHANNELS_PER_SAFE_SELECTOR )) {

         	  // it seems that we have a bug somewhere where a selector is being registered
        	  // but not cancelled on close. As an interim fix scan channels and remove any
        	  // closed ones

        	  Iterator<AbstractSelectableChannel>	chan_it = channels.iterator();

        	  while( chan_it.hasNext()){

        		  AbstractSelectableChannel	chan = chan_it.next();

        		  if ( !chan.isOpen()){

        			  Debug.out( "Selector '" + getName() + "' - removing orphaned safe channel registration" );

        			  chan_it.remove();
        		  }
        	  }
          }

          if( channels.size() < MAX_CHANNELS_PER_SAFE_SELECTOR ) {  //there's room in the current selector

            sel.register( channel, listener, attachment );
            channels.add( channel );

            return;
          }
        }

        //we couldnt find room in any of the existing selectors, so start up a new one if allowed

        //max limit to the number of Selectors we are allowed to create
        if( selectors.size() >= MAX_SAFEMODE_SELECTORS ) {
      	  String msg = "Error: MAX_SAFEMODE_SELECTORS reached [" +selectors.size()+ "], no more socket channels can be registered. Too many peer connections.";
      	  Debug.out( msg );
      	  selectFailure( listener, channel, attachment, new Throwable( msg ) );  //reject registration
      	  return;
        }

        if ( destroyed ){
          String	msg = "socket registered after controller destroyed";
       	  Debug.out( msg );
      	  selectFailure( listener, channel, attachment, new Throwable( msg ) );  //reject registration
      	  return;
        }

        VirtualChannelSelectorImpl sel = new VirtualChannelSelectorImpl( this, op, pause , randomise_keys);

        ArrayList<AbstractSelectableChannel> chans = new ArrayList<>();

        selectors.put( sel, chans );

        sel.register( channel, listener, attachment );

        chans.add( channel );

        selectors_keyset_cow = new HashSet<>(selectors.keySet());
      }
      finally{ selectors_mon.exit();  }
    }
    else {
      selector_impl.register( channel, listener, attachment );
    }
  }



  public boolean isPaused( AbstractSelectableChannel channel ) {
	    if( SAFE_SELECTOR_MODE_ENABLED ) {
	      try{  selectors_mon.enter();
	      	//System.out.println( "pause - " + channel.hashCode() + " - " + Debug.getCompressedStackTrace());
	      for( Map.Entry<VirtualChannelSelectorImpl, ArrayList<AbstractSelectableChannel>> entry: selectors.entrySet()) {

	          VirtualChannelSelectorImpl 			sel 		= entry.getKey();
	          ArrayList<AbstractSelectableChannel> 	channels 	= entry.getValue();

	          if( channels.contains( channel ) ) {
	            return( sel.isPaused( channel ));
	          }
	        }

	        Debug.out( "isPaused():: channel not found!" );
	      }
	      finally{ selectors_mon.exit();  }
	      
	      return( false );
	    }
	    else {
	      return( selector_impl.isPaused( channel ));
	    }
	  }
  
  /**
   * Pause selection operations for the given channel
   * @param channel to pause
   */
  public void pauseSelects( AbstractSelectableChannel channel ) {
    if( SAFE_SELECTOR_MODE_ENABLED ) {
      try{  selectors_mon.enter();
      	//System.out.println( "pause - " + channel.hashCode() + " - " + Debug.getCompressedStackTrace());
      for( Map.Entry<VirtualChannelSelectorImpl, ArrayList<AbstractSelectableChannel>> entry: selectors.entrySet()) {

          VirtualChannelSelectorImpl 			sel 		= entry.getKey();
          ArrayList<AbstractSelectableChannel> 	channels 	= entry.getValue();

          if( channels.contains( channel ) ) {
            sel.pauseSelects( channel );
            return;
          }
        }

        Debug.out( "pauseSelects():: channel not found!" );
      }
      finally{ selectors_mon.exit();  }
    }
    else {
      selector_impl.pauseSelects( channel );
    }
  }



  /**
   * Resume selection operations for the given channel
   * @param channel to resume
   */
  public void resumeSelects( AbstractSelectableChannel channel ) {
    if( SAFE_SELECTOR_MODE_ENABLED ) {
      try{  selectors_mon.enter();
      	//System.out.println( "resume - " + channel.hashCode() + " - " + Debug.getCompressedStackTrace());
      for( Map.Entry<VirtualChannelSelectorImpl, ArrayList<AbstractSelectableChannel>> entry: selectors.entrySet()) {

          VirtualChannelSelectorImpl 			sel 		= entry.getKey();
          ArrayList<AbstractSelectableChannel> 	channels 	= entry.getValue();

          if( channels.contains( channel ) ) {
            sel.resumeSelects( channel );
            return;
          }
        }

        Debug.out( "resumeSelects():: channel not found!" );
      }
      finally{ selectors_mon.exit();  }
    }
    else {
      selector_impl.resumeSelects( channel );
    }
  }


  public boolean isRegistered( AbstractSelectableChannel channel ) {
	    if( SAFE_SELECTOR_MODE_ENABLED ) {
	      try{  selectors_mon.enter();
	      	//System.out.println( "pause - " + channel.hashCode() + " - " + Debug.getCompressedStackTrace());
	      for( Map.Entry<VirtualChannelSelectorImpl, ArrayList<AbstractSelectableChannel>> entry: selectors.entrySet()) {

	          VirtualChannelSelectorImpl 			sel 		= entry.getKey();
	          ArrayList<AbstractSelectableChannel> 	channels 	= entry.getValue();

	          if( channels.contains( channel ) ) {
	            return( sel.isRegistered( channel ));
	          }
	        }

	        Debug.out( "isRegistered():: channel not found!" );
	      }
	      finally{ selectors_mon.exit();  }
	      
	      return( false );
	    }
	    else {
	      return( selector_impl.isRegistered( channel ));
	    }
	  }
  /**
   * Cancel the selection operations for the given channel.
   * @param channel channel originally registered
   */
  public void cancel( AbstractSelectableChannel channel ) {
    if( SAFE_SELECTOR_MODE_ENABLED ) {
      try{  selectors_mon.enter();
      	//System.out.println( "cancel - " + channel.hashCode()  + " - " + Debug.getCompressedStackTrace());
      for( Map.Entry<VirtualChannelSelectorImpl, ArrayList<AbstractSelectableChannel>> entry: selectors.entrySet()) {

          VirtualChannelSelectorImpl 			sel 		= entry.getKey();
          ArrayList<AbstractSelectableChannel> 	channels 	= entry.getValue();

          if( channels.remove( channel ) ) {
            sel.cancel( channel );
            return;
          }
        }
      }
      finally{ selectors_mon.exit();  }
    }
    else {
      if( selector_impl != null )  selector_impl.cancel( channel );
    }
  }

  public void
  setRandomiseKeys(
	boolean	_rk )
  {
	  randomise_keys = _rk;

	    if( SAFE_SELECTOR_MODE_ENABLED ) {
	      try{  selectors_mon.enter();
	        for( VirtualChannelSelectorImpl sel: selectors.keySet()){
	        	sel.setRandomiseKeys( randomise_keys );
	        }
	      }
	      finally{ selectors_mon.exit();  }
	    }
	    else {
	      if( selector_impl != null )  selector_impl.setRandomiseKeys( randomise_keys );
	    }
	  }

  /**
   * Run a virtual select() operation, with the given selection timeout value;
   * (1) cancellations are processed (2) the select operation is performed; (3)
   * listener notification of completed selects (4) new registrations are processed
   * @param timeout in ms; if zero, block indefinitely
   * @return number of sockets selected
   */
  public int select(long timeout) {
    if( SAFE_SELECTOR_MODE_ENABLED ) {
      boolean	was_destroyed = destroyed;

      try{
	      int count = 0;

	      for( VirtualChannelSelectorImpl sel: selectors_keyset_cow ){

	        count += sel.select( timeout );
	      }

	      return count;

      }finally{

    	  if ( was_destroyed ){

    		  // destruction process requires select op after destroy...

   			 try{
   				 selectors_mon.enter();

	 		     selectors.clear();
			     selectors_keyset_cow = new HashSet<>();

   			 }finally{
   				 selectors_mon.exit();
   			 }
    	  }
      }
    }

    return selector_impl.select( timeout );
  }

  public void destroy()
  {
	  destroyed	= true;

	  if ( SAFE_SELECTOR_MODE_ENABLED ){

	      for( VirtualChannelSelectorImpl sel: selectors_keyset_cow ){

	        sel.destroy();
	     }
	  }else{
		  selector_impl.destroy();
	  }
  }

  public boolean
  isDestroyed()
  {
	  return( destroyed );
  }

  public boolean isSafeSelectionModeEnabled() {  return SAFE_SELECTOR_MODE_ENABLED;  }

  public boolean
  selectSuccess(
	VirtualAbstractSelectorListener		listener,
	AbstractSelectableChannel 			sc,
	Object 								attachment )
  {
	  if ( op == OP_ACCEPT ){

		  return(((VirtualAcceptSelectorListener)listener).selectSuccess( VirtualChannelSelector.this, (ServerSocketChannel)sc, attachment ));
	  }else{

		  return(((VirtualSelectorListener)listener).selectSuccess( VirtualChannelSelector.this, (SocketChannel)sc, attachment ));
	  }
  }

  public void
  selectFailure(
	VirtualAbstractSelectorListener		listener,
	AbstractSelectableChannel 			sc,
	Object 								attachment,
	Throwable							msg)
  {
	  if ( op == OP_ACCEPT ){

		  ((VirtualAcceptSelectorListener)listener).selectFailure( VirtualChannelSelector.this, (ServerSocketChannel)sc, attachment, msg );
	  }else{

		  ((VirtualSelectorListener)listener).selectFailure( VirtualChannelSelector.this, (SocketChannel)sc, attachment, msg );

	  }
  }

  public interface
  VirtualAbstractSelectorListener
  {
  }

  /**
   * Listener for notification upon socket channel selection.
   */
  public interface VirtualSelectorListener extends VirtualAbstractSelectorListener{
    /**
     * Called when a channel is successfully selected for readyness.
     * @param attachment originally given with the channel's registration
     * @return indicator of whether or not any 'progress' was made due to this select
     * 			null -> progress made, String -> location of non progress
     *         e.g. read-select -> read >0 bytes, write-select -> wrote > 0 bytes
     */
    public boolean selectSuccess(VirtualChannelSelector selector, SocketChannel sc, Object attachment);

    /**
     * Called when a channel selection fails.
     * @param msg  failure message
     */
    public void selectFailure(VirtualChannelSelector selector, SocketChannel sc, Object attachment, Throwable msg);
  }

  public interface VirtualAcceptSelectorListener extends VirtualAbstractSelectorListener{
    /**
     * Called when a channel is successfully selected for readyness.
     * @param attachment originally given with the channel's registration
     * @return indicator of whether or not any 'progress' was made due to this select
     *         e.g. read-select -> read >0 bytes, write-select -> wrote > 0 bytes
     */
    public boolean selectSuccess(VirtualChannelSelector selector, ServerSocketChannel sc, Object attachment);

    /**
     * Called when a channel selection fails.
     * @param msg  failure message
     */
    public void selectFailure(VirtualChannelSelector selector, ServerSocketChannel sc, Object attachment, Throwable msg);
  }

}
