 /*
 * Created on Apr 13, 2004
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

package com.biglybt.core.util;

import java.nio.ByteBuffer;


/**
 * SHA-1 hasher utility frontend.
 */
public final class SHA1Hasher {
  private final SHA1 sha1;


  /**
   * Create a new SHA1Hasher instance
   */
  public SHA1Hasher() {
    sha1 = new SHA1();
  }


  /**
   * Calculate the SHA-1 hash for the given bytes.
   * @param bytes data to hash
   * @return 20-byte hash
   */
  public byte[] calculateHash( byte[] bytes ) {
    ByteBuffer buff = ByteBuffer.wrap( bytes );
    return calculateHash( buff );
  }


  /**
   * Calculate the SHA-1 hash for the given buffer.
   * @param buffer data to hash
   * @return 20-byte hash
   */
  public byte[] calculateHash( ByteBuffer buffer ) {
    sha1.reset();
    return sha1.digest( buffer );
  }


  /**
   * Start or continue a hash calculation with the given data.
   * @param data input
   */
  public void update( byte[] data ) {
  	update( ByteBuffer.wrap( data ));
  }


  /**
   * Start or continue a hash calculation with the given data,
   * starting at the given position, for the given length.
   * @param data input
   * @param pos start position
   * @param len length
   */
  public void update( byte[] data, int pos, int len ) {
  	update( ByteBuffer.wrap( data, pos, len ));
  }


  /**
   * Start or continue a hash calculation with the given data.
   * @param buffer data input
   */
  public void update( ByteBuffer buffer ) {
    sha1.update( buffer );
  }


  /**
   * Finish the hash calculation.
   * @return 20-byte hash
   */
  public byte[] getDigest() {
  	return sha1.digest();
  }

  public HashWrapper getHash() {
  	return new HashWrapper(sha1.digest());
  }

  /**
   * Resets the hash calculation.
   */
  public void reset() {
    sha1.reset();
  }


  /**
   * Save the current hasher state for later resuming.
   */
  public void saveHashState() {
    sha1.saveState();
  }


  /**
   * Restore the hasher state from previous save.
   */
  public void restoreHashState() {
    sha1.restoreState();
  }

}
