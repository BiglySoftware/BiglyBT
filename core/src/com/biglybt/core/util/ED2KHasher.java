/*
 * File    : ED2KHasher.java
 * Created : 16-Feb-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.util;


/**
 * @author parg
 *
 */

public class
ED2KHasher
{
	public static final int	BLOCK_SIZE = 0x947000;

	protected MD4Hasher	current_hasher	= new MD4Hasher();
	protected MD4Hasher	block_hasher;

	protected int	current_bytes;

	public
	ED2KHasher()
	{

	}

	public void
	update(
		byte[]		data )
	{
		update( data, 0, data.length );
	}

	public void
	update(
		byte[]		data,
		int			pos,
		int			len )
	{
		int		rem = len;

		while( rem > 0 ){

			int	space = BLOCK_SIZE - current_bytes;

			if ( rem <= space ){

				current_hasher.update( data, pos, rem );

				current_bytes += rem;

				break;

			}else{

				if ( block_hasher == null ){

					block_hasher = new MD4Hasher();
				}

				if ( space == 0 ){

					block_hasher.update( current_hasher.getDigest());

					current_hasher = new MD4Hasher();

					current_bytes = 0;

				}else{

					current_hasher.update( data, pos, space );

					pos 			+= space;
					rem				-= space;
					current_bytes	+= space;
				}
			}
		}
	}

	public byte[]
	getDigest()
	{
			// data that is a multiple of BLOCK_SIZE needs to have a null MD4 hash appended

		if ( current_bytes == BLOCK_SIZE ){

			if ( block_hasher == null ){

				block_hasher = new MD4Hasher();
			}

			block_hasher.update( current_hasher.getDigest());

			current_hasher = new MD4Hasher();
		}

		if ( block_hasher == null ){

			return( current_hasher.getDigest());

		}else{

			if ( current_bytes > 0 ){

				block_hasher.update( current_hasher.getDigest());
			}

			return( block_hasher.getDigest());
		}
	}

	/*

	public static void
	main(
		String[]	args )
	{
		SESecurityManager.initialise();

		ED2KHasher	hasher = new ED2KHasher();

		try{
			FileInputStream	fis = new FileInputStream( "C:\\temp\\dat.txt");

			byte[]	buffer = new byte[1024*1024];

			while( true ){

				int	len = fis.read( buffer );

				if ( len <= 0 ){

					break;
				}

				hasher.update( buffer, 0, len );

			}
		}catch( Throwable e ){

			e.printStackTrace();
		}

		byte[]	bah = new byte[BLOCK_SIZE];

		Arrays.fill( bah, (byte)'a' );

		hasher.update( bah );




		try{
			FileOutputStream	fos = new FileOutputStream( "C:\\temp\\data.txt" );

			fos.write( bah );

			fos.close();

		}catch( Throwable e ){

			e.printStackTrace();
		}


		System.out.println( "hash=" + ByteFormatter.encodeString( hasher.getDigest()));
	}
	*/

}
