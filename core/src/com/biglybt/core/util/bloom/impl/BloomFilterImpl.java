/*
 * Created on 29-Apr-2005
 * Created by Paul Gardner
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

package com.biglybt.core.util.bloom.impl;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.util.MapUtils;


public abstract class
BloomFilterImpl
	implements BloomFilter
{
	protected static final String MY_PACKAGE = "com.biglybt.core.util.bloom.impl";

	/*
	private static final boolean	USE_BIG_INTS	= false;
	private static final char[]	HEX_CHARS = "0123456789ABCDEF".toCharArray();
	private static final BigInteger	bi_zero		= new BigInteger("0");
	private static final BigInteger	bi_a2		= new BigInteger(""+a2);
	private static final BigInteger	bi_a3		= new BigInteger(""+a3);
	private static final BigInteger	bi_a4		= new BigInteger(""+a4);

	private static final BigInteger	bi_b2		= new BigInteger(""+b2);
	private static final BigInteger	bi_b3		= new BigInteger(""+b3);
	private static final BigInteger	bi_b4		= new BigInteger(""+b4);
	*/

		// change the hash num and you gotta change the hash function below!!!!

	private static final int	HASH_NUM	= 5;

	private static final int	a2		= 2;
	private static final int	a3		= 3;
	private static final int	a4		= 5;

	private static final int	b2		= 51;
	private static final int	b3		= 145;
	private static final int	b4		= 216;

	public static BloomFilter
	deserialiseFromMap(
		Map<String,Object>	map )
	{
		String	impl = MapUtils.getMapString( map, "_impl", "" );

		if ( impl.startsWith( "." )){

			impl = MY_PACKAGE + impl;
		}

		try{
			Class<BloomFilterImpl> cla = (Class<BloomFilterImpl>)Class.forName( impl );

			Constructor<BloomFilterImpl> cons = cla.getDeclaredConstructor( Map.class );

			cons.setAccessible( true );

			return( cons.newInstance( map ));

		}catch( Throwable e ){

			Debug.out( "Can't construct bloom filter for " + impl, e );

			return( null );
		}
	}


	private final int			max_entries;
	//private BigInteger	bi_max_entries;

	private int			entry_count;

	private long		start_time = SystemTime.getMonotonousTime();

	public
	BloomFilterImpl(
		int		_max_entries )
	{
		//bi_max_entries	= new BigInteger( ""+(((_max_entries/2)*2)+1));

		max_entries	= ((_max_entries/2)*2)+1;
	}

	public
	BloomFilterImpl(
		Map<String,Object>		x )
	{
		max_entries = ((Long)x.get( "_max" )).intValue();

		entry_count = ((Long)x.get( "_count" )).intValue();
	}
	protected void
	serialiseToMap(
		Map<String,Object>		x )
	{
		String	cla = this.getClass().getName();

		if ( cla.startsWith( MY_PACKAGE )){

			cla = cla.substring( MY_PACKAGE.length());
		}

		x.put( "_impl", cla );

		x.put( "_max", new Long( max_entries ));
		x.put( "_count", new Long( entry_count ));
	}

	@Override
	public Map<String, Object>
	serialiseToMap()
	{
		Map<String, Object>  m = new HashMap<>();

		serialiseToMap( m );

		return( m );
	}

	protected int
	getMaxEntries()
	{
		return( max_entries );
	}

	@Override
	public int
	add(
		byte[]		value )
	{
		return( add( bytesToInteger( value )));
	}

	@Override
	public int
	remove(
		byte[]		value )
	{
		return( remove( bytesToInteger( value )));
	}

	@Override
	public int
	count(
		byte[]		value )
	{
		return( count( bytesToInteger( value )));
	}

	@Override
	public boolean
	contains(
		byte[]		value )
	{
		return( contains( bytesToInteger( value )));
	}



	protected int
	add(
		int		value )
	{
		int	count = 0xffff;

		for (int i=0;i<HASH_NUM;i++){

			int	index = getHash( i, value );

				// v = value BEFORE inc

			int	v = incValue( index );

			if ( v < count ){

				count = v;
			}
		}

		if ( count == 0 ){

			entry_count++;
		}

			// count is the smallest val found *before* incrementing

		return( trimValue( count + 1 ));
	}

	protected int
	remove(
		int		value )
	{
		int	count = 0xffff;

		for (int i=0;i<HASH_NUM;i++){

			int	index = getHash( i, value );

				// v = value BEFORE dec

			int	v = decValue( index );

			if ( v < count ){

				count = v;
			}
		}

		if ( count == 1 && entry_count > 0 ){

			entry_count--;
		}

			// count is the value BEFORE dec, decrease one further

		return( trimValue( count - 1 ));
	}

	protected int
	count(
		int		value )
	{
		int	count = 0xffff;

		for (int i=0;i<HASH_NUM;i++){

			int	index = getHash( i, value );

			int	v = getValue( index );

			if ( v < count ){

				count	= v;
			}
		}

		return( count );
	}

	protected boolean
	contains(
		int		value )
	{
		for (int i=0;i<HASH_NUM;i++){

			int	index = getHash( i, value );

			int	v = getValue( index );

			if ( v == 0 ){

				return( false );
			}
		}

		return( true );
	}


	protected abstract int
	getValue(
		int		index );


	protected abstract int
	incValue(
		int		index );

	protected abstract int
	decValue(
		int		index );

	protected abstract int
	trimValue(
		int		value );

	protected int
	getHash(
		int			function,
		int			value )
	{
		long	res;

		switch( function ){
			case 0:
			{
					// x mod p

				res = value;

				break;
			}
			case 1:
			{
				 	// x^2 mod p

				res	= value * value;	// don't try and *fix* this to avoid integer overflow as bloom filters are serialised and this would screw up the hash...

				break;
			}
			case 2:
			{
					// bx + a mod p

				res = value *  a2 + b2;	// as above

				break;
			}
			case 3:
			{
					// cx + d mod p

				res = value * a3 + b3; 	// as above

				break;

			}
			case 4:
			{
					// ex + f mod p

				res = value * a4 + b4; 	// as above

				break;
			}
			default:
			{
				System.out.println( "**** BloomFilter hash function doesn't exist ****" );

				res = 0;
			}
		}

		// System.out.println( "hash[" + function + "] " + value + "->" + r );

		return( Math.abs( (int)res % max_entries ));
	}

	protected int
	bytesToInteger(
		byte[]		data )
	{
		int	res = 0x51f7ac81;

		for (int i=0;i<data.length;i++){

			//res ^= (data[i]&0xff)<<((i%4)*8);

			res = res * 191 + (data[i]&0xff);
		}

		return( res );
	}


	/*
	protected int
	add(
		BigInteger		value )
	{
		entry_count++;

		for (int i=0;i<HASH_NUM;i++){

			int	index = getHash( i, value );

			byte	v = getValue( index );

			if ( v < 15 ){

				setValue( index, (byte)(v+1));
			}
		}
	}

	public void
	remove(
		BigInteger		value )
	{
		entry_count--;

		if ( entry_count < 0 ){

			entry_count	= 0;
		}

		for (int i=0;i<HASH_NUM;i++){

			int	index = getHash( i, value );

			byte	v = getValue( index );

			if ( v > 0 ){

				setValue( index, (byte)(v-1));
			}
		}
	}

	public boolean
	contains(
		BigInteger		value )
	{
		for (int i=0;i<HASH_NUM;i++){

			int	index = getHash( i, value );

			int	v = getValue( index );

			if ( v == 0 ){

				return( false );
			}
		}

		return( true );
	}

		protected int
	getHash(
		int			function,
		BigInteger	value )
	{
		BigInteger	res;

		switch( function ){
			case 0:
			{
					// x mod p

				res = value;

				break;
			}
			case 1:
			{
				 	// x^2 mod p

				res	= value.pow(2);

				break;
			}
			case 2:
			{
					// bx + a mod p

				res = value.multiply( bi_a2 ).add( bi_b2 );

				break;
			}
			case 3:
			{
					// cx + d mod p

				res = value.multiply( bi_a3 ).add( bi_b3 );

				break;

			}
			case 4:
			{
					// ex + f mod p

				res = value.multiply( bi_a4 ).add( bi_b4 );

				break;
			}
			default:
			{
				System.out.println( "**** BloomFilter hash function doesn't exist ****" );

				res = new BigInteger("0");
			}
		}

		int	r = 0;

		while(true){

			BigInteger	x = res.divide( bi_max_entries );

			if ( x.compareTo( bi_zero) == 0 ){

				r += res.mod( bi_max_entries ).intValue();

				break;
			}

			r += x.mod( bi_max_entries ).intValue();

			res = x;
		}


		// System.out.println( "hash[" + function + "] " + value + "->" + r );

		return( r % max_entries );
	}

	protected BigInteger
	bytesToBigInteger(
		byte[]		data )
	{
		StringBuffer	buffer = new StringBuffer(data.length*2);

		for (int i=0;i<data.length;i++){

			buffer.append( HEX_CHARS[(data[i]>>4)&0x0f] );

			buffer.append( HEX_CHARS[data[i]&0x0f] );
		}

		BigInteger	res		= new BigInteger( new String(buffer), 16 );

		return( res );
	}
	*/


	@Override
	public int
	getEntryCount()
	{
		return( entry_count );
	}

	@Override
	public int
	getSize()
	{
		return( max_entries );
	}

	@Override
	public void
	clear()
	{
		start_time  = SystemTime.getMonotonousTime();

		entry_count = 0;
	}

	@Override
	public long
	getStartTimeMono()
	{
		return( start_time );
	}

	protected static byte[]
	getSerialization(
		byte[]	address,
	    int		port )
	{
		//combine address and port bytes into one
		byte[] full_address = new byte[ address.length +2 ];
		System.arraycopy( address, 0, full_address, 0, address.length );
		full_address[ address.length ] = (byte)(port >> 8);
		full_address[ address.length +1 ] = (byte)(port & 0xff);
		return full_address;
	}

	@Override
	public String
	getString()
	{
		return( "ent=" + entry_count + ",max=" + max_entries );
	}

	public static void
	main(
		String[]	args )
	{
		Random	rand = new Random();

		/*
		BloomFilter b1 = new BloomFilterAddRemove8Bit(10000);

		for (int i=0;i<260;i++){

			System.out.println( b1.add( "parp".getBytes()) + ", count = " + b1.count( "parp".getBytes()) + ", ent = " + b1.getEntryCount());

		}

		for (int i=0;i<260;i++){

			System.out.println( b1.remove( "parp".getBytes())+ ", count = " + b1.count( "parp".getBytes()) + ", ent = " + b1.getEntryCount());
		}
		*/

		/*

		BloomFilter b1 = new BloomFilterAddOnly(90*10/3);

		byte[]	key1 = new byte[4];

		for (int i=0;i<200;i++){


			if ( i%2==0){
				rand.nextBytes( key1 );
			}

			b1.add( key1 );

			System.out.println( "entries = " + b1.getEntryCount() + ", act = " + i );
		}

		System.exit(0);
		*/


		int	fp_count = 0;

		for (int j=0;j<1000;j++){

			long	start = System.currentTimeMillis();

			BloomFilter b = new BloomFilterAddRemove8Bit(10000);
			//BloomFilter b = new BloomFilterAddOnly(10000);

			int	fp = 0;

			for (int i=0;i<1000;i++){

				//String	key = "" + rand.nextInt();

				byte[]	key = new byte[6];

				rand.nextBytes( key );

				//key  = getSerialization( key, 6881 );

				if ( i%2 == 0 ){

					b.add( key  );

					if ( !b.contains( key )){

						System.out.println( "false negative on add!!!!" );
					}
				}else{

					if ( b.contains( key )){

						fp++;
					}
				}

				/*
				if ( i%2 == 0 ){

					b.remove( key  );

					if ( b.contains( key )){

						System.out.println( "false positive" );
					}
				}
				*/
			}

			System.out.println( "" + (System.currentTimeMillis() - start + ", fp = " + fp ));

			if ( fp > 0 ){

				fp_count++;
			}
		}

		System.out.println( fp_count );
	}
}
