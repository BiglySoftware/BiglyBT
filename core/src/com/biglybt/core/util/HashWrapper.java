/*
 * Created on 22 juil. 2003
 *
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
 */
package com.biglybt.core.util;

import java.util.Arrays;

import com.biglybt.pif.utils.ByteArrayWrapper;

/**
 * @author Olivier
 *
 */
public class
HashWrapper
	implements ByteArrayWrapper
{

  private final byte[] 	hash;
  private int		hash_code;

  public HashWrapper(byte[] _hash)
  {
  	this(_hash,0,_hash.length);
  }

  public HashWrapper(byte[] _hash, int offset,int length)
  {
	 hash = new byte[length];

	 System.arraycopy(_hash,offset,hash,0,length);

	 for (int i = 0; i < length; i++) {

	 	hash_code = 31*hash_code + hash[i];
	 }
   }

  public boolean equals(Object o) {
    if(! (o instanceof HashWrapper))
      return false;

    byte[] otherHash = ((HashWrapper)o).getHash();
	return Arrays.equals(hash, otherHash);
  }

  public byte[]
  getHash()
  {
    return( hash );
  }

  @Override
  public byte[]
  getBytes()
  {
  	return( hash );
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  public int hashCode()
  {
  	return( hash_code );
  }

  @Override
  public String toBase32String() {
  	return Base32.encode(hash);
  }
}
