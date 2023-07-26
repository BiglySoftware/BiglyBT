/*
 * Created on 15 Jun 2006
 * Created by Aaron Grunthal and Paul Gardner
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

import java.nio.ByteBuffer;
import java.security.*;

import org.gudy.bouncycastle.jce.provider.JCEECDHKeyAgreement;

import com.biglybt.core.security.CryptoECCUtils;
import com.biglybt.core.security.CryptoManagerException;
import com.biglybt.core.security.CryptoSTSEngine;



/**
 * STS authentication protocol using a symmetric 4 message ECDH/ECDSA handshake
 */
final class
CryptoSTSEngineImpl
	implements CryptoSTSEngine
{
	public static final int	VERSION	= 1;

	private KeyPair 	ephemeralKeyPair;

	private final PublicKey	myPublicKey;
	private final PrivateKey	myPrivateKey;
	private PublicKey 	remotePubKey;
	private byte[] 		sharedSecret;

	private InternalDH	ecDH;

	/**
	 *
	 * @param myIdent keypair representing our current identity
	 */

	CryptoSTSEngineImpl(
		PublicKey			_myPub,
		PrivateKey			_myPriv )

		throws CryptoManagerException
	{
		myPublicKey		= _myPub;
		myPrivateKey	= _myPriv;

		ephemeralKeyPair = CryptoECCUtils.createKeys();

		try{
			ecDH = new InternalDH();

			//ecDH = KeyAgreement.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);

			ecDH.init(ephemeralKeyPair.getPrivate());

		}catch (Exception e){

			throw new CryptoManagerException("Couldn't initialize crypto handshake", e);
		}
	}

	@Override
	public void
	getKeys(
		ByteBuffer		message )

		throws CryptoManagerException
	{
		getMessage( message, true );
	}

	@Override
	public void
	putKeys(
		ByteBuffer		message )

		throws CryptoManagerException
	{
		putMessage( message, true );
	}

	@Override
	public void
	getAuth(
		ByteBuffer		message )

		throws CryptoManagerException
	{
		getMessage( message, false );
	}

	@Override
	public void
	putAuth(
		ByteBuffer		message )

		throws CryptoManagerException
	{
		putMessage( message, false );
	}

	public void
	putMessage(
		ByteBuffer		message,
		boolean			keys )

		throws CryptoManagerException
	{
		// System.out.println( "put( " + keys + ") " + this );

		try{
			int	version = getInt( message, 255 );

			if ( version != VERSION ){

				throw( new CryptoManagerException( "invalid version (" + version + ")" ));
			}

			if ( keys ){

				if ( sharedSecret != null ){

					throw( new CryptoManagerException( "phase error: keys already received" ));
				}

				final byte[] rawRemoteOtherPubkey = getBytes( message, 65535 );

				final byte[] rawRemoteEphemeralPubkey = getBytes( message, 65535 );

				final byte[] remoteSig = getBytes( message, 65535 );

				final byte[] pad = getBytes( message, 65535 );

				remotePubKey = CryptoECCUtils.rawdataToPubkey(rawRemoteOtherPubkey);

				Signature check = CryptoECCUtils.getSignature(remotePubKey);

				check.update(rawRemoteOtherPubkey);

				check.update(rawRemoteEphemeralPubkey);

				if ( check.verify(remoteSig)){

					ecDH.doPhase(CryptoECCUtils.rawdataToPubkey(rawRemoteEphemeralPubkey), true);

					sharedSecret = ecDH.generateSecret();

				}else{

					throw( new CryptoManagerException( "Signature check failed" ));
				}

			}else{

				if ( sharedSecret == null ){

					throw( new CryptoManagerException( "phase error: keys not received" ));
				}

				final byte[] IV = getBytes( message, 65535 );

				final byte[] remoteSig = getBytes( message, 65535);

				Signature check = CryptoECCUtils.getSignature( remotePubKey );

				check.update(IV);

				check.update(sharedSecret);

				if ( !check.verify(remoteSig)){

					throw( new CryptoManagerException( "Signature check failed" ));
				}
			}
		}catch( CryptoManagerException	e ){

			throw( e );

		}catch( Throwable e ){

			throw( new CryptoManagerException( "Failed to generate message" ));
		}
	}

	public void
	getMessage(
		ByteBuffer	buffer,
		boolean		keys )

		throws CryptoManagerException
	{
		// System.out.println( "get( " + keys + ") " + this );

		try{
			putInt( buffer, VERSION, 255 );

			SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

			Signature sig = CryptoECCUtils.getSignature(myPrivateKey);

			if ( keys ){

				final byte[] rawMyPubkey = CryptoECCUtils.keyToRawdata(myPublicKey);

				final byte[] rawEphemeralPubkey = CryptoECCUtils.keyToRawdata(ephemeralKeyPair.getPublic());

				sig.update(rawMyPubkey);

				sig.update(rawEphemeralPubkey);

				final byte[] rawSign = sig.sign();

				final byte[] pad = new byte[random.nextInt(32)];

				random.nextBytes(pad);

				putBytes( buffer, rawMyPubkey, 65535 );

				putBytes( buffer, rawEphemeralPubkey, 65535 );

				putBytes( buffer, rawSign, 65535 );

				putBytes( buffer, pad, 65535 );

			}else{

				if ( sharedSecret == null ){

					throw( new CryptoManagerException( "phase error: keys not received" ));
				}

				final byte[] IV = new byte[20 + random.nextInt(32)];

				random.nextBytes(IV);

				sig.update(IV);

				sig.update(sharedSecret);

				final byte[] rawSig = sig.sign();

				putBytes( buffer, IV, 65535 );

				putBytes( buffer, rawSig, 65535 );
			}
		}catch( CryptoManagerException	e ){

			throw( e );

		}catch( Throwable e ){

			throw( new CryptoManagerException( "Failed to generate message" ));
		}
	}

	@Override
	public byte[]
	getSharedSecret()

		throws CryptoManagerException
	{
		if ( sharedSecret == null ){

			throw( new CryptoManagerException( "secret not yet available" ));
		}

		return sharedSecret;
	}

	@Override
	public byte[]
	getRemotePublicKey()

		throws CryptoManagerException
	{
		if ( remotePubKey == null ){

			throw( new CryptoManagerException( "key not yet available" ));
		}

		return( CryptoECCUtils.keyToRawdata( remotePubKey ));
	}

	protected int
	getInt(
		ByteBuffer	buffer,
		int			max_size )

		throws CryptoManagerException
	{
		try{
			if ( max_size < 256 ){

				return( buffer.get() & 0xff);

			}else if ( max_size < 65536 ){

				return( buffer.getShort() & 0xffff);

			}else{

				return( buffer.getInt());
			}
		}catch( Throwable e ){

			throw( new CryptoManagerException( "Failed to get int", e ));
		}
	}

	protected byte[]
	getBytes(
		ByteBuffer	buffer,
		int			max_size )

		throws CryptoManagerException
	{
		int	len = getInt( buffer, max_size );

		if ( len > max_size ){

			throw( new CryptoManagerException( "Invalid length" ));
		}

		try{
			byte[]	res = new byte[len];

			buffer.get( res );

			return( res );

		}catch( Throwable e ){

			throw( new CryptoManagerException( "Failed to get byte[]", e ));
		}
	}

	protected void
	putInt(
		ByteBuffer	buffer,
		int			value,
		int			max_size )

		throws CryptoManagerException
	{
		try{
			if ( max_size < 256 ){

				buffer.put((byte)value);

			}else if ( max_size < 65536 ){

				buffer.putShort((short)value );

			}else{

				buffer.putInt( value );
			}
		}catch( Throwable e ){

			throw( new CryptoManagerException( "Failed to put int", e ));
		}
	}

	protected void
	putBytes(
		ByteBuffer	buffer,
		byte[]		value,
		int			max_size )

		throws CryptoManagerException
	{
		putInt( buffer, value.length, max_size );

		try{
			buffer.put( value );

		}catch( Throwable e ){

			throw( new CryptoManagerException( "Failed to put byte[]", e ));
		}
	}

	static class
	InternalDH
		extends JCEECDHKeyAgreement.DH
	{
			// we use this class to obtain compatibility with BC

		@Override
		public void
		init(
			Key		key )

			throws InvalidKeyException, InvalidAlgorithmParameterException
		{
			engineInit( key, null );
		}

		@Override
		public Key
		doPhase(
			Key		key,
			boolean	lastPhase )

			throws InvalidKeyException, IllegalStateException
		{
			return( engineDoPhase( key, lastPhase ));
		}

		@Override
		public byte[]
		generateSecret()

			throws IllegalStateException
		{
			return( engineGenerateSecret());
		}
	}
}