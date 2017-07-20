/*
 * Created on 13-Jul-2004
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

/**
 * @author parg
 *
 */

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;

import org.gudy.bouncycastle.asn1.x509.X509Name;
import org.gudy.bouncycastle.jce.X509V3CertificateGenerator;
import org.gudy.bouncycastle.jce.provider.BouncyCastleProvider;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SystemTime;

public class
SESecurityManagerBC
{
	protected static void
	initialise()
	{
		try{
			Security.addProvider(new org.gudy.bouncycastle.jce.provider.BouncyCastleProvider());

			KeyFactory kf = KeyFactory.getInstance( "ECDSA", BouncyCastleProvider.PROVIDER_NAME );

			if ( Constants.IS_CVS_VERSION ){

				String	where = "";

				try{

					where = BouncyCastleProvider.class.getClassLoader().getResource( "org/gudy/bouncycastle/jce/provider/BouncyCastleProvider.class" ).toExternalForm();

				}catch( Throwable e ){

					ClassLoader cl = BouncyCastleProvider.class.getClassLoader();

					if ( cl == null ){

						where = "<bootstrap>";

					}else{

						where = cl.toString();
					}
				}

				if ( !where.contains( Constants.DEFAULT_JAR_NAME )){

					Debug.outNoStack( "BC Provider '" +  BouncyCastleProvider.PROVIDER_NAME + "' initialised successfully (loaded from " + where + ")" );
				}
			}
		}catch( Throwable e ){

			Debug.out( "BC Provider initialisation failed", e );
		}
	}

	public static Certificate
	createSelfSignedCertificate(
		SESecurityManagerImpl	manager,
		String					alias,
		String					cert_dn,
		int						strength )

		throws Exception
	{
		KeyPairGenerator	kg = KeyPairGenerator.getInstance( "RSA" );

		kg.initialize(strength, RandomUtils.SECURE_RANDOM );

		KeyPair pair = kg.generateKeyPair();

		X509V3CertificateGenerator certificateGenerator =
			new X509V3CertificateGenerator();

		certificateGenerator.setSignatureAlgorithm( "MD5WithRSAEncryption" );

		certificateGenerator.setSerialNumber( new BigInteger( ""+SystemTime.getCurrentTime()));

		X509Name	issuer_dn = new X509Name(true,cert_dn);

		certificateGenerator.setIssuerDN(issuer_dn);

		X509Name	subject_dn = new X509Name(true,cert_dn);

		certificateGenerator.setSubjectDN(subject_dn);

		Calendar	not_after = Calendar.getInstance();

		not_after.add(Calendar.YEAR, 1);

		certificateGenerator.setNotAfter( not_after.getTime());

		certificateGenerator.setNotBefore(Calendar.getInstance().getTime());

		certificateGenerator.setPublicKey( pair.getPublic());

		X509Certificate certificate = certificateGenerator.generateX509Certificate(pair.getPrivate());

		java.security.cert.Certificate[] certChain = {(java.security.cert.Certificate) certificate };

		manager.addCertToKeyStore( alias, pair.getPrivate(), certChain );

		return( certificate );
	}
}
