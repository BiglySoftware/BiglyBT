/*
 * Created on Feb 24, 2005
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

package com.biglybt.pif.messaging;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.messaging.generic.GenericMessageHandler;
import com.biglybt.pif.messaging.generic.GenericMessageRegistration;


/**
 * Manages peer message handling.
 */
public interface MessageManager {

  public static final int	STREAM_ENCRYPTION_NONE				= 1;
  public static final int	STREAM_ENCRYPTION_RC4_PREFERRED		= 2;
  public static final int	STREAM_ENCRYPTION_RC4_REQUIRED		= 3;

  /**
   * Register the given message type with the manager for processing.
   * NOTE: A message type needs to be registered in order for support to be
   * advertised to other peers.
   * @param message instance to use for decoding
   * @throws MessageException if this message type has already been registered
   */
  public void registerMessageType( Message message ) throws MessageException;


  /**
   * Remove registration of given message type from manager.
   * @param message type to remove
   */
  public void deregisterMessageType( Message message );


  /**
   * Globally register for notification of peers that support the given message type.
   * @param plug_interface to get the download manager
   * @param message to match
   * @param listener to notify
   */
  public void locateCompatiblePeers( PluginInterface plug_interface, Message message, MessageManagerListener listener );


  /**
   * Cancel registration for compatible-peer notification.
   * @param orig_listener listener used for registration
   */
  public void cancelCompatiblePeersLocation( MessageManagerListener orig_listener );

  /**
   * Register a peer-independent message handler
   * @param type
   * @param description
   * @param handler
   * @return
   * @throws MessageException
   */

  public GenericMessageRegistration
  registerGenericMessageType(
		String					type,
		String					description,
		int						stream_encryption,
		GenericMessageHandler	handler )

  	throws MessageException;
}
