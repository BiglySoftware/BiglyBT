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

import com.biglybt.core.networkmanager.impl.tcp.VirtualChannelSelectorImpl;


public class VirtualChannelSelector {
  public static final int OP_ACCEPT  = SelectionKey.OP_ACCEPT;
  public static final int OP_CONNECT  = SelectionKey.OP_CONNECT;
  public static final int OP_READ   = SelectionKey.OP_READ;
  public static final int OP_WRITE  = SelectionKey.OP_WRITE;

  private final String		name;
  private final VirtualChannelSelectorImpl selector_impl;

  private volatile boolean	destroyed;

  private final int op;

  private boolean randomise_keys;


  /**
   * Create a new virtual selectable-channel selector, selecting over the given interest-op.
   * @param interest_op operation set of OP_CONNECT, OP_ACCEPT, OP_READ, or OP_WRITE
   * @param pause_after_select whether or not to auto-disable interest op after select
   */
  public VirtualChannelSelector( String name, int interest_op, boolean pause_after_select ) {
		this.name = name;
    this.op = interest_op;

	  selector_impl = new VirtualChannelSelectorImpl(this, op, pause_after_select, randomise_keys);
  }

  public String
  getName()
  {
	  return( name );
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
	  selector_impl.register(channel, listener, attachment);
  }



  public boolean isPaused( AbstractSelectableChannel channel ) {
	  return selector_impl.isPaused(channel);
  }
  
  /**
   * Pause selection operations for the given channel
   * @param channel to pause
   */
  public void pauseSelects( AbstractSelectableChannel channel ) {
	  selector_impl.pauseSelects(channel);
  }



  /**
   * Resume selection operations for the given channel
   * @param channel to resume
   */
  public void resumeSelects( AbstractSelectableChannel channel ) {
	  selector_impl.resumeSelects(channel);
  }


  public boolean isRegistered( AbstractSelectableChannel channel ) {
	  return selector_impl.isRegistered(channel);
  }

  /**
   * Cancel the selection operations for the given channel.
   * @param channel channel originally registered
   */
  public void cancel( AbstractSelectableChannel channel ) {
	  selector_impl.cancel(channel);
  }

  public void
  setRandomiseKeys(
	boolean	_rk )
  {
	  randomise_keys = _rk;

	  selector_impl.setRandomiseKeys(randomise_keys);
  }

  /**
   * Run a virtual select() operation, with the given selection timeout value;
   * (1) cancellations are processed (2) the select operation is performed; (3)
   * listener notification of completed selects (4) new registrations are processed
   * @param timeout in ms; if zero, block indefinitely
   * @return number of sockets selected
   */
  public int select(long timeout) {

    return selector_impl.select( timeout );
  }

  public void destroy()
  {
	  destroyed	= true;

	  selector_impl.destroy();
  }

  public boolean
  isDestroyed()
  {
	  return( destroyed );
  }

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
