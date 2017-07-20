package org.gudy.bouncycastle.jce.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

import org.gudy.bouncycastle.asn1.ASN1Sequence;
import org.gudy.bouncycastle.asn1.DERNull;
import org.gudy.bouncycastle.asn1.DEROutputStream;
import org.gudy.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.gudy.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.gudy.bouncycastle.asn1.x509.RSAPublicKeyStructure;
import org.gudy.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.gudy.bouncycastle.crypto.params.RSAKeyParameters;

public class JCERSAPublicKey
    implements RSAPublicKey
{
    private BigInteger modulus;
    private BigInteger publicExponent;

    JCERSAPublicKey(
        RSAKeyParameters key)
    {
        this.modulus = key.getModulus();
        this.publicExponent = key.getExponent();
    }

    JCERSAPublicKey(
        RSAPublicKeySpec spec)
    {
        this.modulus = spec.getModulus();
        this.publicExponent = spec.getPublicExponent();
    }

    JCERSAPublicKey(
        RSAPublicKey key)
    {
        this.modulus = key.getModulus();
        this.publicExponent = key.getPublicExponent();
    }

    JCERSAPublicKey(
        SubjectPublicKeyInfo    info)
    {
        try
        {
            RSAPublicKeyStructure   pubKey = new RSAPublicKeyStructure((ASN1Sequence)info.getPublicKey());

            this.modulus = pubKey.getModulus();
            this.publicExponent = pubKey.getPublicExponent();
		}
        catch (IOException e)
        {
			throw new IllegalArgumentException("invalid info structure in RSA public key");
        }
    }

    /**
     * return the modulus.
     *
     * @return the modulus.
     */
    @Override
    public BigInteger getModulus()
    {
        return modulus;
    }

    /**
     * return the public exponent.
     *
     * @return the public exponent.
     */
    @Override
    public BigInteger getPublicExponent()
    {
        return publicExponent;
    }

    @Override
    public String getAlgorithm()
    {
        return "RSA";
    }

    @Override
    public String getFormat()
    {
        return "X.509";
    }

    @Override
    public byte[] getEncoded()
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        DEROutputStream         dOut = new DEROutputStream(bOut);
        SubjectPublicKeyInfo    info = new SubjectPublicKeyInfo(new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, new DERNull()), new RSAPublicKeyStructure(getModulus(), getPublicExponent()).getDERObject());

        try
        {
            dOut.writeObject(info);
            dOut.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error encoding RSA public key");
        }

        return bOut.toByteArray();

    }

	public boolean equals(Object o)
	{
		if ( !(o instanceof RSAPublicKey) )
		{
			return false;
		}

		if ( o == this )
		{
			return true;
		}

		RSAPublicKey key = (RSAPublicKey)o;

		return getModulus().equals(key.getModulus())
			&& getPublicExponent().equals(key.getPublicExponent());
	}

    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        String          nl = System.getProperty("line.separator");

        buf.append("RSA Public Key").append(nl);
        buf.append("            modulus: ").append(this.getModulus().toString(16)).append(nl);
        buf.append("    public exponent: ").append(this.getPublicExponent().toString(16)).append(nl);

        return buf.toString();
    }
}
