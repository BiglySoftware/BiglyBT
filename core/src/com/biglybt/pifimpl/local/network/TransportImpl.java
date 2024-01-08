/*
 * Created on Feb 11, 2005
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

package com.biglybt.pifimpl.local.network;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.pif.network.Transport;
import com.biglybt.pif.network.TransportFilter;

/**
 *
 */
public class TransportImpl implements Transport {
  private com.biglybt.core.networkmanager.Transport core_transport;
  private NetworkConnection	core_network;

  public TransportImpl( NetworkConnection core_network ) {
    this.core_network = core_network;
  }

  public TransportImpl( com.biglybt.core.networkmanager.Transport core_transport ) {
	    this.core_transport = core_transport;
  }

  @Override
  public long read(ByteBuffer[] buffers, int array_offset, int length ) throws IOException {
    return coreTransport().read( buffers, array_offset, length );
  }

  @Override
  public long write(ByteBuffer[] buffers, int array_offset, int length ) throws IOException {
    return coreTransport().write( buffers, array_offset, length );
  }

  public com.biglybt.core.networkmanager.Transport coreTransport() throws IOException {
	if ( core_transport == null ){
		core_transport = core_network.getTransport();
		if ( core_transport == null ){
			throw( new IOException( "Not connected" ));
		}
	}
	return this.core_transport;
  }

  @Override
  public void setFilter(TransportFilter filter) throws IOException {
	  ((com.biglybt.core.networkmanager.impl.TransportImpl)coreTransport()).setFilter(
	      ((TransportFilterImpl)filter).filter
	  );
  }

  @Override
  public boolean isEncrypted(){
	  if ( core_transport == null ){
		  core_transport = core_network.getTransport();
		  if ( core_transport == null ){
			  return( false );
		  }
	  }
	  return core_transport.isEncrypted();
  }
}
