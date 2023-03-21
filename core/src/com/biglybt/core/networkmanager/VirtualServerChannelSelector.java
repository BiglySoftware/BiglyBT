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

package com.biglybt.core.networkmanager;

import java.net.InetAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;




/**
 * Virtual server socket channel for listening and accepting incoming connections.
 */

public interface VirtualServerChannelSelector {

  public void startProcessing();

  public void stopProcessing();

  public boolean isRunning();

  public InetAddress getBoundToAddress();

  public int getPort();

  public long getTimeOfLastAccept();

  public void setAlertOnFail( boolean b );
  
  /**
   * Listener notified when a new incoming connection is accepted.
   */
  public interface SelectListener {
    /**
     * The given connection has just been accepted.
     * @param channel new connection
     */
    public void newConnectionAccepted( ServerSocketChannel	server, SocketChannel channel );
  }


}
