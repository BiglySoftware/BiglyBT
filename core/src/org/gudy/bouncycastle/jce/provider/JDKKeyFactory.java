/*
 * Created on 08-Jun-2004
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

package org.gudy.bouncycastle.jce.provider;

/**
 * @author parg
 *
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;

import org.gudy.bouncycastle.asn1.ASN1Sequence;
import org.gudy.bouncycastle.asn1.DERInputStream;
import org.gudy.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.gudy.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.gudy.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.gudy.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.gudy.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.gudy.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.gudy.bouncycastle.jce.interfaces.ElGamalPrivateKey;
import org.gudy.bouncycastle.jce.interfaces.ElGamalPublicKey;
import org.gudy.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.gudy.bouncycastle.jce.spec.ECPublicKeySpec;

import com.biglybt.core.util.Debug;

public abstract class
JDKKeyFactory
	extends KeyFactorySpi
{
    @Override
    protected KeySpec engineGetKeySpec(
            Key    key,
            Class    spec)
        throws InvalidKeySpecException
        {
           if (spec.isAssignableFrom(PKCS8EncodedKeySpec.class) && key.getFormat().equals("PKCS#8"))
           {
                   return new PKCS8EncodedKeySpec(key.getEncoded());
           }
           else if (spec.isAssignableFrom(X509EncodedKeySpec.class) && key.getFormat().equals("X.509"))
           {
                   return new X509EncodedKeySpec(key.getEncoded());
           }
           else if (spec.isAssignableFrom(RSAPublicKeySpec.class) && key instanceof RSAPublicKey)
           {
                RSAPublicKey    k = (RSAPublicKey)key;

                return new RSAPublicKeySpec(k.getModulus(), k.getPublicExponent());
           }
           else if (spec.isAssignableFrom(RSAPrivateKeySpec.class) && key instanceof RSAPrivateKey)
           {
                RSAPrivateKey    k = (RSAPrivateKey)key;

                return new RSAPrivateKeySpec(k.getModulus(), k.getPrivateExponent());
           }
           else if (spec.isAssignableFrom(RSAPrivateCrtKeySpec.class) && key instanceof RSAPrivateCrtKey)
           {
                RSAPrivateCrtKey    k = (RSAPrivateCrtKey)key;

                return new RSAPrivateCrtKeySpec(
                                k.getModulus(), k.getPublicExponent(),
                                k.getPrivateExponent(),
                                k.getPrimeP(), k.getPrimeQ(),
                                k.getPrimeExponentP(), k.getPrimeExponentQ(),
                                k.getCrtCoefficient());
           }


            throw new RuntimeException("not implemented yet " + key + " " + spec);
        }

        @Override
        protected Key engineTranslateKey(
            Key    key)
            throws InvalidKeyException
        {
            if (key instanceof RSAPublicKey)
            {
                return new JCERSAPublicKey((RSAPublicKey)key);
            }
            else if (key instanceof RSAPrivateCrtKey)
            {
                //return new JCERSAPrivateCrtKey((RSAPrivateCrtKey)key);
            }
            else if (key instanceof RSAPrivateKey)
            {
                //return new JCERSAPrivateKey((RSAPrivateKey)key);
            }
            else if (key instanceof DHPublicKey)
            {
                //return new JCEDHPublicKey((DHPublicKey)key);
            }
            else if (key instanceof DHPrivateKey)
            {
                //return new JCEDHPrivateKey((DHPrivateKey)key);
            }
            else if (key instanceof DSAPublicKey)
            {
                //return new JDKDSAPublicKey((DSAPublicKey)key);
            }
            else if (key instanceof DSAPrivateKey)
            {
                //return new JDKDSAPrivateKey((DSAPrivateKey)key);
            }
            else if (key instanceof ElGamalPublicKey)
            {
                //return new JCEElGamalPublicKey((ElGamalPublicKey)key);
            }
            else if (key instanceof ElGamalPrivateKey)
            {
                //return new JCEElGamalPrivateKey((ElGamalPrivateKey)key);
            }

            throw new InvalidKeyException("key type unknown");
        }


	   static PublicKey createPublicKeyFromDERStream(
		        InputStream         in)
		        throws IOException
		    {
		        return createPublicKeyFromPublicKeyInfo(
		                new SubjectPublicKeyInfo((ASN1Sequence)(new DERInputStream(in).readObject())));
		    }

    static PublicKey createPublicKeyFromPublicKeyInfo(
        SubjectPublicKeyInfo         info)
    {
        AlgorithmIdentifier     algId = info.getAlgorithmId();

        if (algId.getObjectId().equals(PKCSObjectIdentifiers.rsaEncryption)
        	|| algId.getObjectId().equals(X509ObjectIdentifiers.id_ea_rsa))
        {
              return new JCERSAPublicKey(info);
        }
        else if (algId.getObjectId().equals(X9ObjectIdentifiers.id_ecPublicKey))
        {
              return new JCEECPublicKey(info);
        }
        else
        {
            throw new RuntimeException("algorithm identifier in key not recognised");
        }
    }

    static PrivateKey createPrivateKeyFromDERStream(
            InputStream         in)
            throws IOException
        {
            return createPrivateKeyFromPrivateKeyInfo(
                    new PrivateKeyInfo((ASN1Sequence)(new DERInputStream(in).readObject())));
        }

        /**
         * create a private key from the given public key info object.
         */
        static PrivateKey createPrivateKeyFromPrivateKeyInfo(
            PrivateKeyInfo      info)
        {
            AlgorithmIdentifier     algId = info.getAlgorithmId();

            /*
            if (algId.getObjectId().equals(PKCSObjectIdentifiers.rsaEncryption))
            {
                  return new JCERSAPrivateCrtKey(info);
            }
            else if (algId.getObjectId().equals(PKCSObjectIdentifiers.dhKeyAgreement))
            {
                  return new JCEDHPrivateKey(info);
            }
            else if (algId.getObjectId().equals(OIWObjectIdentifiers.elGamalAlgorithm))
            {
                  return new JCEElGamalPrivateKey(info);
            }
            else if (algId.getObjectId().equals(X9ObjectIdentifiers.id_dsa))
            {
                  return new JDKDSAPrivateKey(info);
            }
            else */
            if (algId.getObjectId().equals(X9ObjectIdentifiers.id_ecPublicKey))
            {
                  return new JCEECPrivateKey(info);
            }
            else
            {
                throw new RuntimeException("algorithm identifier in key not recognised");
            }
        }

    public static class EC
    extends JDKKeyFactory
	{
	    String  algorithm;

	    public EC()

	    	throws NoSuchAlgorithmException
	    {
	        this("EC");

	        	// PARG - bail if we're constructing an X509 cert for SSL as the BC SSL impl is old and doesn't have recent named curves
	        	// If we allow this to continue it borks constructing the EC public key and takes the whole SSL process down with
	        	// utimately a
	        	// Caused by: java.io.IOException: subject key, java.lang.NullPointerException
	        	// at sun.security.x509.X509Key.parse(X509Key.java:157)
	    		// at sun.security.x509.CertificateX509Key.<init>(CertificateX509Key.java:58)
	    		// at sun.security.x509.X509CertInfo.parse(X509CertInfo.java:688)
	    		// at sun.security.x509.X509CertInfo.<init>(X509CertInfo.java:152)

	        try{
	        	StackTraceElement[] elements = new Exception().getStackTrace();

	        	boolean	ssl 	= false;
	        	boolean	x509	= false;

	        	for ( StackTraceElement elt: elements ){

	        		String name = elt.getClassName() + "." + elt.getMethodName();

	        		if ( name.contains( "SSLSocketFactory" ) || name.contains( "KeyStore.load" )){

	        			ssl = true;
	        		}else if ( name.contains( "X509" )){


	        			x509 = true;
	        		}
	        	}

	        	if( ssl && x509 ){

	        		//Debug.out( "Hacking SSL EC" );

	        		throw( new NoSuchAlgorithmException());
	        	}
	        }catch( NoSuchAlgorithmException e ){

	        	throw( e );

	        }catch( Throwable e ){

	        	Debug.out( e );
	        }
	    }

	    public EC(
	        String  algorithm)
	    {
	        this.algorithm = algorithm;
	    }

	    @Override
	    protected PrivateKey engineGeneratePrivate(
	        KeySpec    keySpec)
	        throws InvalidKeySpecException
	    {
	        if (keySpec instanceof PKCS8EncodedKeySpec)
	        {
	            try
	            {
	                return JDKKeyFactory.createPrivateKeyFromDERStream(
	                            new ByteArrayInputStream(((PKCS8EncodedKeySpec)keySpec).getEncoded()));
	            }
	            catch (Exception e)
	            {
	                throw new InvalidKeySpecException(e.toString());
	            }
	        }
	        else if (keySpec instanceof ECPrivateKeySpec)
	        {
	            return new JCEECPrivateKey(algorithm, (ECPrivateKeySpec)keySpec);
	        }

	        throw new InvalidKeySpecException("Unknown KeySpec type.");
	    }

	    @Override
	    protected PublicKey engineGeneratePublic(
	        KeySpec    keySpec)
	        throws InvalidKeySpecException
	    {
	        if (keySpec instanceof X509EncodedKeySpec)
	        {
	            try
	            {
	                return JDKKeyFactory.createPublicKeyFromDERStream(
	                            new ByteArrayInputStream(((X509EncodedKeySpec)keySpec).getEncoded()));
	            }
	            catch (Exception e)
	            {
	                throw new InvalidKeySpecException(e.toString());
	            }
	        }
	        else if (keySpec instanceof ECPublicKeySpec)
	        {
	            return new JCEECPublicKey(algorithm, (ECPublicKeySpec)keySpec);
	        }

	        throw new InvalidKeySpecException("Unknown KeySpec type.");
	    }
	}

    public static class ECDSA
    extends EC
    {
	    public ECDSA()
	    {
	        super("ECDSA");
	    }
    }
}
