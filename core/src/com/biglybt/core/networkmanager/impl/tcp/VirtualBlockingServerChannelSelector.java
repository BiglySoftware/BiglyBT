/*
 * Created on Dec 4, 2004
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
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.VirtualServerChannelSelector;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;



/**
 * Virtual server socket channel for listening and accepting incoming connections.
 */
public class VirtualBlockingServerChannelSelector
	implements VirtualServerChannelSelector{
	private static final LogIDs LOGID = LogIDs.NWMAN;
  private ServerSocketChannel server_channel = null;
  private final InetSocketAddress bind_address;
  private final int receive_buffer_size;
  private final SelectListener listener;

  protected final AEMonitor	this_mon	= new AEMonitor( "VirtualServerChannelSelector" );

  private long last_accept_time;

  private boolean alert_on_fail = true;


  /**
   * Create a new server listening on the given address and reporting to the given listener.
   * @param bind_address ip+port to listen on
   * @param so_rcvbuf_size new socket receive buffer size
   * @param listener to notify of incoming connections
   */
  public VirtualBlockingServerChannelSelector( InetSocketAddress _bind_address, int so_rcvbuf_size, SelectListener listener ) {
    this.bind_address = _bind_address;
    this.receive_buffer_size = so_rcvbuf_size;
    this.listener = listener;
  }

	public void
	setAlertOnFail(
		boolean b )
	{
		alert_on_fail = b;
	}
	

  /**
   * Start the server and begin accepting incoming connections.
   *
   */
  @Override
  public void startProcessing() {
  	try{
  		this_mon.enter();

	    if( !isRunning() ) {
	      try {
	        server_channel = ServerSocketChannel.open();

	        server_channel.socket().setReuseAddress( true );
	        if( receive_buffer_size > 0 )  server_channel.socket().setReceiveBufferSize( receive_buffer_size );

	        server_channel.socket().bind( bind_address, 1024 );

	        if (Logger.isEnabled()) 	Logger.log(new LogEvent(LOGID, "TCP incoming server socket "	+ bind_address));

	        AEThread accept_thread = new AEThread( "VServerSelector:port" + bind_address.getPort() ) {
	          @Override
	          public void runSupport() {
	            accept_loop();
	          }
	        };
	        accept_thread.setDaemon( true );
	        accept_thread.start();
	      }
	      catch( Throwable t ) {
	    	  if ( alert_on_fail ){
		      	Debug.out( t );
		      	Logger.log(new LogAlert(LogAlert.UNREPEATABLE,	"ERROR, unable to bind TCP incoming server socket to " +bind_address.getPort(), t));
	    	  }
	      }

	      last_accept_time = SystemTime.getCurrentTime();  //init to now
	    }
  	}finally{

  		this_mon.exit();
  	}
  }


  /**
   * Stop the server.
   */
  @Override
  public void stopProcessing() {
  	try{
  		this_mon.enter();

	    if( server_channel != null ) {
	      try {
	        server_channel.close();
	        server_channel = null;
	      }
	      catch( Throwable t ) {  Debug.out( t );  }
	    }
  	}finally{

  		this_mon.exit();
  	}
  }


  protected void accept_loop() {
    while( isRunning() ) {
      try {
        SocketChannel client_channel = server_channel.accept();
        last_accept_time = SystemTime.getCurrentTime();
        try{
        	client_channel.configureBlocking( false );
        }catch( IOException e ){
        	client_channel.close();
        	throw( e );
        }
        listener.newConnectionAccepted( server_channel, client_channel );
      }
      catch( AsynchronousCloseException e ) {
        /* is thrown when stop() is called */
      }
      catch( Throwable t ) {
        Debug.out( t );
        try {  Thread.sleep( 500 );  }catch( Exception e ) {  e.printStackTrace();  }
      }

    }
  }


  /**
   * Is this selector actively running
   * @return true if enabled, false if not running
   */
  @Override
  public boolean isRunning() {
  	if( server_channel != null && server_channel.isOpen() )  return true;
  	return false;
  }


  @Override
  public InetAddress getBoundToAddress() {
  	if( server_channel != null ) {
  		return server_channel.socket().getInetAddress();
  	}
  	return null;
  }

  @Override
  public int getPort()
  {
	 	if( server_channel != null ) {
	  		return server_channel.socket().getLocalPort();
	  	}
	  	return -1;
  }

  @Override
  public long getTimeOfLastAccept() {
  	return last_accept_time;
  }
}
