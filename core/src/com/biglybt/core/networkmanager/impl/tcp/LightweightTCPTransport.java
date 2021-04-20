/*
 * Created on Oct 28, 2005
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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.biglybt.core.networkmanager.*;
import com.biglybt.core.networkmanager.impl.TransportHelperFilter;


/**
 * This class is essentially a socket channel wrapper to support working with az message encoders/decoders.
 */
public class LightweightTCPTransport implements Transport {

	private final TransportStartpointTCP		transport_startpoint;
	private final TransportEndpointTCP			transport_endpoint;
	private final TransportHelperFilter 		filter;

	public LightweightTCPTransport( ProtocolEndpoint	pe, TransportHelperFilter filter ) {
		SocketChannel channel = ((TCPTransportHelper)filter.getHelper()).getSocketChannel();
		transport_endpoint		= new TransportEndpointTCP( pe, channel );
		transport_startpoint	= new TransportStartpointTCP( transport_endpoint );

		this.filter = filter;
	}

	@Override
	public TransportEndpoint
	getTransportEndpoint()
	{
		return( transport_endpoint );
	}

	@Override
	public TransportStartpoint
	getTransportStartpoint()
	{
		return( transport_startpoint );
	}

  @Override
  public long write(ByteBuffer[] buffers, int array_offset, int length ) throws IOException {
  	return filter.write( buffers, array_offset, length );
  }


  @Override
  public long read(ByteBuffer[] buffers, int array_offset, int length ) throws IOException {
  	return filter.read( buffers, array_offset, length );
  }


  public SocketChannel getSocketChannel(){  return ((TCPTransportHelper)filter.getHelper()).getSocketChannel();  }

  public InetSocketAddress
  getRemoteAddress()
  {
	  return( new InetSocketAddress( getSocketChannel().socket().getInetAddress(), getSocketChannel().socket().getPort()));
  }

  @Override
  public String getDescription(){  return getSocketChannel().socket().getInetAddress().getHostAddress() + ": " + getSocketChannel().socket().getPort();  }

  @Override
  public void close(String reason ){
  	try {
  		getSocketChannel().close();  //close() can block
    }
    catch( Throwable t) { t.printStackTrace(); }
  }

  @Override
  public int
  getMssSize()
  {
	  return( TCPNetworkManager.getTcpMssSize());
  }

  @Override
  public void setAlreadyRead(ByteBuffer bytes_already_read ){ 	throw new RuntimeException( "not implemented" );  }
  @Override
  public boolean isReadyForWrite(EventWaiter waiter){  throw new RuntimeException( "not implemented" );  }
  @Override
  public long isReadyForRead(EventWaiter waiter){  throw new RuntimeException( "not implemented" );  }
  @Override
  public void setReadyForRead(){ throw new RuntimeException( "not implemented" );  }
  @Override
  public void connectOutbound(ByteBuffer initial_data, ConnectListener listener, int priority ){ throw new RuntimeException( "not implemented" ); }
  @Override
  public void connectedInbound(){ throw new RuntimeException( "not implemented" ); }
  @Override
  public void setTransportMode(int mode ){ throw new RuntimeException( "not implemented" ); }
  @Override
  public int getTransportMode(){ throw new RuntimeException( "not implemented" );  }
  @Override
  public void setTrace(boolean on) {
	  // TODO Auto-generated method stub

  }
  @Override
  public String getEncryption(boolean verbose){ return( filter.getName(verbose)); }
  @Override
  public boolean isEncrypted(){ return( filter.isEncrypted());}
  @Override
  public boolean isTCP(){ return true; }
  @Override
  public boolean isSOCKS(){ return( false ); }

	@Override
	public String getProtocol(){ return "TCP"; }

  @Override
  public void
  bindConnection(
	NetworkConnection	connection )
  {
  }

  @Override
  public void
  unbindConnection(
	NetworkConnection	connection )
  {
  }
  
  @Override
  public Object 
  getUserData(
		  Object		key )
  {
	  if ( filter != null ){

		  return( filter.getHelper().getUserData( key ));
	  }

	  return( null );
  }
  
	@Override
	public void 
	setUserData(
		Object		key,
		Object		value )
	{
		if ( filter != null ){
			
			filter.getHelper().setUserData( key, value );
		}
	}
}
