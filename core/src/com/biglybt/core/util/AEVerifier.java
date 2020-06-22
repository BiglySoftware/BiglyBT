/*
 * Created on 13 Jun 2006
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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class
AEVerifier
{
    private static final String pub_exp = "10001";
    private static final String modulus	= "9fdb0ce824917c111618b0a33b8596a7c259a8949a0e364c83efe10cc3e3914f17623bda9ad294a7a9477c2a9435a5dc9d0341e64aec419c4cbc90e5a13e25d3a51b6b8d2ccc9e2836e2322e3aede99344bc18548e3d2af4b5a37104881b87e6f98510b973ad76b8fee3db7e2598dec9d02b6a7fd4481f58c453a86e71b07a79f04924355ef64df7f478a37268ab44688c66aa8bdbe1991a8254fdbf2dfa64d3";

    public static void
	verifyData(
		File		file )

		throws AEVerifierException, Exception
	{
		KeyFactory key_factory = KeyFactory.getInstance("RSA");

		RSAPublicKeySpec 	public_key_spec =
			new RSAPublicKeySpec( new BigInteger(modulus,16), new BigInteger(pub_exp,16));

		RSAPublicKey public_key 	= (RSAPublicKey)key_factory.generatePublic( public_key_spec );

		verifyData( file, public_key );
	}

	protected static void
	verifyData(
		File			file,
		RSAPublicKey	key )

		throws AEVerifierException, Exception
	{
		ZipInputStream	zis = null;

		try{
			zis = new ZipInputStream(
					new BufferedInputStream( FileUtil.newFileInputStream( file ) ));

			byte[]		signature	= null;

			Signature	sig = Signature.getInstance("MD5withRSA" );

			sig.initVerify( key );

			while( true ){

				ZipEntry	entry = zis.getNextEntry();

				if ( entry == null ){

					break;
				}

				if ( entry.isDirectory()){

					continue;
				}

				String	name = entry.getName();

				ByteArrayOutputStream	output = null;

				if ( name.equalsIgnoreCase("azureus.sig")){

					output	= new ByteArrayOutputStream();
				}

				byte[]	buffer = new byte[65536];

				while( true ){

					int	len = zis.read( buffer );

					if ( len <= 0 ){

						break;
					}

					if ( output == null ){

						sig.update( buffer, 0, len );

					}else{

						output.write( buffer, 0, len );
					}
				}

				if ( output != null ){

					signature = output.toByteArray();
				}
			}

			if ( signature == null ){

				throw( new AEVerifierException( AEVerifierException.FT_SIGNATURE_MISSING, "Signature missing from file (" + file.getAbsolutePath() + ")" ));
			}

			if ( !sig.verify( signature )){

				throw( new AEVerifierException( AEVerifierException.FT_SIGNATURE_BAD, "Signature doesn't match data (" + file.getAbsolutePath() + ")" ));
			}
		}finally{

			if ( zis != null ){

				zis.close();
			}
		}
	}

	public static void
	verifyData(
		String			data,
		byte[]			signature )

		throws AEVerifierException, Exception
	{
		KeyFactory key_factory = KeyFactory.getInstance("RSA");

		RSAPublicKeySpec 	public_key_spec =
			new RSAPublicKeySpec( new BigInteger(modulus,16), new BigInteger(pub_exp,16));

		RSAPublicKey public_key 	= (RSAPublicKey)key_factory.generatePublic( public_key_spec );

		Signature	sig = Signature.getInstance("MD5withRSA" );

		sig.initVerify( public_key );

		sig.update( data.getBytes( "UTF-8" ));

		if ( !sig.verify( signature )){

			throw( new AEVerifierException( AEVerifierException.FT_SIGNATURE_BAD, "Data verification failed, signature doesn't match data" ));
		}
	}
}
