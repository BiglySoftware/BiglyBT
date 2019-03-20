/*
 * Created on 15 Jun 2006
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

package com.biglybt.core.security.impl;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.gudy.bouncycastle.crypto.CipherParameters;
import org.gudy.bouncycastle.crypto.engines.RC4Engine;
import org.gudy.bouncycastle.crypto.params.KeyParameter;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.security.*;
import com.biglybt.core.util.*;

public class
CryptoManagerImpl
	implements CryptoManager
{
	private static final int 	PBE_ITERATIONS	= 100;
	private static final String	PBE_ALG			= "PBEWithMD5AndDES";

	private static CryptoManagerImpl		singleton;


	public static synchronized CryptoManager
	getSingleton()
	{
		if ( singleton == null ){

			singleton = new CryptoManagerImpl();
		}

		return( singleton );
	}

	private byte[]				secure_id;
	private final CopyOnWriteList		password_handlers	= new CopyOnWriteList();
	private final CopyOnWriteList		keychange_listeners	= new CopyOnWriteList();

	private final Map	session_passwords =	Collections.synchronizedMap( new HashMap());

	private final CryptoHandler		ecc_handler;

	private final Map<Integer,CryptoHandler>	ecc_handlers = new HashMap<>();
	
	protected
	CryptoManagerImpl()
	{
		SESecurityManager.initialise();

		long	now = SystemTime.getCurrentTime();

		for (int i=0;i<CryptoManager.HANDLERS.length;i++){

			int	handler = CryptoManager.HANDLERS[i];

			String persist_timeout_key 	= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_timeout";
			String persist_pw_key 		= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_value";

			long	timeout = COConfigurationManager.getLongParameter( persist_timeout_key, 0 );

			if ( now > timeout ){

				COConfigurationManager.setParameter( persist_timeout_key, 0 );
				COConfigurationManager.setParameter( persist_pw_key, "" );

			}else{

				addPasswordTimer( persist_timeout_key, persist_pw_key, timeout );
			}
		}

		ecc_handler = new CryptoHandlerECC( this, 1 );
		
		ecc_handlers.put( 1, ecc_handler );
	}

	protected void
	addPasswordTimer(
		final String		timeout_key,
		final String		pw_key,
		final long			timeout )
	{
		if ( timeout == Long.MAX_VALUE ){

			return;
		}

		SimpleTimer.addEvent(
			"ClientCryptoManager:pw_timeout",
			timeout,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event)
				{
					synchronized( CryptoManagerImpl.this ){

						if ( COConfigurationManager.getLongParameter( timeout_key, 0 ) == timeout ){

							COConfigurationManager.removeParameter( timeout_key );
							COConfigurationManager.removeParameter( pw_key );
						}
					}
				}
			});
	}

	@Override
	public byte[]
	getSecureID()
	{
		String key = CryptoManager.CRYPTO_CONFIG_PREFIX + "id";

		if ( secure_id == null ){

			secure_id = COConfigurationManager.getByteParameter( key, null );
		}

		if ( secure_id == null ){

			secure_id = new byte[20];

			RandomUtils.SECURE_RANDOM.nextBytes( secure_id );

			COConfigurationManager.setParameter( key, secure_id );

			COConfigurationManager.save();
		}

		return( secure_id );
	}

	private byte[]
	getOBSID()
	{
		String key = CryptoManager.CRYPTO_CONFIG_PREFIX + "obs.id";

		byte[] obs_id = COConfigurationManager.getByteParameter( key, null );

		if ( obs_id == null ){

			obs_id = new byte[20];

			RandomUtils.SECURE_RANDOM.nextBytes( obs_id );

			COConfigurationManager.setParameter( key, obs_id );

			COConfigurationManager.save();
		}

		return( obs_id );
	}

	@Override
	public byte[]
	obfuscate(
		byte[]		data )
	{
		RC4Engine	engine = new RC4Engine();

		CipherParameters	params = new KeyParameter( new SHA1Simple().calculateHash( getOBSID()));

		engine.init( true, params );

		byte[]	temp = new byte[1024];

		engine.processBytes( temp, 0, 1024, temp, 0 );

		final byte[] obs_value = new byte[ data.length ];

		engine.processBytes( data, 0, data.length, obs_value, 0 );

		return( obs_value );
	}

	@Override
	public byte[]
	deobfuscate(
		byte[]		data )
	{
		return( obfuscate( data ));
	}

	@Override
	public CryptoHandler
	getECCHandler()
	{
		return( ecc_handler );
	}
	
	@Override
	public CryptoHandler 
	getECCHandler(
		int instance)
	{
		synchronized( ecc_handlers ){
			
			CryptoHandler h = ecc_handlers.get( instance );
			
			if ( h == null ){
				
				h = new CryptoHandlerECC( this, instance );
				
				ecc_handlers.put( instance, h );
			}
			
			return( h );
		}
	}

	protected byte[]
	encryptWithPBE(
		byte[]		data,
		char[]		password )

		throws CryptoManagerException
	{
		try{
			byte[]	salt = new byte[8];

			RandomUtils.SECURE_RANDOM.nextBytes( salt );

			PBEKeySpec keySpec = new PBEKeySpec(password);

			SecretKeyFactory keyFactory = SecretKeyFactory.getInstance( PBE_ALG );

			SecretKey key = keyFactory.generateSecret(keySpec);

			PBEParameterSpec paramSpec = new PBEParameterSpec( salt, PBE_ITERATIONS );

			Cipher cipher = Cipher.getInstance( PBE_ALG );

			cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);

			byte[]	enc = cipher.doFinal( data );

			byte[]	res = new byte[salt.length + enc.length];

			System.arraycopy( salt, 0, res, 0, salt.length );

			System.arraycopy( enc, 0, res, salt.length, enc.length );

			return( res );

		}catch( Throwable e ){

			throw( new CryptoManagerException( "PBE encryption failed", e ));
		}
	}

	protected byte[]
   	decryptWithPBE(
   		byte[]		data,
   		char[]		password )

		throws CryptoManagerException
   	{
		boolean fail_is_pw_error = false;

		try{
			byte[]	salt = new byte[8];

			System.arraycopy( data, 0, salt, 0, 8 );

			PBEKeySpec keySpec = new PBEKeySpec(password);

			SecretKeyFactory keyFactory = SecretKeyFactory.getInstance( PBE_ALG );

			SecretKey key = keyFactory.generateSecret(keySpec);

			PBEParameterSpec paramSpec = new PBEParameterSpec(salt, PBE_ITERATIONS);

			Cipher cipher = Cipher.getInstance( PBE_ALG );

			cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);

			fail_is_pw_error = true;

			return( cipher.doFinal( data, 8, data.length-8 ));

		}catch( Throwable e ){

			if ( fail_is_pw_error ){

				throw( new CryptoManagerPasswordException( true, "Password incorrect", e ));

			}else{
				throw( new CryptoManagerException( "PBE decryption failed", e ));
			}
		}
   	}

	@Override
	public void
	clearPasswords()
	{
		clearPasswords( CryptoManagerPasswordHandler.HANDLER_TYPE_ALL );
	}

	@Override
	public void
	clearPasswords(
		int		password_handler_type )
	{
		session_passwords.clear();

		for (int i=0;i<CryptoManager.HANDLERS.length;i++){

			clearPassword( CryptoManager.HANDLERS[i], password_handler_type );
		}

		ecc_handler.lock();
	}

	protected void
   	clearPassword(
   		int		handler,
   		int		password_handler_type )
   	{
   		final String persist_timeout_key 	= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_timeout";
   		final String persist_pw_key 		= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_value";
		final String persist_pw_key_type	= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_type";

		int	pw_type = (int)COConfigurationManager.getLongParameter( persist_pw_key_type, CryptoManagerPasswordHandler.HANDLER_TYPE_USER );

		if ( 	password_handler_type == CryptoManagerPasswordHandler.HANDLER_TYPE_ALL ||
				password_handler_type == pw_type ){

			COConfigurationManager.removeParameter( persist_timeout_key );
			COConfigurationManager.removeParameter( persist_pw_key );
		}
   	}

	protected passwordDetails
	setPassword(
		int				handler,
		int				pw_type,
		char[]			pw_chars,
		long			timeout )

		throws CryptoManagerException
	{
		try{
			String persist_timeout_key 	= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_timeout";
			String persist_pw_key 		= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_value";
			String persist_pw_key_type	= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_type";

			byte[]	salt		= getPasswordSalt();
			byte[]	pw_bytes	= new String( pw_chars ).getBytes( "UTF8" );

			SHA1 sha1 = new SHA1();

			sha1.update( ByteBuffer.wrap( salt ));
			sha1.update( ByteBuffer.wrap( pw_bytes ));

			String	encoded_pw = ByteFormatter.encodeString( sha1.digest());

			COConfigurationManager.setParameter( persist_timeout_key, timeout );
			COConfigurationManager.setParameter( persist_pw_key_type, pw_type );
			COConfigurationManager.setParameter( persist_pw_key, encoded_pw );

			passwordDetails	result = new passwordDetails( encoded_pw.toCharArray(), pw_type );

			return( result );

		}catch( Throwable e ){

			throw( new CryptoManagerException( "setPassword failed", e ));
		}
	}

	protected passwordDetails
	getPassword(
		int				handler,
		int				action,
		String			reason,
		passwordTester	tester,
		int				pw_type )

		throws CryptoManagerException
	{
		final String persist_timeout_key 	= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_timeout";
		final String persist_pw_key 		= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_value";
		final String persist_pw_key_type	= CryptoManager.CRYPTO_CONFIG_PREFIX + "pw." + handler + ".persist_type";

		long	current_timeout = COConfigurationManager.getLongParameter( persist_timeout_key, 0 );

			// session timeout

		if ( current_timeout < 0 ){

			passwordDetails	pw = (passwordDetails)session_passwords.get( persist_pw_key );

			if ( pw != null && pw.getHandlerType() == pw_type ){

				return( pw );
			}
		}

			// absolute timeout

		if ( current_timeout > SystemTime.getCurrentTime()){

			String	current_pw = COConfigurationManager.getStringParameter( persist_pw_key, "" );

			if ( current_pw.length() > 0 ){

				int	type = (int)COConfigurationManager.getLongParameter( persist_pw_key_type, CryptoManagerPasswordHandler.HANDLER_TYPE_USER );

				if ( type == pw_type ){

					return( new passwordDetails( current_pw.toCharArray(), type ));
				}
			}
		}

		Iterator	it = password_handlers.iterator();

		while( it.hasNext()){

			int	retry_count	= 0;

			char[]	last_pw_chars = null;

			CryptoManagerPasswordHandler provider = (CryptoManagerPasswordHandler)it.next();

			if ( 	pw_type != CryptoManagerPasswordHandler.HANDLER_TYPE_UNKNOWN &&
					pw_type != provider.getHandlerType()){

				continue;
			}

			while( retry_count < 64 ){

				try{
					CryptoManagerPasswordHandler.passwordDetails details = provider.getPassword( handler, action, retry_count > 0, reason );

					if ( details == null ){

							// try next password provider

						break;
					}

					char[]	pw_chars = details.getPassword();

					if ( last_pw_chars != null && Arrays.equals( last_pw_chars, pw_chars )){

							// no point in going through verification if same as last

						retry_count++;

						continue;
					}

					last_pw_chars = pw_chars;

						// transform password so we can persist if needed

					byte[]	salt		= getPasswordSalt();
					byte[]	pw_bytes	= new String( pw_chars ).getBytes( "UTF8" );

					SHA1 sha1 = new SHA1();

					sha1.update( ByteBuffer.wrap( salt ));
					sha1.update( ByteBuffer.wrap( pw_bytes ));

					String	encoded_pw = ByteFormatter.encodeString( sha1.digest());

					if ( tester != null && !tester.testPassword( encoded_pw.toCharArray())){

							// retry

						retry_count++;

						continue;
					}

					int	persist_secs = details.getPersistForSeconds();

					long	timeout;

					if ( persist_secs == 0 ){

						timeout	= 0;

					}else if ( persist_secs == Integer.MAX_VALUE ){

						timeout = Long.MAX_VALUE;

					}else if ( persist_secs < 0 ){

							// session only

						timeout = -1;

					}else{

						timeout = SystemTime.getCurrentTime() + persist_secs * 1000L;
					}

					passwordDetails	result = new passwordDetails( encoded_pw.toCharArray(), provider.getHandlerType());

					synchronized( this ){

						COConfigurationManager.setParameter( persist_timeout_key, timeout );
						COConfigurationManager.setParameter( persist_pw_key_type, provider.getHandlerType());

						session_passwords.remove( persist_pw_key );

						COConfigurationManager.removeParameter( persist_pw_key );

						if ( timeout < 0 ){

							session_passwords.put( persist_pw_key, result );

						}else if ( timeout > 0 ){

							COConfigurationManager.setParameter( persist_pw_key, encoded_pw );

							addPasswordTimer( persist_timeout_key, persist_pw_key, timeout );
						}
					}

					provider.passwordOK( handler, details );

					return( result );

				}catch( Throwable e ){

					Debug.printStackTrace(e);

						// next provider

					break;
				}
			}
		}

		throw( new CryptoManagerPasswordException( false, "No password handlers returned a password" ));
	}

	protected byte[]
	getPasswordSalt()
	{
		return( getSecureID());
	}

	protected void
	setSecureID(
		byte[]	id )
	{
		String key = CryptoManager.CRYPTO_CONFIG_PREFIX + "id";

		COConfigurationManager.setParameter( key, id );

		COConfigurationManager.save();

		secure_id = id;
	}

	protected void
	keyChanged(
		CryptoHandler	handler )
	{
		Iterator it = keychange_listeners.iterator();

		while( it.hasNext()){

			try{
				((CryptoManagerKeyListener)it.next()).keyChanged( handler );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected void
	lockChanged(
		CryptoHandler	handler )
	{
		Iterator it = keychange_listeners.iterator();

		while( it.hasNext()){

			try{
				((CryptoManagerKeyListener)it.next()).keyLockStatusChanged( handler );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	@Override
	public void
	addPasswordHandler(
		CryptoManagerPasswordHandler		handler )
	{
		password_handlers.add( handler );
	}

	@Override
	public void
	removePasswordHandler(
		CryptoManagerPasswordHandler		handler )
	{
		password_handlers.remove( handler );
	}

	@Override
	public void
	addKeyListener(
		CryptoManagerKeyListener		listener )
	{
		keychange_listeners.add( listener );
	}

	@Override
	public void
	removeKeyListener(
		CryptoManagerKeyListener		listener )
	{
		keychange_listeners.remove( listener );
	}

	public interface
	passwordTester
	{
		public boolean
		testPassword(
			char[]		pw );
	}

	@Override
	public void
	setSRPParameters(
		byte[]		salt,
		BigInteger	verifier )
	{
		if ( salt == null ){

			COConfigurationManager.removeParameter( CryptoManager.CRYPTO_CONFIG_PREFIX + "srp.def.salt" );
			COConfigurationManager.removeParameter( CryptoManager.CRYPTO_CONFIG_PREFIX + "srp.def.verifier" );

		}else{

			COConfigurationManager.setParameter( CryptoManager.CRYPTO_CONFIG_PREFIX + "srp.def.salt", salt );
			COConfigurationManager.setParameter( CryptoManager.CRYPTO_CONFIG_PREFIX + "srp.def.verifier", verifier.toByteArray());
		}
	}

	@Override
	public SRPParameters
	getSRPParameters()
	{
		byte[]	salt 		= COConfigurationManager.getByteParameter( CryptoManager.CRYPTO_CONFIG_PREFIX + "srp.def.salt", null );
		byte[]	verifier	= COConfigurationManager.getByteParameter( CryptoManager.CRYPTO_CONFIG_PREFIX + "srp.def.verifier", null );

		if ( salt != null && verifier != null ){

			return( new SRPParametersImpl( salt, new BigInteger( verifier )));
		}

		return( null );
	}

	protected static class
	passwordDetails
	{
		private final char[]		password;
		private final int			type;

		protected
		passwordDetails(
			char[]		_password,
			int			_type )
		{
			password	= _password;
			type		= _type;
		}

		public char[]
		getPassword()
		{
			return( password );
		}

		public int
		getHandlerType()
		{
			return( type );
		}
	}

	private static class
	SRPParametersImpl
		implements SRPParameters
	{
		private final byte[]		salt;
		private final BigInteger	verifier;

		private
		SRPParametersImpl(
			byte[]		_salt,
			BigInteger	_verifier )
		{
			salt		= _salt;
			verifier	= _verifier;
		}

		@Override
		public byte[]
		getSalt()
		{
			return( salt );
		}

		@Override
		public BigInteger
		getVerifier()
		{
			return( verifier );
		}
	}

	public static void
	main(
		String[]	args )
	{
		try{

			String	stuff = "12345";

			CryptoManagerImpl man = (CryptoManagerImpl)getSingleton();

			man.addPasswordHandler(
				new CryptoManagerPasswordHandler()
				{
					@Override
					public int
					getHandlerType()
					{
						return( HANDLER_TYPE_USER );
					}

					@Override
					public passwordDetails
					getPassword(
							int 		handler_type,
							int 		action_type,
							boolean		last_pw_incorrect,
							String 		reason )
					{
						return(
								new passwordDetails()
								{
									@Override
									public char[]
									getPassword()
									{
										return( "trout".toCharArray());
									}

									@Override
									public int
									getPersistForSeconds()
									{
										return( 10 );
									}
								});
					}

					@Override
					public void
					passwordOK(
						int 				handler_type,
						passwordDetails 	details)
					{
					}
				});

			CryptoHandler	handler1 = man.getECCHandler();

			CryptoHandler	handler2 = new CryptoHandlerECC( man, 2 );


			// handler1.resetKeys( null );
			// handler2.resetKeys( null );

			byte[]	sig = handler1.sign( stuff.getBytes(), "h1: sign" );

			System.out.println( handler1.verify( handler1.getPublicKey(  "h1: Test verify" ), stuff.getBytes(), sig ));

			handler1.lock();

			byte[]	enc = handler1.encrypt( handler2.getPublicKey( "h2: getPublic" ), stuff.getBytes(), "h1: encrypt" );

			System.out.println( "pk1 = " + ByteFormatter.encodeString( handler1.getPublicKey("h1: getPublic")));
			System.out.println( "pk2 = " + ByteFormatter.encodeString( handler2.getPublicKey("h2: getPublic")));

			System.out.println( "dec: " + new String( handler2.decrypt(handler1.getPublicKey( "h1: getPublic" ), enc, "h2: decrypt" )));

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
