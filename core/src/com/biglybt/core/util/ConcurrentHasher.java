/*
 * Created on 09-Sep-2004
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

package com.biglybt.core.util;

/**
 * @author parg
 *
 */

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;

public class
ConcurrentHasher
{

	protected static final ConcurrentHasher		singleton	= new ConcurrentHasher();

	protected int			processor_num;

	protected final List<ConcurrentHasherRequest>				requests		= new LinkedList<>();

	protected final List<SHA1Hasher>	v1_hashers			= new LinkedList<>();
	protected final List<MessageDigest>	v2_hashers			= new LinkedList<>();

	protected final AESemaphore		request_sem		= new AESemaphore("ConcHashReqQ");
	protected final AESemaphore		scheduler_sem	= new AESemaphore("ConcHashSched");

	protected final AEMonitor			requests_mon	= new AEMonitor( "ConcurrentHasher:R" );

	private static boolean friendly_hashing;

	static{
		COConfigurationManager.addAndFireParameterListener( "diskmanager.hashchecking.strategy", new ParameterListener() {
			@Override
			public void parameterChanged(String  str ) {
				friendly_hashing = COConfigurationManager.getIntParameter( "diskmanager.hashchecking.strategy" ) == 0;
			}
		});
	}



	public static ConcurrentHasher
	getSingleton()
	{
		return( singleton );
	}

	public static boolean
	concurrentHashingAvailable()
	{
		return( getSingleton().processor_num > 1 );
	}

	protected
	ConcurrentHasher()
	{
			// TODO: number of processors can apparently change....
			// so periodically grab num + reserve/release as necessary

		processor_num = Runtime.getRuntime().availableProcessors();

			// just in case :P

		if ( processor_num <= 0 ){

			processor_num	= 1;
		}

			// triple keeps processors pretty busy

		for (int i=0;i<processor_num*3;i++){

			scheduler_sem.release();
		}

		final ThreadPool pool	= new ThreadPool( "ConcurrentHasher", processor_num<64?64:128 );

		new AEThread2("ConcurrentHasher:scheduler", true )
		{
			@Override
			public void
			run()
			{
				while(true){

						// get a request to run

					request_sem.reserve();

						// now extract the request

					final ConcurrentHasherRequest	req;
					final SHA1Hasher				v1_hasher;
					final MessageDigest				v2_hasher;

					try{
						requests_mon.enter();

						req	= requests.remove(0);

						if ( req.getHashVersion() == 1 ){
							
							v2_hasher = null;
							
							if ( v1_hashers.size() == 0 ){
	
								v1_hasher = new SHA1Hasher();
	
							}else{
	
								v1_hasher	= v1_hashers.remove(0);
							}
						}else{
							v1_hasher = null;
							
							if ( v2_hashers.size() == 0 ){
	
								try{
									v2_hasher = MessageDigest.getInstance( "SHA-256" );
									
								}catch( Throwable e ){
									
									Debug.out( e );
									
									req.cancel();
									
									continue;
								}
	
							}else{
	
								v2_hasher	= v2_hashers.remove(0);
							}
						}
					}finally{

						requests_mon.exit();
					}

					pool.run(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									try{
										if ( v1_hasher != null ){
										
											req.run( v1_hasher );
										}else{
											
											req.run( v2_hasher );
										}
									}finally{
										try{
											requests_mon.enter();

											if ( v1_hasher != null ){
											
												v1_hashers.add( v1_hasher );
												
											}else{
												
												v2_hashers.add( v2_hasher );
											}
										}finally{

											requests_mon.exit();
										}

										if ( friendly_hashing && req.isLowPriority()){

											try{
												int	size = req.getSize();

													// pieces can be several MB so delay based on size

												final int max = 250;
												final int min = 50;

												size = size/1024;	// in K

												size = size/8;

													// 4MB -> 500
													// 1MB -> 125

												size = Math.min( size, max );
												size = Math.max( size, min );

												Thread.sleep( size );

											}catch( Throwable e ){

												Debug.printStackTrace( e );
											}
										}

										scheduler_sem.release();
									}
								}
							});

				}
			}
		}.start();
	}

		/**
		 * add a synchronous request - on return it will have run (or been cancelled)
	     */

	public ConcurrentHasherRequest
	addRequest(
		ByteBuffer		buffer,
		int				hash_version,
		int				piece_size,
		long			v2_file_size )
	{
		return( addRequest( buffer, hash_version, piece_size, v2_file_size, null, false ));
	}

		/**
		 * Add an asynchronous request if listener supplied, sync otherwise
		 * @param buffer
		 * @param priority
		 * @param listener
		 * @param low_priorty low priority checks will cause the "friendly hashing" setting to be
		 * taken into account
		 * @return
		 */

	public ConcurrentHasherRequest
	addRequest(
		ByteBuffer							buffer,
		int									hash_version,
		int									piece_size,
		long								v2_file_size,
		ConcurrentHasherRequestListener		listener,
		boolean								low_priorty )
	{
		final ConcurrentHasherRequest	req = new ConcurrentHasherRequest( this, buffer, hash_version, piece_size, v2_file_size, listener, low_priorty );

			// get permission to run a request


		// test code to force synchronous checking
		//SHA1Hasher	hasher = new SHA1Hasher();
		//req.run( hasher );

		scheduler_sem.reserve();

		try{
			requests_mon.enter();

			requests.add( req );

		}finally{

			requests_mon.exit();
		}

		request_sem.release();

		return( req );
	}

	public static void
	main(
		String[]	args )
	{
		/*
		final ConcurrentHasher	hasher = ConcurrentHasher.getSingleton();

		int		threads			= 1;

		final long	buffer_size		= 128*1024;
		final long	loop			= 1024;

		for (int i=0;i<threads;i++){

			new Thread()
			{
				public void
				run()
				{
					// SHA1Hasher sha1_hasher = new SHA1Hasher();

					long	start = System.currentTimeMillis();
					//ByteBuffer	buffer = ByteBuffer.allocate((int)buffer_size);

					for (int j=0;j<loop;j++){


						//sha1_hasher.calculateHash( buffer );
						//ConcurrentHasherRequest req = hasher.addRequest( buffer );
					}

					long	elapsed = System.currentTimeMillis() - start;

					System.out.println(
							"elapsed = " + elapsed + ", " +
							((loop*buffer_size*1000)/elapsed) + " B/sec" );
				}
			}.start();
		}
		*/
	}
}
