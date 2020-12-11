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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * SHA-1 hasher utility frontend.
 */
public final class 
SHA1Hasher 
{
	private static final boolean	use_digest;

	static{

		boolean	ud;
		
		try{
			new DigestImpl();
			
			ud = true;
			
		}catch( Throwable e ){
			
			ud = false;
		}
		
		use_digest = ud;
	}

	private final Impl		impl;
	
	public
	SHA1Hasher()
	{
		if ( use_digest ){
			
			Impl i;
			
			try{
				i	= new DigestImpl();
				
			}catch( Throwable e ){
				
					// shouldn't happen
				
				i = new LocalImpl();
			}
			
			impl = i;
			
		}else{
			
			impl	= new LocalImpl();
		}
	}
	
	public byte[] 
	calculateHash( 
		byte[] bytes ) 
	{
		return( impl.calculateHash(bytes));
	}

	public byte[] 
	calculateHash( 
		ByteBuffer buffer ) 
	{
		return( impl.calculateHash(buffer));
	}

	public void
	update( 
		byte[] data ) 
	{
		impl.update(data);
	}
	
	public void 
	update( 
		byte[] 	data, 
		int		pos, 
		int 	len ) 
	{
		impl.update(data, pos, len);
	}

	public byte[] 
	getDigest() 
	{
		return( impl.getDigest());
	}
	
	private interface
	Impl
	{
		public byte[] 
		calculateHash( 
			byte[] bytes ); 

		public byte[] 
		calculateHash( 
			ByteBuffer buffer );
		
		public void
		update( 
			byte[] data );
		
		public void 
		update( 
			byte[] 	data, 
			int 	pos, 
			int 	len );
		
		public byte[] 
		getDigest() ;
	}
	
	private static class
	DigestImpl
		implements Impl
	{
		private final MessageDigest	md;

		private 
		DigestImpl()
			throws NoSuchAlgorithmException
		{
			md = MessageDigest.getInstance( "SHA-1" );
		}
		
		public byte[] 
		calculateHash( 
			byte[] bytes ) 
		{
			md.reset();
			
			md.update( bytes );
			
			return( md.digest());
		}

		public byte[] 
		calculateHash( 
			ByteBuffer buffer ) 
		{
			md.reset();
			
			md.update( buffer );
			
			return( md.digest());
		}

		public void
		update( byte[] data ) 
		{
			md.update( data );
		}

		public void 
		update( 
			byte[] data, int pos, int len ) 
		{
			md.update( data, pos, len );
		}

		public byte[] 
		getDigest() 
		{
			return( md.digest());
		}
	}
	
	private static class
	LocalImpl
		implements Impl
	{
	  private final SHA1 sha1;

	  private LocalImpl() {
	    sha1 = new SHA1();
	  }

	  public byte[] calculateHash( byte[] bytes ) {
	    ByteBuffer buff = ByteBuffer.wrap( bytes );
	    return calculateHash( buff );
	  }
	 
	  public byte[] calculateHash( ByteBuffer buffer ) {
	    sha1.reset();
	    return sha1.digest( buffer );
	  }
 
	  public void update( byte[] data ) {
	  	update( ByteBuffer.wrap( data ));
	  }

	  public void update( byte[] data, int pos, int len ) {
	  	update( ByteBuffer.wrap( data, pos, len ));
	  }

	  public void update( ByteBuffer buffer ) {
	    sha1.update( buffer );
	  }

	  public byte[] getDigest() {
	  	return sha1.digest();
	  }
	}
}
