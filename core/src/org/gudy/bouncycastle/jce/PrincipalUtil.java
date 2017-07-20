package org.gudy.bouncycastle.jce;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;

import org.gudy.bouncycastle.asn1.ASN1InputStream;
import org.gudy.bouncycastle.asn1.ASN1Sequence;
import org.gudy.bouncycastle.asn1.x509.TBSCertList;
import org.gudy.bouncycastle.asn1.x509.TBSCertificateStructure;

/**
 * a utility class that will extract X509Principal objects from X.509 certificates.
 */
public class PrincipalUtil
{
    /**
     * return the issuer of the given cert as an X509PrincipalObject.
     */
    public static X509Principal getIssuerX509Principal(
        X509Certificate cert)
        throws CertificateEncodingException
    {
        try
        {
            ByteArrayInputStream    bIn = new ByteArrayInputStream(
                cert.getTBSCertificate());
            ASN1InputStream         aIn = new ASN1InputStream(bIn);
            TBSCertificateStructure tbsCert = new TBSCertificateStructure(
                                            (ASN1Sequence)aIn.readObject());

            return new X509Principal(tbsCert.getIssuer());
        }
        catch (IOException e)
        {
            throw new CertificateEncodingException(e.toString());
        }
    }

    /**
     * return the subject of the given cert as an X509PrincipalObject.
     */
    public static X509Principal getSubjectX509Principal(
        X509Certificate cert)
        throws CertificateEncodingException
    {
        try
        {
            ByteArrayInputStream    bIn = new ByteArrayInputStream(
                cert.getTBSCertificate());
            ASN1InputStream         aIn = new ASN1InputStream(bIn);
            TBSCertificateStructure tbsCert = new TBSCertificateStructure(
                                            (ASN1Sequence)aIn.readObject());

            return new X509Principal(tbsCert.getSubject());
        }
        catch (IOException e)
        {
            throw new CertificateEncodingException(e.toString());
        }
    }

	/**
	 * return the issuer of the given CRL as an X509PrincipalObject.
	 */
	public static X509Principal getIssuerX509Principal(
		X509CRL crl)
		throws CRLException
	{
		try
		{
			ByteArrayInputStream    bIn = new ByteArrayInputStream(
				crl.getTBSCertList());
			ASN1InputStream         aIn = new ASN1InputStream(bIn);
			TBSCertList tbsCertList = new TBSCertList(
											(ASN1Sequence)aIn.readObject());

			return new X509Principal(tbsCertList.getIssuer());
		}
		catch (IOException e)
		{
			throw new CRLException(e.toString());
		}
	}
}
