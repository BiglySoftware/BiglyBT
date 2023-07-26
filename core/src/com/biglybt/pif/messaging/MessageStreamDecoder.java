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

package com.biglybt.pif.messaging;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.biglybt.pif.network.Transport;


/**
 * Decodes a message stream into separate messages.
 */
public interface MessageStreamDecoder {
  /**
   * Decode message stream from the given transport.
   * @param transport to decode from
   * @param max_bytes to decode/read from the stream
   * @return number of bytes decoded
   * @throws IOException on decoding error
   */
  public int performStreamDecode( Transport transport, int max_bytes ) throws IOException;

  /**
   * Get the messages decoded from the transport, if any, from the last decode op.
   * @return decoded messages, or null if no new complete messages were decoded
   */
  public Message[] removeDecodedMessages();

  /**
   * Get the number of protocol (overhead) bytes decoded from the transport, from the last decode op.
   * @return number of protocol bytes received
   */
  public int getProtocolBytesDecoded();

  /**
   * Get the number of (piece) data bytes decoded from the transport, from the last decode op.
   * @return number of data bytes received
   */
  public int getDataBytesDecoded();

  /**
   * Pause message decoding.
   */
  public void pauseDecoding();

  /**
   * Resume message decoding.
   */
  public void resumeDecoding();

  /**
   * Destroy this decoder, i.e. perform cleanup.
   * @return any bytes already-read and still remaining within the decoder
   */
  public ByteBuffer destroy();
}
