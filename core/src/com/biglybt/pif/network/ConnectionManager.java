/*
 * Created on Feb 9, 2005
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

package com.biglybt.pif.network;

import java.net.InetSocketAddress;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.spec.SecretKeySpec;

import com.biglybt.pif.messaging.MessageStreamDecoder;
import com.biglybt.pif.messaging.MessageStreamEncoder;


/**
 * Manages connections.
 */
public interface
ConnectionManager
{
	public static final int	NAT_UNKNOWN			= 0;
	public static final int	NAT_OK				= 1;
	public static final int	NAT_PROBABLY_OK		= 2;
	public static final int	NAT_BAD				= 3;

  /**
   * Create a new unconnected remote connection (for outbound-initiated connections).
   * @param remote_address to connect to
   * @return not yet established connection
   */
  public Connection createConnection( InetSocketAddress remote_address, MessageStreamEncoder encoder, MessageStreamDecoder decoder );

  	/**
  	 * Returns the current view on whether or not we are inwardly connectable via our listener port
  	 * @return
  	 */
  public int getNATStatus();

  public Object[] getNATStatusEx();
  
  /**
   * @since 3.0.5.3
   */
  public TransportCipher createTransportCipher(String algorithm, int mode, SecretKeySpec key_spec, AlgorithmParameterSpec params) throws TransportException;

  /**
   * @since 3.0.5.3
   */
  public TransportFilter createTransportFilter(Connection connection, TransportCipher read_cipher, TransportCipher write_cipher) throws TransportException;

  /**
   * @since 4.7.0.3
   */

  public RateLimiter
  createRateLimiter(
	 String		name,
	 int		bytes_per_second );

}
