/*
 * Created on 20 Jun 2006
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

package com.biglybt.pifimpl.local.utils.security;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.biglybt.core.Core;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.security.CryptoManagerException;
import com.biglybt.core.security.CryptoSTSEngine;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.pif.messaging.MessageException;
import com.biglybt.pif.messaging.generic.GenericMessageConnection;
import com.biglybt.pif.messaging.generic.GenericMessageConnectionListener;
import com.biglybt.pif.messaging.generic.GenericMessageEndpoint;
import com.biglybt.pif.messaging.generic.GenericMessageStartpoint;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pif.utils.security.SEPublicKey;
import com.biglybt.pif.utils.security.SEPublicKeyLocator;
import com.biglybt.pif.utils.security.SESecurityManager;
import com.biglybt.pifimpl.local.messaging.GenericMessageConnectionImpl;
import com.biglybt.pifimpl.local.utils.PooledByteBufferImpl;

public class
SESTSConnectionImpl
	implements GenericMessageConnection
{
	private static final int	CRYPTO_SETUP_TIMEOUT	= 60*1000;

	private static final LogIDs LOGID = LogIDs.NWMAN;

	private static final byte[]		AES_IV1				=
   	{ 	(byte)0x15, (byte)0xE0, (byte)0x6B, (byte)0x7E, (byte)0x98, (byte)0x59, (byte)0xE4, (byte)0xA7,
		(byte)0x34, (byte)0x66, (byte)0xAD, (byte)0x48, (byte)0x35, (byte)0xE2, (byte)0xD0, (byte)0x24 };

	private static final byte[]		AES_IV2				= {
		(byte)0xC4, (byte)0xEF, (byte)0x06, (byte)0x3C, (byte)0x98, (byte)0x23, (byte)0xE8, (byte)0xB4,
		(byte)0x26, (byte)0x58, (byte)0xAE, (byte)0xB9, (byte)0x2C, (byte)0x24, (byte)0xB6, (byte)0x11 };

	private final int	AES_KEY_SIZE_BYTES = AES_IV1.length;


	private static List<SESTSConnectionImpl>					connections	= new ArrayList<>();

	static{

		SimpleTimer.addPeriodicEvent(
			"SESTSConnectionTimer",
			15*1000,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event )
				{
					List<SESTSConnectionImpl>	to_close = new ArrayList<>();

					synchronized( connections ){

						for (int i=0;i<connections.size();i++){

							SESTSConnectionImpl connection = connections.get(i);

							if ( connection.crypto_complete.isReleasedForever()){

								continue;
							}

							long	now = SystemTime.getCurrentTime();

							if ( connection.create_time > now ){

								connection.create_time = now;

							}else{

								int time_allowed = connection.getConnectMethodCount() * CRYPTO_SETUP_TIMEOUT;

								if ( now - connection.create_time > time_allowed ){

									to_close.add( connection );
								}
							}
						}
					}

					for (int i=0;i<to_close.size();i++){

						to_close.get(i).reportFailed( new Exception( "Timeout during crypto setup" ));
					}
				}

			});
	}

	private static AsyncDispatcher	dispatcher = new AsyncDispatcher( "SESTSAsync" );
	
	private static final int			BLOOM_RECREATE				= 30*1000;
	private static final int			BLOOM_INCREASE				= 500;
	private static BloomFilter			generate_bloom				= BloomFilterFactory.createAddRemove4Bit(BLOOM_INCREASE);
	private static long					generate_bloom_create_time	= SystemTime.getCurrentTime();


	private Core core;
	private GenericMessageConnectionImpl	connection;
	private SEPublicKey						my_public_key;
	private SEPublicKeyLocator				key_locator;
	private String							reason;
	private int								block_crypto;

	private long							create_time;

	private CryptoSTSEngine	sts_engine;

	private CopyOnWriteList<GenericMessageConnectionListener>	listeners = new CopyOnWriteList<>();

	private boolean		sent_keys;
	private boolean		sent_auth;

	private PooledByteBuffer	pending_message;

	private AESemaphore	crypto_complete	= new AESemaphore( "SESTSConnection:send" );

	private Cipher	outgoing_cipher;
	private Cipher	incoming_cipher;


	private volatile boolean	failed;

	protected
	SESTSConnectionImpl(
		Core _core,
		GenericMessageConnectionImpl	_connection,
		SEPublicKey						_my_public_key,
		SEPublicKeyLocator				_key_locator,
		String							_reason,
		int								_block_crypto )

		throws Exception
	{
		core			= _core;
		connection		= _connection;
		my_public_key	= _my_public_key;
		key_locator		= _key_locator;
		reason			= _reason;
		block_crypto	= _block_crypto;

		create_time = SystemTime.getCurrentTime();

		synchronized( connections ){

			connections.add( this );
		}

		if ( connection.isIncoming()){

			rateLimit( connection.getEndpoint().getNotionalAddress());
		}

		sts_engine	= core.getCryptoManager().getECCHandler( my_public_key.getInstance()).getSTSEngine( reason );

		connection.addListener(
			new GenericMessageConnectionListener()
			{
				@Override
				public void
				connected(
					GenericMessageConnection	connection )
				{
					reportConnected();
				}

				@Override
				public void
				receive(
					GenericMessageConnection	connection,
					PooledByteBuffer			message )

					throws MessageException
				{
					SESTSConnectionImpl.this.receive( message );
				}

				@Override
				public void
				failed(
					GenericMessageConnection	connection,
					Throwable 					error )

					throws MessageException
				{
					reportFailed( error );
				}
			});
	}

	@Override
	public GenericMessageStartpoint 
	getStartpoint()
	{
		return( connection.getStartpoint());
	}
	
	@Override
	public Connection
	getConnection()
	{
		return( connection.getConnection());
	}
	
	protected int
	getConnectMethodCount()
	{
		return( connection.getConnectMethodCount());
	}

	protected static void
	rateLimit(
		InetSocketAddress	originator )

		throws Exception
	{
		synchronized( SESTSConnectionImpl.class ){

			int	hit_count = generate_bloom.add( AddressUtils.getAddressBytes( originator ));

			long	now = SystemTime.getCurrentTime();

				// allow up to 10% bloom filter utilisation

			if ( generate_bloom.getSize() / generate_bloom.getEntryCount() < 10 ){

				generate_bloom = BloomFilterFactory.createAddRemove4Bit(generate_bloom.getSize() + BLOOM_INCREASE );

				generate_bloom_create_time	= now;

	     		Logger.log(	new LogEvent(LOGID, "STS bloom: size increased to " + generate_bloom.getSize()));

			}else if ( now < generate_bloom_create_time || now - generate_bloom_create_time > BLOOM_RECREATE ){

				generate_bloom = BloomFilterFactory.createAddRemove4Bit(generate_bloom.getSize());

				generate_bloom_create_time	= now;
			}

			if ( hit_count >= 15 ){

	     		Logger.log(	new LogEvent(LOGID, "STS bloom: too many recent connection attempts from " + originator ));

	     		Debug.out( "STS: too many recent connection attempts from " + originator );

				throw( new IOException( "Too many recent connection attempts (sts)"));
			}
		}
	}

	@Override
	public GenericMessageEndpoint
	getEndpoint()
	{
		return( connection.getEndpoint());
	}

	@Override
	public int
	getMaximumMessageSize()
	{
		int	max = connection.getMaximumMessageSize();

		if ( outgoing_cipher != null ){

			max -= outgoing_cipher.getBlockSize();
		}

		return( max );
	}

	@Override
	public String
	getType()
	{
		String	con_type = connection.getType();

		if ( con_type.length() == 0 ){

			return( "" );
		}

		return( "AES " + con_type );
	}

	@Override
	public int
	getTransportType()
	{
		return( connection.getTransportType());
	}

	@Override
	public void
	addInboundRateLimiter(
		RateLimiter		limiter )
	{
		connection.addInboundRateLimiter( limiter );
	}

	@Override
	public void
	removeInboundRateLimiter(
		RateLimiter		limiter )
	{
		connection.removeInboundRateLimiter( limiter );
	}

	@Override
	public void
	addOutboundRateLimiter(
		RateLimiter		limiter )
	{
		connection.addOutboundRateLimiter( limiter );
	}

	@Override
	public void
	removeOutboundRateLimiter(
		RateLimiter		limiter )
	{
		connection.removeOutboundRateLimiter( limiter );
	}

	@Override
	public void
	connect(
		GenericMessageConnection.GenericMessageConnectionPropertyHandler		ph )

		throws MessageException
	{
		if ( connection.isIncoming()){

			connection.connect( ph );

		}else{

			try{
				ByteBuffer	buffer = ByteBuffer.allocate( 32*1024 );

				sts_engine.getKeys( buffer );

				buffer.flip();

				sent_keys = true;

				connection.connect( buffer, ph );

			}catch( CryptoManagerException	e ){

				throw( new MessageException( "Failed to get initial keys", e ));
			}
		}
	}

	protected void
	setFailed()
	{
		failed	= true;

		try{
			cryptoComplete();

		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	public void
	receive(
		PooledByteBuffer			message )

		throws MessageException
	{
		try{
			boolean	forward				= false;
			boolean	crypto_completed	= false;

			ByteBuffer	out_buffer = null;

			synchronized( this ){

				if ( crypto_complete.isReleasedForever()){

					forward	= true;

				}else{

						// basic sts flow:
						//   a -> puba -> b
						//   a <- pubb <- b
						//   a -> auta -> b
						//	 a <- autb <- b
						//   a -> data -> b

						// optimised

						//  a -> puba 		 -> b
						//  a <- pubb + auta <- b
						//  a -> autb + data -> b

						// therefore can be one or two messages in the payload
						// 	  1 crypto
						//    2 crypto (pub + auth)
						//	  crypto + data

						// initial a ->puba -> is done on first data send so data is ready for phase 3

					ByteBuffer	in_buffer = ByteBuffer.wrap( message.toByteArray());

					message.returnToPool();

						// piggyback pub key send

					if ( !sent_keys ){

							// we've received
							//		a -> puba -> b
							// reply with
							//		a <- puba + auta <- b

						out_buffer = ByteBuffer.allocate( 64*1024 );

							// write our keys

						sts_engine.getKeys( out_buffer );

						sent_keys	= true;

							// read their keys

						sts_engine.putKeys( in_buffer );

							// write our auth

						//System.out.println( "auth " + connection.getEndpoint().getNotionalAddress() );
						
						sts_engine.getAuth( out_buffer );

						sent_auth 	= true;

					}else if ( !sent_auth ){

						out_buffer = ByteBuffer.allocate( 64*1024 );

						// we've received
						//		a <- puba + auta <- b
						// reply with
						//		a -> autb + data -> b

							// read their keys

						sts_engine.putKeys( in_buffer );

							// write our auth

						sts_engine.getAuth( out_buffer );

						sent_auth = true;

							// read their auth

						sts_engine.putAuth( in_buffer );

							// check we wanna talk to this person

						byte[]	rem_key = sts_engine.getRemotePublicKey();

						if ( !key_locator.accept(
								SESTSConnectionImpl.this,
								new SEPublicKeyImpl( my_public_key.getType(), my_public_key.getInstance(), rem_key ))){

							throw( new MessageException( "remote public key not accepted" ));
						}

						setupBlockCrypto();

						if ( pending_message != null ){

							byte[]	pending_bytes = pending_message.toByteArray();

							int	pending_size = pending_bytes.length;

							if ( outgoing_cipher != null ){

								pending_size =  (( pending_size + AES_KEY_SIZE_BYTES -1 )/AES_KEY_SIZE_BYTES)*AES_KEY_SIZE_BYTES;

								if ( pending_size == 0 ){

									pending_size = AES_KEY_SIZE_BYTES;
								}
							}

							if ( out_buffer.remaining() >= pending_size ){

								if ( outgoing_cipher != null ){


									out_buffer.put( outgoing_cipher.doFinal( pending_bytes ));

								}else{

									out_buffer.put( pending_bytes );
								}

									// don't deallocate the pending message, the original caller does this

								pending_message	= null;
							}
						}

						crypto_completed	= true;

					}else{
							// we've received
							//		a -> autb + data -> b

							// read their auth

						sts_engine.putAuth( in_buffer );

							// check we wanna talk to this person

						byte[]	rem_key = sts_engine.getRemotePublicKey();

						if ( !key_locator.accept(
								SESTSConnectionImpl.this,
								new SEPublicKeyImpl( my_public_key.getType(), my_public_key.getInstance(), rem_key ))){

								// this is just here to prevent unwanted spew  during closedown process

							connection.closing();

							throw( new MessageException( "remote public key not accepted" ));
						}

						setupBlockCrypto();

						crypto_completed	= true;

							// pick up any remaining data for delivery

						if ( in_buffer.hasRemaining()){

							message = new PooledByteBufferImpl( new DirectByteBuffer( in_buffer.slice()));

							forward	= true;
						}
					}
				}
			}

			if ( out_buffer != null ){

				out_buffer.flip();

				connection.send( new PooledByteBufferImpl( new DirectByteBuffer( out_buffer )));
			}

			if ( crypto_completed ){

				cryptoComplete();
			}
			if ( forward ){

				receiveContent( message );
			}
		}catch( Throwable e ){

			reportFailed( e );

			if ( e instanceof MessageException ){

				throw((MessageException)e);

			}else{

				throw( new MessageException( "Receive failed", e ));
			}
		}
	}

	protected void
	setupBlockCrypto()

		throws MessageException
	{
		if ( !failed ){

			if ( block_crypto == SESecurityManager.BLOCK_ENCRYPTION_NONE ){

				return;
			}

			try{
				byte[]	shared_secret = sts_engine.getSharedSecret();

			    SecretKeySpec	secret_key_spec1 = new SecretKeySpec(shared_secret, 0, 16, "AES" );
			    SecretKeySpec	secret_key_spec2 = new SecretKeySpec(shared_secret, 8, 16, "AES" );

			    AlgorithmParameterSpec	param_spec1 = 	new IvParameterSpec( AES_IV1);
			    AlgorithmParameterSpec	param_spec2 = 	new IvParameterSpec( AES_IV2);

			    Cipher cipher1 = Cipher.getInstance( "AES/CBC/PKCS5Padding" );
			    Cipher cipher2 = Cipher.getInstance( "AES/CBC/PKCS5Padding" );

			    if ( connection.isIncoming()){

			        cipher1.init( Cipher.ENCRYPT_MODE, secret_key_spec1, param_spec1 );
			        cipher2.init( Cipher.DECRYPT_MODE, secret_key_spec2, param_spec2 );

			        incoming_cipher	= cipher2;
			        outgoing_cipher	= cipher1;

			    }else{

			        cipher1.init( Cipher.DECRYPT_MODE, secret_key_spec1, param_spec1 );
			        cipher2.init( Cipher.ENCRYPT_MODE, secret_key_spec2, param_spec2 );

			        incoming_cipher	= cipher1;
			        outgoing_cipher	= cipher2;
			    }

			}catch( Throwable e ){

				throw( new MessageException( "Failed to setup block encryption", e ));
			}
		}
	}

	protected void
	cryptoComplete()

		throws MessageException
	{
		crypto_complete.releaseForever();
	}

	@Override
	public void
	send(
		PooledByteBuffer			message )

		throws MessageException
	{
		if ( failed ){

			throw( new MessageException( "Connection failed" ));
		}

		try{
			if ( crypto_complete.isReleasedForever()){

				sendContent( message );

			}else{

					// not complete, stash the message so it has a chance of being piggybacked on
					// the crypto protocol exchange

				synchronized( this ){

					if ( pending_message == null ){

						pending_message = message;
					}
				}
			}

			crypto_complete.reserve();

				// if the pending message couldn't be piggy backed it'll still be allocated

			boolean	send_it = false;

			synchronized( this ){

				if ( pending_message == message ){

					pending_message	= null;

					send_it	= true;
				}
			}

			if ( send_it ){

				sendContent( message );
			}

		}catch( Throwable e ){

			setFailed();

			if ( e instanceof MessageException ){

				throw((MessageException)e);

			}else{

				throw( new MessageException( "Send failed", e ));
			}
		}
	}

	protected void
	sendContent(
		PooledByteBuffer			message )

		throws MessageException
	{
		if ( outgoing_cipher != null ){

			try{
				byte[]	plain	=  message.toByteArray();
				byte[]	enc		= outgoing_cipher.doFinal( plain );

				PooledByteBuffer	temp = new PooledByteBufferImpl( enc );

				try{
					connection.send( temp );

						// successfull send -> release caller's buffer

					message.returnToPool();

				}catch( Throwable e ){

						// failed semantics are to not release the caller's buffer

					temp.returnToPool();

					throw( e );
				}

			}catch( Throwable e ){

				throw( new MessageException( "Failed to encrypt data", e ));
			}
		}else{
				// sanity check - never allow unencrypted outbound if block enc selected

			if ( block_crypto != SESecurityManager.BLOCK_ENCRYPTION_NONE ){

				connection.close();

				throw( new MessageException( "Crypto isn't setup" ));
			}

			connection.send( message );
		}
	}

	protected void
	receiveContent(
		PooledByteBuffer			message )

		throws MessageException
	{
		boolean	buffer_handled = false;

		try{
			if ( incoming_cipher != null ){

				try{
					byte[]	enc 	= message.toByteArray();
					byte[]	plain 	= incoming_cipher.doFinal( enc );

					PooledByteBuffer	temp = new PooledByteBufferImpl( plain );

					message.returnToPool();

					buffer_handled	= true;

					message	= temp;

				}catch( Throwable e ){

					throw( new MessageException( "Failed to decrypt data", e ));
				}

			}else if ( block_crypto != SESecurityManager.BLOCK_ENCRYPTION_NONE ){

				throw( new MessageException( "Crypto isn't setup" ));
			}

			List<GenericMessageConnectionListener> listeners_ref = listeners.getList();

			MessageException	last_error = null;

			for (int i=0;i<listeners_ref.size();i++){

				PooledByteBuffer	message_to_deliver;

				if ( i == 0 ){

					message_to_deliver	= message;

				}else{

						// unlikely we'll ever have > 1 receiver....

					message_to_deliver = new PooledByteBufferImpl( message.toByteArray());
				}

				try{
					listeners_ref.get(i).receive( this, message_to_deliver );

					if ( message_to_deliver == message ){

						buffer_handled	= true;
					}
				}catch( Throwable e ){

					message_to_deliver.returnToPool();

					if ( message_to_deliver == message ){

						buffer_handled	= true;
					}

					if ( e instanceof MessageException ){

						last_error = (MessageException)e;

					}else{

						last_error = new MessageException( "Failed to process message", e );
					}
				}
			}

			if ( last_error != null ){

				throw( last_error );
			}
		}finally{

			if ( !buffer_handled ){

				message.returnToPool();
			}
		}
	}

	@Override
	public void
	close()

		throws MessageException
	{
		synchronized( connections ){

			connections.remove( this );
		}

		connection.close();
	}

	protected void
	reportConnected()
	{
			// we've got to take this off the current thread to avoid the connection event causing immediate
			// submission of a message which then block this thread awaiting crypto completion. "this" thread
			// is currently the selector thread which then screws the crypto protocol...

		dispatcher.dispatch( AERunnable.create(()->{
			
			List<GenericMessageConnectionListener> listeners_ref = listeners.getList();

			for (int i=0;i<listeners_ref.size();i++){

				try{
					listeners_ref.get(i).connected( SESTSConnectionImpl.this );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}));
	}

	protected void
	reportFailed(
		final Throwable	error )
	{
		setFailed();

		dispatcher.dispatch( AERunnable.create(()->{
			try{
				List<GenericMessageConnectionListener> listeners_ref = listeners.getList();

				for (int i=0;i<listeners_ref.size();i++){

					try{
						listeners_ref.get(i).failed( SESTSConnectionImpl.this, error );

					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}
			}finally{

				try{
					close();

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}));
	}

	@Override
	public void
	addListener(
		GenericMessageConnectionListener		listener )
	{
		listeners.add( listener );
	}

	@Override
	public void
	removeListener(
		GenericMessageConnectionListener		listener )
	{
		listeners.remove( listener );
	}
}
