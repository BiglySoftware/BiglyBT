package org.gudy.bouncycastle.jce.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.gudy.bouncycastle.asn1.ASN1OutputStream;
import org.gudy.bouncycastle.asn1.DERObjectIdentifier;
import org.gudy.bouncycastle.asn1.DEROutputStream;
import org.gudy.bouncycastle.asn1.x509.CertificateList;
import org.gudy.bouncycastle.asn1.x509.TBSCertList;
import org.gudy.bouncycastle.asn1.x509.X509Extension;
import org.gudy.bouncycastle.asn1.x509.X509Extensions;
import org.gudy.bouncycastle.jce.X509Principal;

/**
 * The following extensions are listed in RFC 2459 as relevant to CRLs
 *
 * Authority Key Identifier
 * Issuer Alternative Name
 * CRL Number
 * Delta CRL Indicator (critical)
 * Issuing Distribution Point (critical)
 */
public class X509CRLObject
    extends X509CRL
{
    private CertificateList c;

    public X509CRLObject(
        CertificateList c)
    {
        this.c = c;
    }

	/**
	 * Will return true if any extensions are present and marked
	 * as critical as we currently dont handle any extensions!
	 */
    @Override
    public boolean hasUnsupportedCriticalExtension()
	{
		Set extns = getCriticalExtensionOIDs();
		if ( extns != null && !extns.isEmpty() )
		{
			return true;
		}

		return false;
	}

	private Set getExtensionOIDs(boolean critical)
	{
		if (this.getVersion() == 2)
		{
			HashSet         set = new HashSet();
			X509Extensions  extensions = c.getTBSCertList().getExtensions();
			Enumeration     e = extensions.oids();

			while (e.hasMoreElements())
			{
				DERObjectIdentifier oid = (DERObjectIdentifier)e.nextElement();
				X509Extension       ext = extensions.getExtension(oid);

				if (critical == ext.isCritical())
				{
					set.add(oid.getId());
				}
			}

			return set;
		}

		return null;
	}

    @Override
    public Set getCriticalExtensionOIDs()
	{
		return getExtensionOIDs(true);
	}

    @Override
    public Set getNonCriticalExtensionOIDs()
	{
		return getExtensionOIDs(false);
	}

    @Override
    public byte[] getExtensionValue(String oid)
	{
		X509Extensions exts = c.getTBSCertList().getExtensions();

		if (exts != null)
		{
			X509Extension   ext = exts.getExtension(new DERObjectIdentifier(oid));

			if (ext != null)
			{
                ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
                DEROutputStream dOut = new DEROutputStream(bOut);

                try
                {
                    dOut.writeObject(ext.getValue());

                    return bOut.toByteArray();
                }
                catch (Exception e)
                {
                    throw new RuntimeException("error encoding " + e.toString());
                }
			}
		}

		return null;
	}

    @Override
    public byte[] getEncoded()
        throws CRLException
	{
		ByteArrayOutputStream	bOut = new ByteArrayOutputStream();
		DEROutputStream			dOut = new DEROutputStream(bOut);

		try
		{
			dOut.writeObject(c);

			return bOut.toByteArray();
		}
		catch (IOException e)
		{
			throw new CRLException(e.toString());
		}
	}

    @Override
    public void verify(PublicKey key)
        throws CRLException,  NoSuchAlgorithmException,
        InvalidKeyException, NoSuchProviderException,
        SignatureException
	{
		verify(key, BouncyCastleProvider.PROVIDER_NAME);
	}

    @Override
    public void verify(PublicKey key, String sigProvider)
        throws CRLException, NoSuchAlgorithmException,
        InvalidKeyException, NoSuchProviderException,
        SignatureException
	{
		if ( !c.getSignatureAlgorithm().equals(c.getTBSCertList().getSignature()) )
		{
			throw new CRLException("Signature algorithm on CertifcateList does not match TBSCertList.");
		}

		Signature sig = Signature.getInstance(getSigAlgName(), sigProvider);

		sig.initVerify(key);
		sig.update(this.getTBSCertList());
		if ( !sig.verify(this.getSignature()) )
		{
			throw new SignatureException("CRL does not verify with supplied public key.");
		}
	}

    @Override
    public int getVersion()
	{
		return c.getVersion();
	}

    @Override
    public Principal getIssuerDN()
	{
		return new X509Principal(c.getIssuer());
	}

    @Override
    public X500Principal getIssuerX500Principal()
    {
        try
        {
            ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
            ASN1OutputStream        aOut = new ASN1OutputStream(bOut);

            aOut.writeObject(c.getIssuer());

            return new X500Principal(bOut.toByteArray());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("can't encode issuer DN");
        }
    }

    @Override
    public Date getThisUpdate()
	{
		return c.getThisUpdate().getDate();
	}

    @Override
    public Date getNextUpdate()
	{
		if (c.getNextUpdate() != null)
		{
			return c.getNextUpdate().getDate();
		}

		return null;
	}

    @Override
    public X509CRLEntry getRevokedCertificate(BigInteger serialNumber)
	{
		TBSCertList.CRLEntry[] certs = c.getRevokedCertificates();

		if ( certs != null )
		{
			for ( int i = 0; i < certs.length; i++ )
			{
				if ( certs[i].getUserCertificate().getValue().equals(serialNumber) ) {
					return new X509CRLEntryObject(certs[i]);
				}
			}
		}

		return null;
	}

    @Override
    public Set getRevokedCertificates()
	{
		TBSCertList.CRLEntry[] certs = c.getRevokedCertificates();

		if ( certs != null )
		{
			HashSet set = new HashSet();
			for ( int i = 0; i < certs.length; i++ )
			{
				set.add(new X509CRLEntryObject(certs[i]));

			}

			return set;
		}

		return null;
	}

    @Override
    public byte[] getTBSCertList()
		throws CRLException
	{
		ByteArrayOutputStream	bOut = new ByteArrayOutputStream();
		DEROutputStream			dOut = new DEROutputStream(bOut);

		try
		{
			dOut.writeObject(c.getTBSCertList());

			return bOut.toByteArray();
		}
		catch (IOException e)
		{
			throw new CRLException(e.toString());
		}
	}

    @Override
    public byte[] getSignature()
	{
		return c.getSignature().getBytes();
	}

    @Override
    public String getSigAlgName()
	{
		Provider	prov = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
		String		algName = prov.getProperty("Alg.Alias.Signature." + this.getSigAlgOID());

		if ( algName != null )
		{
			return algName;
		}

		Provider[] provs = Security.getProviders();

		//
		// search every provider looking for a real algorithm
		//
		for (int i = 0; i != provs.length; i++)
		{
			algName = provs[i].getProperty("Alg.Alias.Signature." + this.getSigAlgOID());
			if ( algName != null )
			{
				return algName;
			}
		}

		return this.getSigAlgOID();
	}

    @Override
    public String getSigAlgOID()
	{
		return c.getSignatureAlgorithm().getObjectId().getId();
	}

    @Override
    public byte[] getSigAlgParams()
	{
		ByteArrayOutputStream	bOut = new ByteArrayOutputStream();

		if ( c.getSignatureAlgorithm().getParameters() != null )
		{
			try
			{
				DEROutputStream	dOut = new DEROutputStream(bOut);

				dOut.writeObject(c.getSignatureAlgorithm().getParameters());
			}
			catch (Exception e)
			{
				throw new RuntimeException("exception getting sig parameters " + e);
			}

			return bOut.toByteArray();
		}

		return null;
	}

    /**
     * Returns a string representation of this CRL.
     *
     * @return a string representation of this CRL.
     */
    public String toString()
	{
		return "X.509 CRL";
	}

    /**
     * Checks whether the given certificate is on this CRL.
     *
     * @param cert the certificate to check for.
     * @return true if the given certificate is on this CRL,
     * false otherwise.
     */
    @Override
    public boolean isRevoked(Certificate cert)
	{
		if ( !cert.getType().equals("X.509") )
		{
			throw new RuntimeException("X.509 CRL used with non X.509 Cert");
		}

		TBSCertList.CRLEntry[] certs = c.getRevokedCertificates();

		if ( certs != null )
		{
			BigInteger serial = ((X509Certificate)cert).getSerialNumber();

			for ( int i = 0; i < certs.length; i++ )
			{
				if ( certs[i].getUserCertificate().getValue().equals(serial) )
				{
					return true;
				}
			}
		}

		return false;
	}
}

