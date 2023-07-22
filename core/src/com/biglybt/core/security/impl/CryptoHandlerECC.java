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

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import org.gudy.bouncycastle.jce.provider.JCEIESCipher;
import org.gudy.bouncycastle.jce.spec.IEKeySpec;
import org.gudy.bouncycastle.jce.spec.IESParameterSpec;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.security.*;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SystemTime;

public class
CryptoHandlerECC
	implements CryptoHandler
{
	private static final String	DEFAULT_PASSWORD	= "";
	private static final Long	DEFAULT_TIMEOUT		= Long.MAX_VALUE;


	private static final int	TIMEOUT_DEFAULT_SECS		= 60*60;


	final CryptoManagerImpl		manager;
	final int					instance_id;
	
	private String				CONFIG_PREFIX = CryptoManager.CRYPTO_CONFIG_PREFIX + "ecc.";

	private PrivateKey			use_method_private_key;
	private PublicKey			use_method_public_key;

	private long	last_unlock_time;

	protected
	CryptoHandlerECC(
		CryptoManagerImpl		_manager,
		int						_instance_id )
	{
		manager		= _manager;
		instance_id	= _instance_id;
		
		CONFIG_PREFIX += _instance_id + ".";

			// migration away from system managed keys

		if ( getDefaultPasswordHandlerType() != CryptoManagerPasswordHandler.HANDLER_TYPE_USER ){

			COConfigurationManager.setParameter( CONFIG_PREFIX + "default_pwtype", CryptoManagerPasswordHandler.HANDLER_TYPE_USER );
		}

		if ( 	getCurrentPasswordType() == CryptoManagerPasswordHandler.HANDLER_TYPE_SYSTEM ||
				COConfigurationManager.getByteParameter( CONFIG_PREFIX + "publickey", null ) == null ){

			try{
				createAndStoreKeys(
					manager.setPassword(
						CryptoManager.HANDLER_ECC,
						CryptoManagerPasswordHandler.HANDLER_TYPE_USER,
						DEFAULT_PASSWORD.toCharArray(),
						DEFAULT_TIMEOUT ));

				Debug.outNoStack( "Successfully migrated key management" );

			}catch( Throwable e ){

				Debug.out( "Failed to migrate key management", e );
			}
		}
	}

	@Override
	public int
	getType()
	{
		return( CryptoManager.HANDLER_ECC );
	}

	@Override
	public int
	getInstance()
	{
		return( instance_id );
	}
	
	@Override
	public void
	unlock()

		throws CryptoManagerException
	{
		getMyPrivateKey( "unlock" );
	}

	@Override
	public synchronized boolean
	isUnlocked()
	{
		return( use_method_private_key != null );
	}

	@Override
	public void
	lock()
	{
		boolean	changed = false;

		synchronized( this ){

			changed = use_method_private_key != null;

			use_method_private_key	= null;
		}

		if ( changed ){

			manager.lockChanged( this );
		}
	}

	@Override
	public int
	getUnlockTimeoutSeconds()
	{
		return( COConfigurationManager.getIntParameter( CONFIG_PREFIX + "timeout", TIMEOUT_DEFAULT_SECS ));
	}

	@Override
	public void
	setUnlockTimeoutSeconds(
		int		secs )
	{
		COConfigurationManager.setParameter( CONFIG_PREFIX + "timeout", secs );
	}

	@Override
	public byte[]
	sign(
		byte[]		data,
		String		reason )

		throws CryptoManagerException
	{
		PrivateKey	priv = getMyPrivateKey( reason );

		Signature sig = CryptoECCUtils.getSignature( priv );

		try{
			sig.update( data );

			return( sig.sign());

		}catch( Throwable e ){

			throw( new CryptoManagerException( "Signature failed", e ));
		}
	}

	@Override
	public boolean
	verify(
		byte[]		public_key,
		byte[]		data,
		byte[]		signature )

		throws CryptoManagerException
	{
		PublicKey	pub = CryptoECCUtils.rawdataToPubkey( public_key );

		Signature sig = CryptoECCUtils.getSignature( pub );

		try{
			sig.update( data );

			return( sig.verify( signature ));

		}catch( Throwable e ){

			throw( new CryptoManagerException( "Signature failed", e ));
		}
	}

	@Override
	public byte[]
	encrypt(
		byte[]		other_public_key,
		byte[]		data,
		String		reason )

		throws CryptoManagerException
	{
		try{
			IEKeySpec   key_spec = new IEKeySpec( getMyPrivateKey( reason ), CryptoECCUtils.rawdataToPubkey( other_public_key ));

			byte[]	d = new byte[16];
			byte[]	e = new byte[16];

			RandomUtils.nextSecureBytes( d );
			RandomUtils.nextSecureBytes( e );

			IESParameterSpec param = new IESParameterSpec( d, e, 128);

			InternalECIES	cipher = new InternalECIES();

			cipher.internalEngineInit( Cipher.ENCRYPT_MODE, key_spec, param, null );

			byte[]	encrypted = cipher.internalEngineDoFinal(data, 0, data.length );

			byte[] result = new byte[32+encrypted.length];

			System.arraycopy( d, 0, result, 0, 16 );
			System.arraycopy( e, 0, result, 16, 16 );
			System.arraycopy( encrypted, 0, result, 32, encrypted.length );

			return( result );

		}catch( CryptoManagerException e ){

			throw( e );

		}catch( Throwable e){

			throw( new CryptoManagerException( "Encrypt failed", e ));
		}
	}

	@Override
	public byte[]
	decrypt(
		byte[]		other_public_key,
		byte[]		data,
		String		reason )

		throws CryptoManagerException
	{
		try{
			IEKeySpec   key_spec = new IEKeySpec( getMyPrivateKey(  reason ), CryptoECCUtils.rawdataToPubkey( other_public_key ));

			byte[]	d = new byte[16];
			byte[]	e = new byte[16];

			System.arraycopy( data, 0, d, 0, 16 );
			System.arraycopy( data, 16, e, 0, 16 );

			IESParameterSpec param = new IESParameterSpec( d, e, 128);

			InternalECIES	cipher = new InternalECIES();

			cipher.internalEngineInit( Cipher.DECRYPT_MODE, key_spec, param, null );

			return( cipher.internalEngineDoFinal( data, 32, data.length - 32 ));

		}catch( CryptoManagerException e ){

			throw( e );

		}catch( Throwable e){

			throw( new CryptoManagerException( "Decrypt failed", e ));
		}
	}

	@Override
	public CryptoSTSEngine
	getSTSEngine(
		String		reason )

		throws CryptoManagerException
	{
		return( new CryptoSTSEngineImpl( getMyPublicKey(  reason, true ), getMyPrivateKey( reason )));
	}

	@Override
	public CryptoSTSEngine
	getSTSEngine(
		PublicKey		public_key,
		PrivateKey		private_key )

		throws CryptoManagerException
	{
		return( new CryptoSTSEngineImpl( public_key, private_key ));
	}

	@Override
	public byte[]
	peekPublicKey()
	{
		try{

			return( CryptoECCUtils.keyToRawdata( getMyPublicKey( "peek", false )));

		}catch( Throwable e ){

			return( null );
		}
	}

	@Override
	public byte[]
	getPublicKey(
		String		reason )

		throws CryptoManagerException
	{
		return( CryptoECCUtils.keyToRawdata( getMyPublicKey( reason, true )));
	}

	@Override
	public byte[]
	getEncryptedPrivateKey(
		String		reason )

		throws CryptoManagerException
	{
		getMyPrivateKey( reason );

		byte[]	pk = COConfigurationManager.getByteParameter( CONFIG_PREFIX + "privatekey", null );

		if ( pk == null ){

			throw( new CryptoManagerException( "Private key unavailable" ));
		}

		int	pw_type = getCurrentPasswordType();

		byte[] res = new byte[pk.length+1];

		res[0] = (byte)pw_type;

		System.arraycopy( pk, 0, res, 1, pk.length );

		return( res );
	}

	@Override
	public void
	recoverKeys(
		byte[]		public_key,
		byte[]		encrypted_private_key_and_type )

		throws CryptoManagerException
	{
		boolean	lock_changed = false;

		synchronized( this ){

			lock_changed = use_method_private_key != null;

			use_method_private_key	= null;
			use_method_public_key	= null;

			manager.clearPassword( CryptoManager.HANDLER_ECC, CryptoManagerPasswordHandler.HANDLER_TYPE_ALL );

			COConfigurationManager.setParameter( CONFIG_PREFIX + "publickey", public_key );

			int	type = (int)encrypted_private_key_and_type[0]&0xff;

			COConfigurationManager.setParameter( CONFIG_PREFIX + "pwtype", type );

			byte[] encrypted_private_key = new byte[encrypted_private_key_and_type.length-1];

			System.arraycopy( encrypted_private_key_and_type, 1, encrypted_private_key, 0, encrypted_private_key.length );

			COConfigurationManager.setParameter( CONFIG_PREFIX + "privatekey", encrypted_private_key );

			COConfigurationManager.save();
		}

		manager.keyChanged( this );

		if ( lock_changed ){

			manager.lockChanged( this );
		}
	}

	@Override
	public void
	resetKeys(
		String		reason )

		throws CryptoManagerException
	{
		boolean	lock_changed = false;

		synchronized( this ){

			lock_changed = use_method_private_key != null;

			use_method_private_key	= null;
			use_method_public_key	= null;

			manager.clearPassword( CryptoManager.HANDLER_ECC, CryptoManagerPasswordHandler.HANDLER_TYPE_ALL );

			COConfigurationManager.removeParameter( CONFIG_PREFIX + "publickey" );

			COConfigurationManager.removeParameter( CONFIG_PREFIX + "privatekey" );

			COConfigurationManager.save();
		}

		if ( lock_changed ){

			manager.lockChanged( this );
		}

		try{

			createAndStoreKeys( "resetting keys" );

		}catch( CryptoManagerException e ){

			manager.keyChanged( this );

			throw( e );
		}
	}

	protected PrivateKey
	getMyPrivateKey(
		String		reason )

		throws CryptoManagerException
	{
		boolean	lock_change = false;

		try{
			synchronized( this ){

				if ( use_method_private_key != null ){

					int	timeout_secs = getUnlockTimeoutSeconds();

					if ( timeout_secs > 0 ){

						if ( SystemTime.getCurrentTime() - last_unlock_time >= timeout_secs * 1000 ){

							lock_change = true;

							use_method_private_key = null;
						}
					}
				}

				if ( use_method_private_key != null ){

					return( use_method_private_key );
				}
			}

			final byte[]	encoded = COConfigurationManager.getByteParameter( CONFIG_PREFIX + "privatekey", null );

			if ( encoded == null ){

				return((PrivateKey)createAndStoreKeys( reason )[1]);

			}else{

				CryptoManagerImpl.passwordDetails password_details =
					manager.getPassword(
							CryptoManager.HANDLER_ECC,
							CryptoManagerPasswordHandler.ACTION_DECRYPT,
							reason,
							new CryptoManagerImpl.passwordTester()
							{
								@Override
								public boolean
								testPassword(
									char[] password )
								{
									try{
										manager.decryptWithPBE( encoded, password );

										return( true );

									}catch( Throwable e ){

										return( false );
									}
								}
							},
							getCurrentPasswordType());

				synchronized( this ){

					boolean		ok = false;

					try{
						use_method_private_key = CryptoECCUtils.rawdataToPrivkey( manager.decryptWithPBE( encoded, password_details.getPassword()));

						lock_change = true;

						last_unlock_time = SystemTime.getCurrentTime();

						if ( !checkKeysOK( reason )){

							throw( new CryptoManagerPasswordException( true, "Password incorrect" ));
						}

						ok = true;

					}catch( CryptoManagerException e ){

						throw( e );

					}catch( Throwable e ){

						throw( new CryptoManagerException( "Password incorrect", e ));

					}finally{

						if ( !ok ){

							manager.clearPassword( CryptoManager.HANDLER_ECC, CryptoManagerPasswordHandler.HANDLER_TYPE_ALL );

							lock_change = true;

							use_method_private_key	= null;
						}
					}

					if ( use_method_private_key == null ){

						throw( new CryptoManagerException( "Failed to get private key" ));
					}

					return( use_method_private_key );
				}
			}
		}finally{

			if ( lock_change ){

				manager.lockChanged( this );
			}
		}
	}

	protected boolean
	checkKeysOK(
		String	reason )

		throws CryptoManagerException
	{
		byte[]	test_data = "test".getBytes();

		return( verify( CryptoECCUtils.keyToRawdata( getMyPublicKey( reason, true )), test_data,  sign( test_data, reason )));
	}

	protected PublicKey
	getMyPublicKey(
		String		reason,
		boolean		create_if_needed )

		throws CryptoManagerException
	{
		boolean	create_new = false;

		synchronized( this ){

			if ( use_method_public_key == null ){

				byte[]	key_bytes = COConfigurationManager.getByteParameter( CONFIG_PREFIX + "publickey", null );

				if ( key_bytes == null ){

					if ( create_if_needed ){

						create_new = true;

					}else{

						return( null );
					}
				}else{

					use_method_public_key = CryptoECCUtils.rawdataToPubkey( key_bytes );
				}
			}

			if ( !create_new ){

				if ( use_method_public_key == null ){

					throw( new CryptoManagerException( "Failed to get public key" ));
				}

				return( use_method_public_key );
			}
		}

		return((PublicKey)createAndStoreKeys( reason )[0] );
	}

	@Override
	public int
	getDefaultPasswordHandlerType()
	{
		return( COConfigurationManager.getIntParameter( CONFIG_PREFIX + "default_pwtype", CryptoManagerPasswordHandler.HANDLER_TYPE_USER ));
	}

	@Override
	public void
	setDefaultPasswordHandlerType(
		int		new_type )

		throws CryptoManagerException
	{
		String reason = "Changing password handler";

		boolean	have_existing_keys = COConfigurationManager.getByteParameter( CONFIG_PREFIX + "privatekey", null ) != null;

			// ensure we unlock the private key so we can then re-persist it with new password

		if ( have_existing_keys ){

			if ( new_type == getCurrentPasswordType()){

				return;
			}

			getMyPrivateKey( reason );

			CryptoManagerImpl.passwordDetails password_details =
				manager.getPassword(
								CryptoManager.HANDLER_ECC,
								CryptoManagerPasswordHandler.ACTION_ENCRYPT,
								reason,
								null,
								new_type );


			synchronized( this ){

				if ( use_method_private_key == null ){

					throw( new CryptoManagerException( "Private key not available" ));
				}

				byte[]	priv_raw = CryptoECCUtils.keyToRawdata( use_method_private_key );

				byte[]	priv_enc = manager.encryptWithPBE( priv_raw, password_details.getPassword());

				COConfigurationManager.setParameter( CONFIG_PREFIX + "privatekey", priv_enc );

				COConfigurationManager.setParameter( CONFIG_PREFIX + "pwtype", password_details.getHandlerType());

				COConfigurationManager.setParameter( CONFIG_PREFIX + "default_pwtype", password_details.getHandlerType());

				COConfigurationManager.save();
			}
		}else{

				// not much to do as keys not yet created

			synchronized( this ){

				if ( COConfigurationManager.getByteParameter( CONFIG_PREFIX + "privatekey", null ) == null ){

					COConfigurationManager.setParameter( CONFIG_PREFIX + "default_pwtype", new_type );

					COConfigurationManager.save();
				}
			}
		}
	}

	protected Key[]
  	createAndStoreKeys(
  		String		reason )

  		throws CryptoManagerException
  	{
		CryptoManagerImpl.passwordDetails password_details =
				manager.getPassword(
  							CryptoManager.HANDLER_ECC,
  							CryptoManagerPasswordHandler.ACTION_ENCRYPT,
  							reason,
  							null,
  							getDefaultPasswordHandlerType());

		return( createAndStoreKeys( password_details ));
  	}

	protected Key[]
	createAndStoreKeys(
		CryptoManagerImpl.passwordDetails	password_details )

		throws CryptoManagerException
	{
		try{
			synchronized( this ){

				if ( use_method_public_key == null || use_method_private_key == null ){

					KeyPair	keys = CryptoECCUtils.createKeys();

					use_method_public_key	= keys.getPublic();

					use_method_private_key	= keys.getPrivate();

					last_unlock_time = SystemTime.getCurrentTime();

					COConfigurationManager.setParameter( CONFIG_PREFIX + "publickey", CryptoECCUtils.keyToRawdata( use_method_public_key ));

					byte[]	priv_raw = CryptoECCUtils.keyToRawdata( use_method_private_key );

					byte[]	priv_enc = manager.encryptWithPBE( priv_raw, password_details.getPassword());

					COConfigurationManager.setParameter( CONFIG_PREFIX + "privatekey", priv_enc );

					COConfigurationManager.setParameter( CONFIG_PREFIX + "pwtype", password_details.getHandlerType());

					COConfigurationManager.save();
				}

				return( new Key[]{ use_method_public_key, use_method_private_key });
			}
		}finally{

			manager.keyChanged( this );

			manager.lockChanged( this );
		}
	}

	@Override
	public boolean
	verifyPublicKey(
		byte[]	encoded )
	{
		try{
			CryptoECCUtils.rawdataToPubkey( encoded );

				// we can't actually verify the key size as although it should be 192 bits
				// it can be less due to leading bits being 0

			return( true );

		}catch( Throwable e ){

			return( false );
		}
	}

	@Override
	public String
	exportKeys()

		throws CryptoManagerException
	{
		return( "id:      " + Base32.encode(manager.getSecureID()) + "\r\n" +
				"public:  " + Base32.encode(getPublicKey( "Key export" )) + "\r\n" +
				"private: " + Base32.encode(getEncryptedPrivateKey( "Key export" )));
	}

	@Override
	public boolean
	importKeys(
		String	str )

		throws CryptoManagerException
	{
		String	reason = "Key import";

		byte[]	existing_id 			= manager.getSecureID();
		byte[]	existing_public_key		= peekPublicKey();
		byte[]	existing_private_key	= existing_public_key==null?null:getEncryptedPrivateKey( reason );

		byte[]		recovered_id 			= null;
		byte[]		recovered_public_key 	= null;
		byte[]		recovered_private_key 	= null;

		String[]	bits = str.split( "\n" );

		for (int i=0;i<bits.length;i++){

			String	bit = bits[i].trim();

			if ( bit.length() == 0 ){

				continue;
			}

			String[] x = bit.split(":");

			if ( x.length != 2 ){

				continue;
			}

			String	lhs = x[0].trim();
			String	rhs = x[1].trim();

			byte[]	rhs_val = Base32.decode( rhs );

			if ( lhs.equals( "id" )){

				recovered_id = rhs_val;

			}else if ( lhs.equals( "public" )){

				recovered_public_key = rhs_val;

			}else if ( lhs.equals( "private" )){

				recovered_private_key = rhs_val;
			}
		}

		if ( recovered_id == null || recovered_public_key == null || recovered_private_key == null ){

			throw( new CryptoManagerException( "Invalid input file" ));
		}

		boolean	ok = false;

		boolean	result = false;

		try{

			result = !Arrays.equals( existing_id, recovered_id );

			if ( result ){

				manager.setSecureID( recovered_id );
			}

			recoverKeys( recovered_public_key, recovered_private_key );

			if ( !checkKeysOK( reason )){

				throw( new CryptoManagerException( "Invalid key pair" ));
			}

			ok = true;

		}finally{

			if ( !ok ){

				result = false;

				manager.setSecureID( existing_id );

				if ( existing_public_key != null ){

					recoverKeys( existing_public_key, existing_private_key );
				}
			}
		}

		return( result );
	}

	protected int
	getCurrentPasswordType()
	{
		return((int)COConfigurationManager.getIntParameter( CONFIG_PREFIX + "pwtype", CryptoManagerPasswordHandler.HANDLER_TYPE_USER ));
	}

	static class InternalECIES
		extends JCEIESCipher.ECIES
	{
			// we use this class to obtain compatibility with BC

		public void
		internalEngineInit(
			int                     opmode,
			Key                     key,
			AlgorithmParameterSpec  params,
			SecureRandom            random )

			throws InvalidKeyException, InvalidAlgorithmParameterException
		{
			engineInit(opmode, key, params, random);
		}

		protected byte[]
		internalEngineDoFinal(
			byte[]  input,
			int     inputOffset,
			int     inputLen )

			throws IllegalBlockSizeException, BadPaddingException
		{
			return engineDoFinal(input, inputOffset, inputLen);
		}
	}
}
