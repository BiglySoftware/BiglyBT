/*
 * Created on 17-Jan-2006
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

package com.biglybt.core.networkmanager.impl;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;

public class
ProtocolDecoderPHE
	extends ProtocolDecoder
{
	private static final LogIDs LOGID = LogIDs.NWMAN;

	private static final byte		CRYPTO_PLAIN	= 0x01;
	private static final byte		CRYPTO_RC4		= 0x02;
	private static final byte		CRYPTO_XOR		= 0x04;
	private static final byte		CRYPTO_AES		= 0x08;


	//private static final String 	DH_P = "92d862b3a95bff4e6cbdce3a266ff4b46e6e1ecad76c0a877d92a3dae4999e6414efde56fc14d1cca6d5408a8ef9ea248389168876b6e8f4503845dfe373549f";
	//private static final String 	DH_G = "4383b53ee650fd73e41e8c9e8527997ab8cb41e1cbd73ac7685493e1e5d091e3e3789dea03ab9d5b2c368faa617bb30e427cbaeb23c268edb38eb8c747756080";
	// private static final String 	DH_P = "f3f90c790c63b119f9c1be43fdb12dc6ed6f26325999c01ba6ed373e75d6b2dee8d1c0475652a987c8df57b23d395bdb142be316d780b9361f85629535030873";

	private static final String 	DH_P = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563";
	private static final String 	DH_G = "02";
	private static final int		DH_L = 160;

	private static final int		DH_SIZE_BYTES	= DH_P.length()/2;

	public static final int MIN_INCOMING_INITIAL_PACKET_SIZE	= DH_SIZE_BYTES;


	private static final BigInteger	DH_P_BI = new BigInteger( DH_P, 16 );
	private static final BigInteger	DH_G_BI = new BigInteger( DH_G, 16 );

	private static KeyPairGenerator		dh_key_generator;
	//private static long					last_dh_incoming_key_generate;

	private static final int			BLOOM_RECREATE				= 30*1000;
	private static final int			BLOOM_INCREASE				= 1000;
	private static BloomFilter			generate_bloom				= BloomFilterFactory.createAddRemove4Bit(BLOOM_INCREASE);
	private static long					generate_bloom_create_time	= SystemTime.getCurrentTime();

	private static boolean	crypto_setup_done;
	private static boolean	crypto_ok;
	//private static boolean	aes_ok;

	/*
	private static final String		AES_STREAM_ALG				= "AES";
	private static final String		AES_STREAM_CIPHER			= "AES/CFB8/NoPadding";
	private static final int		AES_STREAM_KEY_SIZE			= 128;
	private static final int		AES_STREAM_KEY_SIZE_BYTES	= AES_STREAM_KEY_SIZE/8;
   	*/

	//private static final byte[]		AES_STREAM_IV				=
    //	{ 	(byte)0x15, (byte)0xE0, (byte)0x6B, (byte)0x7E, (byte)0x98, (byte)0x59, (byte)0xE4, (byte)0xA7,
    //		(byte)0x34, (byte)0x66, (byte)0xAD, (byte)0x48, (byte)0x35, (byte)0xE2, (byte)0xD0, (byte)0x24 };


	private static final String		RC4_STREAM_ALG				= "RC4";
	private static final String		RC4_STREAM_CIPHER			= "RC4";
	private static final int		RC4_STREAM_KEY_SIZE			= 128;
	private static final int		RC4_STREAM_KEY_SIZE_BYTES	= RC4_STREAM_KEY_SIZE/8;


    private static final int		PADDING_MAX	= 512;

    private static final int		PADDING_MAX_NORMAL	= PADDING_MAX;
    private static final int		PADDING_MAX_LIMITED	= 128;

    public static int
    getMaxIncomingInitialPacketSize(
    	boolean	min_overheads )
    {
    	return( MIN_INCOMING_INITIAL_PACKET_SIZE + (min_overheads?PADDING_MAX_LIMITED:PADDING_MAX_NORMAL)/2 );
    }

    private static final Random	random = RandomUtils.SECURE_RANDOM;

	private static final Map<HashWrapper,Object[]>	global_shared_secrets	= new LightHashMap<>();

	private static final int MAX_OLD_SECRETS	= 1024;
	
	private static final Map<HashWrapper,Object[]> old_global_shared_secrets = new LinkedHashMap<HashWrapper,Object[]>(
			MAX_OLD_SECRETS, 0.75f, true){
		@Override
		protected boolean removeEldestEntry(Map.Entry<HashWrapper,Object[]> eldest){
			return size() > MAX_OLD_SECRETS;
		}
	};
	
	private static boolean
	cryptoSetup()
	{
		synchronized( global_shared_secrets ){

			if ( crypto_setup_done ){

				return( crypto_ok );
			}

			crypto_setup_done = true;

			try{
				DHParameterSpec dh_param_spec = new DHParameterSpec( DH_P_BI, DH_G_BI, DH_L );

				dh_key_generator = KeyPairGenerator.getInstance("DH");

				dh_key_generator.initialize(dh_param_spec);

				dh_key_generator.generateKeyPair();

			    byte[]	rc4_test_secret = new byte[RC4_STREAM_KEY_SIZE_BYTES];

			    SecretKeySpec	rc4_test_secret_key_spec = new SecretKeySpec(rc4_test_secret, 0, RC4_STREAM_KEY_SIZE_BYTES, RC4_STREAM_ALG );

			    TransportCipher rc4_cipher = new TransportCipher( RC4_STREAM_CIPHER, Cipher.ENCRYPT_MODE, rc4_test_secret_key_spec );

			    rc4_cipher = new TransportCipher( RC4_STREAM_CIPHER, Cipher.DECRYPT_MODE, rc4_test_secret_key_spec );

			    /*
				try{
					byte[]	aes_test_secret = new byte[AES_STREAM_KEY_SIZE_BYTES];

					SecretKeySpec	aes_test_secret_key_spec = new SecretKeySpec(aes_test_secret, 0, AES_STREAM_KEY_SIZE_BYTES, AES_STREAM_ALG );

					AlgorithmParameterSpec	spec = 	new IvParameterSpec( aes_test_secret );

			        TCPTransportCipher aes_cipher = new TCPTransportCipher( AES_STREAM_CIPHER, Cipher.ENCRYPT_MODE, aes_test_secret_key_spec, spec );

			        aes_cipher = new TCPTransportCipher( AES_STREAM_CIPHER, Cipher.DECRYPT_MODE, aes_test_secret_key_spec, spec );

			        aes_ok	= true;

				}catch( Throwable e ){

					Logger.log(	new LogEvent(LOGID, "AES Unavailable", e ));
				}
		        */

		        crypto_ok	= true;

		     	if (Logger.isEnabled()){

	        		Logger.log(	new LogEvent(LOGID, "PHE crypto initialised" ));
		     	}
			}catch( NoClassDefFoundError e ){

					// running without PHE classes, not such a severe error

				Logger.log(	new LogEvent(LOGID, "PHE crypto disabled as classes unavailable" ));

				crypto_ok	= false;

			}catch( Throwable e ){

	        	Logger.log(	new LogEvent(LOGID, "PHE crypto initialisation failed", e ));

				crypto_ok	= false;
			}

			return( crypto_ok );
		}
	}

	public static boolean
	isCryptoOK()
	{
		return( cryptoSetup());
	}

	public static void
	addSecretsSupport(
		String			name,
		byte[][]		secrets )
	{
		for (int i=0;i<secrets.length;i++){

			SHA1Hasher hasher = new SHA1Hasher();

	   		hasher.update( REQ2_IV );
	   		hasher.update( secrets[i] );

	   		byte[]	encoded = hasher.getDigest();

			synchronized( global_shared_secrets ){

				global_shared_secrets.put( new HashWrapper( encoded ), new Object[]{ secrets[i], name } );
			}
		}
	}

	public static void
	removeSecretsSupport(
		byte[][]		secrets )
	{
		for (int i=0;i<secrets.length;i++){

			SHA1Hasher hasher = new SHA1Hasher();

	   		hasher.update( REQ2_IV );
	   		hasher.update( secrets[i] );

	   		byte[]	encoded = hasher.getDigest();

	   		HashWrapper hw = new HashWrapper( encoded );
	   		
			synchronized( global_shared_secrets ){

				Object[] entry = global_shared_secrets.remove( hw );
				
				if ( entry != null ){
					
					old_global_shared_secrets.put( hw, entry );
				}
			}
		}
	}

	// private static final byte SUPPORTED_PROTOCOLS = (byte)((aes_ok?CRYPTO_AES:0) | CRYPTO_RC4 | CRYPTO_XOR | CRYPTO_PLAIN );
	private static final byte SUPPORTED_PROTOCOLS = (byte)(CRYPTO_RC4 | CRYPTO_PLAIN );


	static byte 	MIN_CRYPTO;

	static{
	    COConfigurationManager.addAndFireParameterListeners(
	    		new String[]{ "network.transport.encrypted.min_level" },
	    		new ParameterListener()
	    		{
	    			 @Override
				     public void
	    			 parameterChanged(
	    				String ignore )
	    			 {
	    				 if ( NetworkManager.REQUIRE_CRYPTO_HANDSHAKE && !isCryptoOK() ){
	    					 Logger.log( new LogAlert(true,LogAlert.AT_ERROR,"Connection encryption unavailable, please update your Java version" ));
	    				 }

	    				 String	min	= COConfigurationManager.getStringParameter( "network.transport.encrypted.min_level");

	    				 if ( min.equals( "XOR" )){

	    					 MIN_CRYPTO	= CRYPTO_XOR | CRYPTO_RC4 | CRYPTO_AES;

	    				 }else if ( min.equals( "RC4" )){

	    					 MIN_CRYPTO	= CRYPTO_RC4 | CRYPTO_AES;

	    				 }else if ( min.equals( "AES" )){

	    					 MIN_CRYPTO	= CRYPTO_AES;

	    				 }else{

	    					 MIN_CRYPTO	= CRYPTO_PLAIN | CRYPTO_XOR | CRYPTO_RC4 | CRYPTO_AES;
	    				 }

	    				 MIN_CRYPTO = (byte)(MIN_CRYPTO & SUPPORTED_PROTOCOLS);
	    			 }
	    		});
	}


	private static final int		PS_OUTBOUND_1	= 0;
	private static final int		PS_OUTBOUND_2	= 1;
	private static final int		PS_OUTBOUND_3	= 2;
	private static final int		PS_OUTBOUND_4	= 3;

	private static final int		PS_INBOUND_1	= 10;
	private static final int		PS_INBOUND_2	= 11;
	private static final int		PS_INBOUND_3	= 12;
	private static final int		PS_INBOUND_4	= 13;

	public static final byte[]	KEYA_IV	= "keyA".getBytes();
	public static final byte[]	KEYB_IV	= "keyB".getBytes();
	public static final byte[]	REQ1_IV	= "req1".getBytes();
	public static final byte[]	REQ2_IV	= "req2".getBytes();
	public static final byte[]	REQ3_IV	= "req3".getBytes();
	public static final byte[]	VC		= { 0,0,0,0,0,0,0,0};



	private TransportHelper		transport;
	private ByteBuffer			write_buffer;
	private ByteBuffer			read_buffer;


	private ProtocolDecoderAdapter	adapter;

	private KeyAgreement 	key_agreement;
	private byte[]			dh_public_key_bytes;

	private byte[]			shared_secret;
	private byte[]			secret_bytes;

	private ByteBuffer		initial_data_out;
	private ByteBuffer		initial_data_in;

	private TransportCipher		write_cipher;
	private TransportCipher		read_cipher;

	private byte[]			padding_skip_marker;

	private byte			my_supported_protocols;
	private byte			selected_protocol;

	private boolean	outbound;

	private int		protocol_state;
	private int		protocol_substate;

	private boolean	handshake_complete;

	private int		bytes_read;
	private int		bytes_written;

	private long	last_read_time	= SystemTime.getCurrentTime();

	private TransportHelperFilter		filter;

	private boolean			delay_outbound_4;

	private boolean processing_complete;

	private final AEMonitor	process_mon	= new AEMonitor( "ProtocolDecoderPHE:process" );

	public
	ProtocolDecoderPHE(
		TransportHelper				_transport,
		byte[][]					_shared_secrets,
		ByteBuffer					_header,
		ByteBuffer					_initial_data,
		ProtocolDecoderAdapter		_adapter )

		throws IOException
	{
		super( false );

		if ( !isCryptoOK()){

			throw( new IOException( "PHE crypto broken" ));
		}

		transport			= _transport;
		// scattering mode for the first KB, that should include the crypto handshake and part of the bittorrent handshake
		transport.setScatteringMode(768+random.nextInt(256));
		initial_data_out	= _initial_data;
		adapter				= _adapter;

		if ( _shared_secrets == null || _shared_secrets.length == 0 ){

			shared_secret	= new byte[0];

		}else{

			if ( _shared_secrets.length == 1 ){

				shared_secret	= _shared_secrets[0];

			}else{

				shared_secret	= _shared_secrets[random.nextInt( _shared_secrets.length )];
			}

			// System.out.println( "outbound - using crypto secret " + ByteFormatter.encodeString( shared_secret ));
		}

		outbound	= _header == null;

		my_supported_protocols = SUPPORTED_PROTOCOLS;

		if ( outbound ){

			//if ( !NetworkManager.REQUIRE_CRYPTO_HANDSHAKE ){
			//	throw( new IOException( "Crypto encoder selected for outbound but crypto not required" ));
			//}

				// outbound connection, we require a certain minimal level of support

			my_supported_protocols = MIN_CRYPTO;

		}else{

				// incoming. If we require crypto then we use minimum otherwise available

			if ( NetworkManager.REQUIRE_CRYPTO_HANDSHAKE ){

				my_supported_protocols = MIN_CRYPTO;
			}
		}

		initCrypto();

		try{
			process_mon.enter();

			transport.registerForReadSelects(
				new TransportHelper.selectListener()
				{
				   	@Override
				    public boolean
			    	selectSuccess(
			    		TransportHelper	helper,
			    		Object 			attachment )
				   	{
				   		return( ProtocolDecoderPHE.this.selectSuccess( helper, attachment, false ));
				   	}

			        @Override
			        public void
			        selectFailure(
			        	TransportHelper	helper,
			        	Object 			attachment,
			        	Throwable 		msg)
			        {
			        	ProtocolDecoderPHE.this.selectFailure( helper, attachment, msg );
			        }
				},
				null );

			transport.registerForWriteSelects(
					new TransportHelper.selectListener()
					{
					   	@Override
					    public boolean
				    	selectSuccess(
				    		TransportHelper	helper,
				    		Object 			attachment )
					   	{
					   		return( ProtocolDecoderPHE.this.selectSuccess( helper, attachment, true ));
					   	}

				        @Override
				        public void
				        selectFailure(
				        	TransportHelper	helper,
				        	Object 			attachment,
				        	Throwable 		msg)
				        {
				        	ProtocolDecoderPHE.this.selectFailure( helper, attachment, msg );
				        }
					},
					null );

			transport.pauseWriteSelects();

			if ( outbound ){

				protocol_state	= PS_OUTBOUND_1;

				transport.pauseReadSelects();

			}else{

				protocol_state	= PS_INBOUND_1;

				read_buffer = ByteBuffer.allocate( dh_public_key_bytes.length );

				read_buffer.put( _header );

				bytes_read += _header.limit();
			}
		}finally{

			process_mon.exit();
		}

		process();
	}

	protected void
	initCrypto()

		throws IOException
	{
		try{
	        KeyPair key_pair = generateDHKeyPair( transport, outbound );

	        key_agreement = KeyAgreement.getInstance("DH");

	        key_agreement.init(key_pair.getPrivate());

	        DHPublicKey	dh_public_key = (DHPublicKey)key_pair.getPublic();

	        BigInteger	dh_y = dh_public_key.getY();

	        dh_public_key_bytes = bigIntegerToBytes( dh_y, DH_SIZE_BYTES );

		}catch( Throwable e ){

			throw( new IOException( Debug.getNestedExceptionMessage(e)));
		}
	}

	protected void
	completeDH(
		byte[]	buffer )

		throws IOException
	{
		try{
	        BigInteger	other_dh_y = bytesToBigInteger( buffer, 0, DH_SIZE_BYTES );

	        KeyFactory dh_key_factory = KeyFactory.getInstance("DH");

		    PublicKey other_public_key = dh_key_factory.generatePublic( new DHPublicKeySpec( other_dh_y, DH_P_BI, DH_G_BI ));

		    key_agreement.doPhase( other_public_key, true );

		    secret_bytes = key_agreement.generateSecret();

		    adapter.gotSecret( secret_bytes );

		    // System.out.println( "secret = " + ByteFormatter.encodeString( secret_bytes ));

		}catch( Throwable e ){

			throw( new IOException( Debug.getNestedExceptionMessage(e)));
		}
	}

	protected void
	setupCrypto()

		throws IOException
	{
		try{
		    //"HASH('keyA', S, SKEY)" if you're A
		    //"HASH('keyB', S, SKEY)" if you're B

		    SHA1Hasher	hasher = new SHA1Hasher();

		    hasher.update( KEYA_IV );
		    hasher.update( secret_bytes );
		    hasher.update( shared_secret );

		    byte[]	a_key = hasher.getDigest();

		    hasher = new SHA1Hasher();

		    hasher.update( KEYB_IV );
		    hasher.update( secret_bytes );
		    hasher.update( shared_secret );

		    byte[]	b_key = hasher.getDigest();

		    SecretKeySpec	secret_key_spec_a = new SecretKeySpec( a_key, RC4_STREAM_ALG );

		    SecretKeySpec	secret_key_spec_b = new SecretKeySpec( b_key, RC4_STREAM_ALG );

		    write_cipher 	= new TransportCipher( RC4_STREAM_CIPHER, Cipher.ENCRYPT_MODE, outbound?secret_key_spec_a:secret_key_spec_b );

		    read_cipher 	= new TransportCipher( RC4_STREAM_CIPHER, Cipher.DECRYPT_MODE, outbound?secret_key_spec_b:secret_key_spec_a );

		}catch( Throwable e ){

			e.printStackTrace();

			throw( new IOException( Debug.getNestedExceptionMessage(e)));
		}
	}

	/*
	protected void
	completeDH(
		byte[]	buffer )

		throws IOException
	{
		try{
	        BigInteger	other_dh_y = bytesToBigInteger( buffer, 0, DH_SIZE_BYTES );

	        KeyFactory dh_key_factory = KeyFactory.getInstance("DH");

		    PublicKey other_public_key = dh_key_factory.generatePublic( new DHPublicKeySpec( other_dh_y, DH_P_BI, DH_G_BI ));

		    key_agreement.doPhase( other_public_key, true );

		    byte[]	secret_bytes_64 = key_agreement.generateSecret();

		    	// we only want the first 32 bytes of the secret

		    secret_bytes = new byte[32];

		    System.arraycopy( secret_bytes_64, 0, secret_bytes, 0, 32 );

		    sha1_secret_bytes	= new SHA1Simple().calculateHash( secret_bytes );

		    SecretKeySpec	secret_key_spec_a = new SecretKeySpec( secret_bytes, 0, RC4_STREAM_KEY_SIZE_BYTES, RC4_STREAM_ALG );

		    SecretKeySpec	secret_key_spec_b = new SecretKeySpec( secret_bytes, 16, RC4_STREAM_KEY_SIZE_BYTES, RC4_STREAM_ALG );

		    write_cipher 	= new TCPTransportCipher( RC4_STREAM_CIPHER, Cipher.ENCRYPT_MODE, outbound?secret_key_spec_a:secret_key_spec_b );

		    read_cipher 	= new TCPTransportCipher( RC4_STREAM_CIPHER, Cipher.DECRYPT_MODE, outbound?secret_key_spec_b:secret_key_spec_a );

		}catch( Throwable e ){

			throw( new IOException( Debug.getNestedExceptionMessage(e)));
		}
	}
	*/

	protected void
	handshakeComplete()

		throws IOException
	{
		if ( selected_protocol == CRYPTO_PLAIN ){

			filter = new TransportHelperFilterTransparent( transport, true );

		}else if ( selected_protocol == CRYPTO_XOR ){

			filter = new TransportHelperFilterStreamXOR( transport, secret_bytes );

		}else if ( selected_protocol == CRYPTO_RC4 ){

			filter = new TransportHelperFilterStreamCipher(
						transport,
						read_cipher,
						write_cipher );

			/*
		}else if ( selected_protocol == CRYPTO_AES ){

			try{
		        SecretKeySpec	secret_key_spec = new SecretKeySpec( secret_bytes, 32, AES_STREAM_KEY_SIZE_BYTES, AES_STREAM_ALG );

		        AlgorithmParameterSpec	spec = 	new IvParameterSpec( secret_bytes, 48, AES_STREAM_KEY_SIZE_BYTES );

		        write_cipher 	= new TCPTransportCipher( AES_STREAM_CIPHER, Cipher.ENCRYPT_MODE, secret_key_spec, spec );

		        read_cipher 	= new TCPTransportCipher( AES_STREAM_CIPHER, Cipher.DECRYPT_MODE, secret_key_spec, spec );

				filter = new TCPTransportHelperFilterStreamCipher(
						helper,
						read_cipher,
						write_cipher );

			}catch( Throwable e ){

				throw( new IOException( "AES crypto init failed: " + Debug.getNestedExceptionMessage(e)));
			}
		*/


		}else{

			throw( new IOException( "Invalid selected protocol '" + selected_protocol + "'" ));
		}

		if ( initial_data_in != null ){

			filter = new TransportHelperFilterInserter(	filter, initial_data_in );
		}

		handshake_complete	= true;
	}


	/*
	  		X_1

	  	A->B: Diffie Hellman Ya, PadA

	  		X_2

		B->A: Diffie Hellman Yb, PadB

			X_3

		A->B: HASH('req1', S), HASH('req2', SKEY)^HASH('req3', S), ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)), ENCRYPT(IA)

			X_4

		B->A: ENCRYPT(VC, crypto_select, len(padD), padD ) // , len(IB)), ENCRYPT(IB)
	*/


	protected void
	process()

		throws IOException
	{
		try{
			process_mon.enter();

			if ( handshake_complete ){

				Debug.out( "Handshake process already completed" );

				return;
			}

			boolean	loop = true;

			while( loop ){

				// System.out.println( this + ":" + (outbound?"out: ":"in : ") + protocol_state + "/" + protocol_substate + ": r " + bytes_read + " - " + read_buffer + ", w " + bytes_written + " - " + write_buffer );

				if ( protocol_state == PS_OUTBOUND_1 ){

					if ( write_buffer == null ){

							// A sends B Ya + Pa

						byte[]	padding_a = getRandomPadding(getPaddingMax()/2);	// note that /2 also used in calculating max initial packet size above

						write_buffer = ByteBuffer.allocate( dh_public_key_bytes.length + padding_a.length );

						write_buffer.put( dh_public_key_bytes );

						write_buffer.put( padding_a );

						write_buffer.flip();
					}

					write( write_buffer );

					if ( !write_buffer.hasRemaining()){

						write_buffer	= null;

						protocol_state	= PS_INBOUND_2;
					}

				}else if ( protocol_state == PS_INBOUND_1 ){

						// B receives Ya

					read( read_buffer );

					if ( !read_buffer.hasRemaining()){

						read_buffer.flip();

						byte[] other_dh_public_key_bytes = new byte[read_buffer.remaining()];

						read_buffer.get( other_dh_public_key_bytes );

						completeDH( other_dh_public_key_bytes );

				        read_buffer	= null;

						protocol_state	= PS_OUTBOUND_2;
					}

				}else if ( protocol_state == PS_OUTBOUND_2 ){

						// B->A: Yb PadB

					if ( write_buffer == null ){

						byte[]	padding_b = getRandomPadding( getPaddingMax()/2 );

						write_buffer = ByteBuffer.allocate( dh_public_key_bytes.length + padding_b.length );

						write_buffer.put( dh_public_key_bytes );

						write_buffer.put( padding_b );

						write_buffer.flip();
					}

					write( write_buffer );

					if ( !write_buffer.hasRemaining()){

						write_buffer	= null;

						protocol_state	= PS_INBOUND_3;
					}

				}else if ( protocol_state == PS_INBOUND_2 ){

						// A receives: Yb

					if ( read_buffer == null ){

						read_buffer = ByteBuffer.allocate( dh_public_key_bytes.length );
					}

					read( read_buffer );

					if ( !read_buffer.hasRemaining()){

						read_buffer.flip();

						byte[] other_dh_public_key_bytes = new byte[read_buffer.remaining()];

						read_buffer.get( other_dh_public_key_bytes );

						completeDH( other_dh_public_key_bytes );

							// A initiates SKEY so we can now set up crypto

						setupCrypto();

				        read_buffer	= null;

						protocol_state	= PS_OUTBOUND_3;
					}

				}else if ( protocol_state == PS_OUTBOUND_3 ){

						// A->B: HASH('req1', S), HASH('req2', SKEY)^HASH('req3', S), ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)), ENCRYPT(IA)

					if ( write_buffer == null ){

						int initial_data_out_len	= initial_data_out==null?0:initial_data_out.remaining();

							// padding_a here is half of the padding from before

						int	pad_max = getPaddingMax();

						byte[]	padding_a = getRandomPadding(pad_max/2);

						byte[]	padding_c = getZeroPadding(pad_max);

						write_buffer = ByteBuffer.allocate( padding_a.length + 20 + 20 + ( VC.length + 4 + 2 + padding_c.length + 2 ) + initial_data_out_len );

						write_buffer.put( padding_a );

							// HASH('req1', S)

						SHA1Hasher	hasher = new SHA1Hasher();

						hasher.update( REQ1_IV );
						hasher.update( secret_bytes );

						byte[] sha1 = hasher.getDigest();

						write_buffer.put( sha1 );

							// HASH('req2', SKEY)^HASH('req3', S)

						hasher = new SHA1Hasher();

						hasher.update( REQ2_IV );
						hasher.update( shared_secret );

						byte[] sha1_1 = hasher.getDigest();

						hasher = new SHA1Hasher();

						hasher.update( REQ3_IV );
						hasher.update( secret_bytes );

						byte[] sha1_2 = hasher.getDigest();

						for (int i=0;i<sha1_1.length;i++){

							sha1_1[i] ^= sha1_2[i];
						}

						write_buffer.put( sha1_1 );

							// ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)

						write_buffer.put( write_cipher.update( VC ));

						write_buffer.put( write_cipher.update( new byte[]{ 0, 0, 0, my_supported_protocols }));

						write_buffer.put( write_cipher.update( new byte[]{ (byte)(padding_c.length>>8),(byte)padding_c.length }));

						write_buffer.put( write_cipher.update( padding_c ));

						write_buffer.put( write_cipher.update( new byte[]{ (byte)(initial_data_out_len>>8),(byte)initial_data_out_len }));

						if ( initial_data_out_len > 0 ){

							int	save_pos = initial_data_out.position();

							write_cipher.update( initial_data_out, write_buffer );

								// reset in case buffer needs to be used again by caller

							initial_data_out.position( save_pos );

							initial_data_out = null;
						}

						write_buffer.flip();
					}

					write( write_buffer );

					if ( !write_buffer.hasRemaining()){

						write_buffer	= null;

						protocol_state	= PS_INBOUND_4;
					}

				}else if ( protocol_state == PS_INBOUND_3 ){

						// B receives: HASH('req1', S), HASH('req2', SKEY)^HASH('req3', S), ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)), ENCRYPT(IA)

					if ( read_buffer == null ){

						read_buffer = ByteBuffer.allocate( 20 + PADDING_MAX );

						read_buffer.limit( 20 );

						SHA1Hasher hasher = new SHA1Hasher();

						hasher.update( REQ1_IV );
						hasher.update( secret_bytes );

						padding_skip_marker = hasher.getDigest();

						protocol_substate	= 1;
					}

					while( true ){

						read( read_buffer );

						if ( read_buffer.hasRemaining()){

							break;
						}

						if ( protocol_substate == 1 ){

							 	//skip up to HASH('req1', S)

							int	limit = read_buffer.limit();

							read_buffer.position( limit - 20 );

							boolean match	= true;

							for (int i=0;i<20;i++){

								if ( read_buffer.get() != padding_skip_marker[i] ){

									match	= false;

									break;
								}
							}

							if ( match ){

								read_buffer = ByteBuffer.allocate( 20 + VC.length + 4 + 2 );

								protocol_substate	= 2;

								break;

							}else{

								if ( limit == read_buffer.capacity()){

									throw( new IOException( "PHE skip to SHA1 marker failed" ));
								}

								read_buffer.limit( limit + 1 );

								read_buffer.position( limit );
							}
						}else if ( protocol_substate == 2 ){

								// find SKEY using HASH('req2', SKEY)^HASH('req3', S)  , ENCRYPT(VC, crypto_provide, len(PadC),

							read_buffer.flip();

							final byte[]	decode = new byte[20];

							read_buffer.get( decode );

							SHA1Hasher hasher = new SHA1Hasher();

							hasher.update( REQ3_IV );
							hasher.update( secret_bytes );

							byte[] sha1 = hasher.getDigest();

							for (int i=0;i<decode.length;i++){

								decode[i] ^= sha1[i];
							}

							HashWrapper hw = new HashWrapper( decode );
							
							String missing_secret_name = null;
							
							synchronized( global_shared_secrets ){

								Object[] entry = global_shared_secrets.get( hw );
								
								if ( entry == null ){
									
									shared_secret = null;
									
									entry = old_global_shared_secrets.get( hw );
									
									if ( entry != null ){
										
										missing_secret_name = (String)entry[1];
									}
								}else{
									
									shared_secret = (byte[])entry[0];
								}
							}

							if ( shared_secret == null ){

								throw( new IOException( "No matching shared secret: " + transport.getAddress() + " (" + (missing_secret_name==null?"Unknown":missing_secret_name) + ")" ));
							}

							// System.out.println( "inbound - using crypto secret " + ByteFormatter.encodeString( shared_secret ));

							setupCrypto();

							byte[]	crypted = new byte[VC.length + 4 + 2];

							read_buffer.get( crypted );

							byte[]	plain = read_cipher.update( crypted );

							byte	other_supported_protocols = plain[VC.length+3];

							int	common_protocols = my_supported_protocols & other_supported_protocols;

							if (( common_protocols & CRYPTO_PLAIN )!= 0 ){

								selected_protocol = CRYPTO_PLAIN;

							}else if (( common_protocols & CRYPTO_XOR )!= 0 ){

								selected_protocol = CRYPTO_XOR;

							}else if (( common_protocols & CRYPTO_RC4 )!= 0 ){

								selected_protocol = CRYPTO_RC4;

							}else if (( common_protocols & CRYPTO_AES )!= 0 ){

								selected_protocol = CRYPTO_AES;

							}else{

								throw( new IOException(
										"No crypto protocol in common: mine = " +
											Integer.toHexString((byte)my_supported_protocols) + ", theirs = " +
											Integer.toHexString((byte)other_supported_protocols)));

							}

							int	padding	= (( plain[VC.length+4] & 0xff ) << 8 ) + ( plain[VC.length+5] & 0xff );

							if ( padding > PADDING_MAX ){

								throw( new IOException( "Invalid padding '" + padding + "'" ));
							}

							read_buffer = ByteBuffer.allocate( padding + 2 );

								// skip the padding

							protocol_substate	= 3;

						}else if ( protocol_substate == 3 ){

								// ENCRYPT( len(IA)),  { ENCRYPT(IA) }

							read_buffer.flip();

							byte[]	data = new byte[read_buffer.remaining()];

							read_buffer.get( data );

							data = read_cipher.update( data );

							int	ia_len	= 0xffff & ((( data[data.length-2] & 0xff ) << 8 ) + ( data[data.length-1] & 0xff ));

							if ( ia_len > 65535 ){

								throw( new IOException( "Invalid IA length '" + ia_len + "'" ));
							}

							if ( ia_len > 0 ){

								read_buffer = ByteBuffer.allocate( ia_len );

								// skip the padding

								protocol_substate	= 4;

							}else{

								read_buffer	= null;

								protocol_state = PS_OUTBOUND_4;

								break;
							}
						}else if ( protocol_substate == 4 ){

							// ENCRYPT(IA)

							read_buffer.flip();

							byte[]	data = new byte[read_buffer.remaining()];

							read_buffer.get( data );

							data = read_cipher.update( data );

								// hack alert - we can delay the writing of the outbound_4 packet if this is an incoming packet with
								// a piggybacked bt handshake as we know that we'll be sending our own handshake back out pretty soon
								// and it'll take the delayed data with it. To be more generic we'd need to add a callback to the pattern
								// matcher to allow it to decide whether delaying was sensible / or stick a timer on the delayed data

							delay_outbound_4 = new String(data).contains("BitTorrent");

							// System.out.println( "Initial Data In: " + new String( data ) + "->delay=" +delay_outbound_4 );

							initial_data_in = ByteBuffer.wrap( data );

							read_buffer	= null;

							protocol_state = PS_OUTBOUND_4;

							break;
						}
					}
				}else if ( protocol_state == PS_OUTBOUND_4 ){

						// B->A: ENCRYPT(VC, crypto_select, len(padD), padD, // len(IB)), ENCRYPT(IB)

					if ( write_buffer == null ){

						int	pad_max = getPaddingMax();

						byte[]	padding_b = getRandomPadding( pad_max/2 );	// half padding b sent here

						byte[]	padding_d = getZeroPadding( pad_max );

						write_buffer = ByteBuffer.allocate( padding_b.length + VC.length + 4 + 2 + padding_d.length ); // + 2 + initial_data_out.length );

						write_buffer.put( padding_b );

						write_buffer.put( write_cipher.update( VC ));

						write_buffer.put( write_cipher.update( new byte[]{ 0, 0, 0, selected_protocol }));

						write_buffer.put( write_cipher.update( new byte[]{ (byte)(padding_d.length>>8),(byte)padding_d.length }));

						write_buffer.put( write_cipher.update( padding_d ));

						//write_buffer.put( write_cipher.update( new byte[]{ (byte)(initial_data_out.length>>8),(byte)initial_data_out.length }));

						//write_buffer.put( write_cipher.update( initial_data_out ));

						write_buffer.flip();
					}

					if ( delay_outbound_4 ){

						if ( transport.delayWrite( write_buffer )){

							write_buffer	= null;

							handshakeComplete();

						}else{

							delay_outbound_4 = false;
						}
					}

					if ( !delay_outbound_4 ){

						write( write_buffer );

						if ( !write_buffer.hasRemaining()){

							write_buffer	= null;

							handshakeComplete();
						}
					}
				}else if ( protocol_state == PS_INBOUND_4 ){

						// B->A: ENCRYPT(VC, crypto_select, len(padD), padD // , len(IB)), ENCRYPT(IB)

					if ( read_buffer == null ){

						read_buffer = ByteBuffer.allocate( VC.length + PADDING_MAX );

						read_buffer.limit( VC.length );

						padding_skip_marker	= new byte[VC.length];

						padding_skip_marker	= read_cipher.update( padding_skip_marker );

						protocol_substate	= 1;
					}

					while( true ){

						read( read_buffer );

						if ( read_buffer.hasRemaining()){

							break;
						}

						if ( protocol_substate == 1 ){

							 	//skip up to marker

							int	limit = read_buffer.limit();

							read_buffer.position( limit - VC.length );

							boolean match	= true;

							for (int i=0;i<VC.length;i++){

								if ( read_buffer.get() != padding_skip_marker[i] ){

									match	= false;

									break;
								}
							}

							if ( match ){

								read_buffer = ByteBuffer.allocate( 4 + 2 );

								protocol_substate	= 2;

								break;

							}else{

								if ( limit == read_buffer.capacity()){

									throw( new IOException( "PHE skip to SHA1 marker failed" ));
								}

								read_buffer.limit( limit + 1 );

								read_buffer.position( limit );
							}
						}else if ( protocol_substate == 2 ){

								//  ENCRYPT( crypto_select, len(padD))

							read_buffer.flip();

							byte[]	crypted = new byte[4 + 2];

							read_buffer.get( crypted );

							byte[]	plain = read_cipher.update( crypted );

							selected_protocol = plain[3];

							if (( selected_protocol & my_supported_protocols ) == 0 ){


								throw( new IOException(
										"Selected protocol has nothing in common: mine = " +
											Integer.toHexString((byte)my_supported_protocols) + ", theirs = " +
											Integer.toHexString((byte)selected_protocol)));

							}

							int	pad_len	= 0xffff&((( plain[4] & 0xff ) << 8 ) + ( plain[5] & 0xff ));

							if ( pad_len > 65535 ){

								throw( new IOException( "Invalid pad length '" + pad_len + "'" ));
							}

							read_buffer = ByteBuffer.allocate( pad_len ); // + 2 );

							protocol_substate	= 3;

						}else if ( protocol_substate == 3 ){

							read_buffer.flip();

							byte[]	data = new byte[read_buffer.remaining()];

							read_buffer.get( data );

							data = read_cipher.update( data );

							handshakeComplete();

							read_buffer	= null;

							break;
							/*
							int	ib_len	= 0xffff & ((( data[data.length-2] & 0xff ) << 8 ) + ( data[data.length-1] & 0xff ));

							if ( ib_len > 65535 ){

								throw( new IOException( "Invalid IB length '" + ib_len + "'" ));
							}

							read_buffer = ByteBuffer.allocate( ib_len );

							protocol_substate	= 4;

						}else{

							read_buffer.flip();

							byte[]	data = new byte[read_buffer.remaining()];

							read_buffer.get( data );

							initial_data_in = read_cipher.update( data );

							handshakeComplete();

							read_buffer	= null;

							break;
							*/
						}
					}
				}

				if ( handshake_complete ){

					transport.cancelReadSelects();

					transport.cancelWriteSelects();

					loop	= false;

					complete();

				}else{

					if ( read_buffer == null ){

						transport.pauseReadSelects();

					}else{

						transport.resumeReadSelects();

						loop	= false;

					}

					if ( write_buffer == null ){

						transport.pauseWriteSelects();

					}else{

						transport.resumeWriteSelects();

						loop	= false;
					}
				}
			}
		}catch( Throwable e ){

			failed( e );

			if ( e instanceof IOException ){

				throw((IOException)e);

			}else{

				throw( new IOException( Debug.getNestedExceptionMessage(e)));
			}
		}finally{

			process_mon.exit();
		}
	}




	/*
			**** OUTBOUND_1

		A sends B odd/even byte + Ya + Pa

			**** INBOUND_1

		B receives Ya
		B computes Yb
		B computes S and HS

		**** OUTBOUND_2

		B sends A Yb + HS( "supported methods" + len(Pb)) + Pb

			**** INBOUND_2

		A receives Yb
		A computes S and HS
		A receives HS( "supported methods" + len(Pb)) and decrypts using HS
		A skips len(Pb) random bytes

		**** OUTBOUND_3

		A sends SHA1(S) + HS( "selected method" + len(Pc)) + Pc + selectedCrypt( payload )

			**** INBOUND_3

		B skips Pa bytes until receives SHA1(S)
		B decrypts "selected method" + len(Pc) and skips len(Pc) bytes to get to selectedCrypt( payload... )
		B sends A selectedCrypt( payload... )
	 */




	/*
	protected void
	process()

		throws IOException
	{
		try{
			process_mon.enter();

			if ( handshake_complete ){

				Debug.out( "Handshake process already completed" );

				return;
			}

			boolean	loop = true;

			while( loop ){

				if ( protocol_state == PS_OUTBOUND_1 ){

					if ( write_buffer == null ){

							// A sends B odd/even Ya + Pa


						byte[]	padding = getPadding();

						write_buffer = ByteBuffer.allocate( dh_public_key_bytes.length + padding.length );

						write_buffer.put( dh_public_key_bytes );

						write_buffer.put( padding );

						write_buffer.flip();
					}

					write( write_buffer );

					if ( !write_buffer.hasRemaining()){

						write_buffer	= null;

						protocol_state	= PS_INBOUND_2;
					}

				}else if ( protocol_state == PS_OUTBOUND_2 ){

						// B sends A Yb + HS( "supported methods" + len(Pb)) + Pb

					if ( write_buffer == null ){

						byte[]	padding = getPadding();

						write_buffer = ByteBuffer.allocate( dh_public_key_bytes.length + 4 + 2 + padding.length );

						write_buffer.put( dh_public_key_bytes );

							// 4 bytes for my supported protocols

						write_buffer.put( write_cipher.update( new byte[]{ 0, 0, 0, my_supported_protocols }));

						write_buffer.put( write_cipher.update( new byte[]{ (byte)(padding.length>>8),(byte)padding.length }));

						write_buffer.put( padding );

						write_buffer.flip();
					}

					write( write_buffer );

					if ( !write_buffer.hasRemaining()){

						write_buffer	= null;

						protocol_state	= PS_INBOUND_3;
					}

				}else if ( protocol_state == PS_OUTBOUND_3 ){

						// 	A sends SHA1(S) + HS( "selected method" + len(Pc)) + Pc + selectedCrypt( payload )

					if ( write_buffer == null ){

						byte[]	padding = getPadding();

						write_buffer = ByteBuffer.allocate( 20 + 4 + 2 + padding.length );

						write_buffer.put( sha1_secret_bytes );

						write_buffer.put( write_cipher.update( new byte[]{ 0, 0, 0, selected_protocol }));

						write_buffer.put( write_cipher.update( new byte[]{ (byte)(padding.length>>8),(byte)padding.length }));

						write_buffer.put( padding );

						write_buffer.flip();
					}

					write( write_buffer );

					if ( !write_buffer.hasRemaining()){

						write_buffer	= null;

						handshakeComplete();
					}

				}else if ( protocol_state == PS_INBOUND_1 ){

						// B receives marker + Ya

					read( read_buffer );

					if ( !read_buffer.hasRemaining()){

						read_buffer.flip();

						byte[] other_dh_public_key_bytes = new byte[read_buffer.remaining()];

						read_buffer.get( other_dh_public_key_bytes );

						completeDH( other_dh_public_key_bytes );

				        read_buffer	= null;

						protocol_state	= PS_OUTBOUND_2;
					}

				}else if ( protocol_state == PS_INBOUND_2 ){

					//A receives Yb
					//A computes S and HS
					//A receives HS( "supported methods" + len(Pb)) and decrypts using HS
					//A skips len(Pb) random bytes

					if ( read_buffer == null ){

						read_buffer = ByteBuffer.allocate( dh_public_key_bytes.length + 6 );

						protocol_substate	= 1;
					}

					while( true ){

						read( read_buffer );

						if ( read_buffer.hasRemaining()){

							break;
						}

						if ( protocol_substate == 1 ){

							read_buffer.flip();

							byte[] other_dh_public_key_bytes_etc = read_buffer.array();

							completeDH( other_dh_public_key_bytes_etc );

							byte[]	etc = read_cipher.update( other_dh_public_key_bytes_etc, DH_SIZE_BYTES, 6 );

							byte	other_supported_protocols = etc[3];

							int	common_protocols = my_supported_protocols & other_supported_protocols;

							if (( common_protocols & CRYPTO_PLAIN )!= 0 ){

								selected_protocol = CRYPTO_PLAIN;

							}else if (( common_protocols & CRYPTO_XOR )!= 0 ){

								selected_protocol = CRYPTO_XOR;

							}else if (( common_protocols & CRYPTO_RC4 )!= 0 ){

								selected_protocol = CRYPTO_RC4;

							}else if (( common_protocols & CRYPTO_AES )!= 0 ){

								selected_protocol = CRYPTO_AES;

							}else{

								throw( new IOException(
										"No crypto protocol in common: mine = " +
											Integer.toHexString((byte)my_supported_protocols) + ", theirs = " +
											Integer.toHexString((byte)other_supported_protocols)));

							}

							int	padding	= (( etc[4] & 0xff ) << 8 ) + ( etc[5] & 0xff );

							if ( padding > PADDING_MAX ){

								throw( new IOException( "Invalid padding '" + padding + "'" ));
							}

							read_buffer = ByteBuffer.allocate( padding );

							protocol_substate	= 2;

						}else{

							read_buffer	= null;

							protocol_state	= PS_OUTBOUND_3;

							break;
						}
					}
				}else if ( protocol_state == PS_INBOUND_3 ){


					//	B skips Pa bytes until receives SHA1(S)
					//	B decrypts "selected method" + len(Pc) and skips len(Pc) bytes

					if ( read_buffer == null ){

						read_buffer = ByteBuffer.allocate( 20 + PADDING_MAX );

						read_buffer.limit( 20 );

						protocol_substate	= 1;
					}

					while( true ){

						read( read_buffer );

						if ( read_buffer.hasRemaining()){

							break;
						}

						if ( protocol_substate == 1 ){

							int	limit = read_buffer.limit();

							read_buffer.position( limit - 20 );

							boolean match	= true;

							for (int i=0;i<20;i++){

								if ( read_buffer.get() != sha1_secret_bytes[i] ){

									match	= false;

									break;
								}
							}

							if ( match ){

								read_buffer = ByteBuffer.allocate( 6 );

								protocol_substate	= 2;

								break;

							}else{

								if ( limit == read_buffer.capacity()){

									throw( new IOException( "PHE skip to SHA1 marker failed" ));
								}

								read_buffer.limit( limit + 1 );

								read_buffer.position( limit );
							}
						}else if ( protocol_substate == 2 ){

							read_buffer.flip();

							byte[]	etc = read_cipher.update( read_buffer.array());

							selected_protocol = etc[3];

							int	padding	= (( etc[4] & 0xff ) << 8 ) + ( etc[5] & 0xff );

							if ( padding > PADDING_MAX ){

								throw( new IOException( "Invalid padding '" + padding + "'" ));
							}

							read_buffer = ByteBuffer.allocate( padding );

							protocol_substate	= 3;

						}else{

							read_buffer	= null;

							handshakeComplete();

							break;
						}
					}
				}

				if ( handshake_complete ){

					read_selector.cancel( channel );

					write_selector.cancel( channel );

					loop	= false;

					complete();

				}else{

					if ( read_buffer == null ){

						read_selector.pauseSelects( channel );

					}else{

						read_selector.resumeSelects ( channel );

						loop	= false;

					}

					if ( write_buffer == null ){

						write_selector.pauseSelects( channel );

					}else{

						write_selector.resumeSelects ( channel );

						loop	= false;
					}
				}
			}
		}catch( Throwable e ){

			failed( e );

			if ( e instanceof IOException ){

				throw((IOException)e);

			}else{

				throw( new IOException( Debug.getNestedExceptionMessage(e)));
			}
		}finally{

			process_mon.exit();
		}
	}
	*/

	protected void
	read(
		ByteBuffer		buffer )

		throws IOException
	{
		int	len = transport.read( buffer );

		//System.out.println( "read:" + this + "/" + protocol_state + "/" + protocol_substate + " -> " + len +"[" + buffer +"]");

		if ( len < 0 ){

			throw( new IOException( "end of stream on socket read - phe: " + getString()));
		}

		bytes_read += len;
	}

	protected void
	write(
		ByteBuffer		buffer )

		throws IOException
	{
		//System.out.println( "write pre:" + this + "/" + protocol_state + "/" + protocol_substate + " - " + buffer );

		int	len = transport.write( buffer, false );

		//System.out.println( "write:" + this + "/" + protocol_state + "/" + protocol_substate + " -> " + len +"[" + buffer +"]");

		if ( len < 0 ){

			throw( new IOException( "bytes written < 0 " ));
		}

		bytes_written += len;
	}

	public boolean
	selectSuccess(
		TransportHelper			transport,
		Object 					attachment,
		boolean					write_operation )
	{
		try{
			int	old_bytes_read		= bytes_read;
			int	old_bytes_written	= bytes_written;

			process();

			if ( write_operation ){

				return( bytes_written != old_bytes_written );

			}else{

				boolean	progress = bytes_read != old_bytes_read;

				if ( progress ){

					last_read_time = SystemTime.getCurrentTime();
				}

				return( progress );
			}

		}catch( Throwable  e ){

			failed( e );

			return( false );
		}
	}

	public void
	selectFailure(
		TransportHelper			transport,
		Object 					attachment,
		Throwable				msg )
	{
		failed( msg );
	}

	protected byte[]
	bigIntegerToBytes(
		BigInteger	bi,
		int			num_bytes )
	{
		String	str = bi.toString(16);

		while( str.length() < num_bytes*2 ){
			str = "0" + str;
		}

		return( ByteFormatter.decodeString(str));
	}

	protected BigInteger
	bytesToBigInteger(
		byte[]	bytes,
		int		offset,
		int		len )
	{
		return( new BigInteger( ByteFormatter.encodeString( bytes, offset, len  ), 16 ));
	}

	protected int
	getPaddingMax()
	{
		if ( transport.minimiseOverheads()){

			return( PADDING_MAX_LIMITED );

		}else{

			return( PADDING_MAX_NORMAL );
		}
	}

	protected static synchronized byte[]
	getRandomPadding(
		int		max_len )
	{
		byte[]	bytes = new byte[ random.nextInt(max_len)];

		random.nextBytes(bytes);

		return( bytes );
	}

	protected static synchronized byte[]
   	getZeroPadding(
   		int	max_len )
   	{
   		byte[]	bytes = new byte[ random.nextInt(max_len)];

   		return( bytes );
   	}

	protected static KeyPair
	generateDHKeyPair(
		TransportHelper		transport,
		boolean				outbound )

		throws IOException
	{
		if ( dh_key_generator == null ){

			throw( new IOException( "Crypto not setup" ));
		}

		synchronized( dh_key_generator ){

			if ( !outbound ){

				InetSocketAddress is_address = transport.getAddress();

				byte[]	address = AddressUtils.getAddressBytes( is_address );

				int	hit_count = generate_bloom.add( address );

				long	now = SystemTime.getCurrentTime();

					// allow up to 10% bloom filter utilisation

				if ( generate_bloom.getSize() / generate_bloom.getEntryCount() < 10 ){

					generate_bloom = BloomFilterFactory.createAddRemove4Bit(generate_bloom.getSize() + BLOOM_INCREASE );

					generate_bloom_create_time	= now;

		     		Logger.log(	new LogEvent(LOGID, "PHE bloom: size increased to " + generate_bloom.getSize()));

				}else if ( now < generate_bloom_create_time || now - generate_bloom_create_time > BLOOM_RECREATE ){

					generate_bloom = BloomFilterFactory.createAddRemove4Bit(generate_bloom.getSize());

					generate_bloom_create_time	= now;
				}

				if ( hit_count >= 15 ){

		     		Logger.log(	new LogEvent(LOGID, "PHE bloom: too many recent connection attempts from " + transport.getAddress()));

					throw( new IOException( "Too many recent connection attempts (phe)"));
				}

				/*
				 * This is a pretty bad ideasas it simply stalls the entire read process and backs it up
				 * 
				long	since_last = now - last_dh_incoming_key_generate;

				long	delay = 100 - since_last;

					// limit key gen operations to 10 a second

				if ( delay > 0 && delay < 100 ){

					try{
						Thread.sleep( delay );

					}catch( Throwable e ){
					}
				}

				last_dh_incoming_key_generate = now;
				*/
			}

			KeyPair	res = dh_key_generator.generateKeyPair();

			return( res );
		}
	}

	protected void
	complete()
	{
		// System.out.println( (outbound?"out: ":"in :") + " complete, r " + bytes_read + ", w " + bytes_written + ", initial data = " + initial_data_in.length + "/" + initial_data_out.length );

		processing_complete	= true;

		transport.setScatteringMode(0);

		adapter.decodeComplete( this, initial_data_out );
	}

	protected void
	failed(
		Throwable 	cause )
	{
		// System.out.println( (outbound?"out: ":"in :") + " failed, " + cause.getMessage());

		processing_complete	= true;

		transport.cancelReadSelects();

		transport.cancelWriteSelects();

		adapter.decodeFailed( this, cause );
	}

	@Override
	public boolean
	isComplete(
		long		now )
	{
		return( processing_complete );
	}

	@Override
	public TransportHelperFilter
	getFilter()
	{
		return( filter );
	}

	public long
	getLastReadTime()
	{
		long	now = SystemTime.getCurrentTime();

		if ( last_read_time > now ){

			last_read_time	= now;
		}

		return( last_read_time );
	}

	public String
	getString()
	{
		return( "state=" + protocol_state + ",sub=" + protocol_substate + ",in=" + bytes_read + ",out=" + bytes_written);
	}
}
