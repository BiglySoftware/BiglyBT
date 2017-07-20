package org.gudy.bouncycastle.x509;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.text.ParseException;
import java.util.*;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.asn1.x509.AttributeCertificate;
import org.gudy.bouncycastle.asn1.x509.X509Extension;
import org.gudy.bouncycastle.asn1.x509.X509Extensions;
import org.gudy.bouncycastle.util.Arrays;

/**
 * An implementation of a version 2 X.509 Attribute Certificate.
 */
public class X509V2AttributeCertificate
    implements X509AttributeCertificate
{
    private AttributeCertificate    cert;
    private Date                    notBefore;
    private Date                    notAfter;

    public X509V2AttributeCertificate(
        InputStream encIn)
        throws IOException
    {
        this(AttributeCertificate.getInstance(new ASN1InputStream(encIn).readObject()));
    }

    public X509V2AttributeCertificate(
        byte[]  encoded)
        throws IOException
    {
        this(new ByteArrayInputStream(encoded));
    }

    X509V2AttributeCertificate(
        AttributeCertificate    cert)
        throws IOException
    {
        this.cert = cert;

        try
        {
            this.notAfter = cert.getAcinfo().getAttrCertValidityPeriod().getNotAfterTime().getDate();
            this.notBefore = cert.getAcinfo().getAttrCertValidityPeriod().getNotBeforeTime().getDate();
        }
        catch (ParseException e)
        {
            throw new IOException("invalid data structure in certificate!");
        }
    }

    @Override
    public int getVersion()
    {
        return cert.getAcinfo().getVersion().getValue().intValue();
    }

    @Override
    public BigInteger getSerialNumber()
    {
        return cert.getAcinfo().getSerialNumber().getValue();
    }

    @Override
    public AttributeCertificateHolder getHolder()
    {
        return new AttributeCertificateHolder((ASN1Sequence)cert.getAcinfo().getHolder().toASN1Object());
    }

    @Override
    public AttributeCertificateIssuer getIssuer()
    {
        return new AttributeCertificateIssuer(cert.getAcinfo().getIssuer());
    }

    @Override
    public Date getNotBefore()
    {
        return notBefore;
    }

    @Override
    public Date getNotAfter()
    {
        return notAfter;
    }

    @Override
    public boolean[] getIssuerUniqueID()
    {
        DERBitString    id = cert.getAcinfo().getIssuerUniqueID();

        if (id != null)
        {
            byte[]          bytes = id.getBytes();
            boolean[]       boolId = new boolean[bytes.length * 8 - id.getPadBits()];

            for (int i = 0; i != boolId.length; i++)
            {
                boolId[i] = (bytes[i / 8] & (0x80 >>> (i % 8))) != 0;
            }

            return boolId;
        }

        return null;
    }

    @Override
    public void checkValidity()
        throws CertificateExpiredException, CertificateNotYetValidException
    {
        this.checkValidity(new Date());
    }

    @Override
    public void checkValidity(
        Date    date)
        throws CertificateExpiredException, CertificateNotYetValidException
    {
        if (date.after(this.getNotAfter()))
        {
            throw new CertificateExpiredException("certificate expired on " + this.getNotAfter());
        }

        if (date.before(this.getNotBefore()))
        {
            throw new CertificateNotYetValidException("certificate not valid till " + this.getNotBefore());
        }
    }

    @Override
    public byte[] getSignature()
    {
        return cert.getSignatureValue().getBytes();
    }

    @Override
    public final void verify(
            PublicKey   key,
            String      provider)
            throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException
    {
        Signature   signature = null;

        if (!cert.getSignatureAlgorithm().equals(cert.getAcinfo().getSignature()))
        {
            throw new CertificateException("Signature algorithm in certificate info not same as outer certificate");
        }

        signature = Signature.getInstance(cert.getSignatureAlgorithm().getObjectId().getId(), provider);

        signature.initVerify(key);

        try
        {
            signature.update(cert.getAcinfo().getEncoded());
        }
        catch (IOException e)
        {
            throw new SignatureException("Exception encoding certificate info object");
        }

        if (!signature.verify(this.getSignature()))
        {
            throw new InvalidKeyException("Public key presented not for certificate signature");
        }
    }

    @Override
    public byte[] getEncoded()
        throws IOException
    {
        return cert.getEncoded();
    }

    @Override
    public byte[] getExtensionValue(String oid)
    {
        X509Extensions  extensions = cert.getAcinfo().getExtensions();

        if (extensions != null)
        {
            X509Extension   ext = extensions.getExtension(new DERObjectIdentifier(oid));

            if (ext != null)
            {
                ByteArrayOutputStream    bOut = new ByteArrayOutputStream();
                DEROutputStream            dOut = new DEROutputStream(bOut);

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

    private Set getExtensionOIDs(
        boolean critical)
    {
        X509Extensions  extensions = cert.getAcinfo().getExtensions();

        if (extensions != null)
        {
            Set             set = new HashSet();
            Enumeration     e = extensions.oids();

            while (e.hasMoreElements())
            {
                DERObjectIdentifier oid = (DERObjectIdentifier)e.nextElement();
                X509Extension       ext = extensions.getExtension(oid);

                if (ext.isCritical() == critical)
                {
                    set.add(oid.getId());
                }
            }

            return set;
        }

        return null;
    }

    @Override
    public Set getNonCriticalExtensionOIDs()
    {
        return getExtensionOIDs(false);
    }

    @Override
    public Set getCriticalExtensionOIDs()
    {
        return getExtensionOIDs(true);
    }

    @Override
    public boolean hasUnsupportedCriticalExtension()
    {
        Set  extensions = getCriticalExtensionOIDs();

        return extensions != null && !extensions.isEmpty();
    }

    @Override
    public X509Attribute[] getAttributes()
    {
        ASN1Sequence    seq = cert.getAcinfo().getAttributes();
        X509Attribute[] attrs = new X509Attribute[seq.size()];

        for (int i = 0; i != seq.size(); i++)
        {
            attrs[i] = new X509Attribute((ASN1Encodable)seq.getObjectAt(i));
        }

        return attrs;
    }

    @Override
    public X509Attribute[] getAttributes(String oid)
    {
        ASN1Sequence    seq = cert.getAcinfo().getAttributes();
        List            list = new ArrayList();

        for (int i = 0; i != seq.size(); i++)
        {
            X509Attribute attr = new X509Attribute((ASN1Encodable)seq.getObjectAt(i));
            if (attr.getOID().equals(oid))
            {
                list.add(attr);
            }
        }

        if (list.size() == 0)
        {
            return null;
        }

        return (X509Attribute[])list.toArray(new X509Attribute[list.size()]);
    }

    public boolean equals(
        Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof X509AttributeCertificate))
        {
            return false;
        }

        X509AttributeCertificate other = (X509AttributeCertificate)o;

        try
        {
            byte[] b1 = this.getEncoded();
            byte[] b2 = other.getEncoded();

            return Arrays.areEqual(b1, b2);
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public int hashCode()
    {
        try
        {
            byte[]  b = this.getEncoded();
            int     value = 0;

            for (int i = 0; i != b.length; i++)
            {
                value ^= (b[i] & 0xff) << (i % 4);
            }

            return value;
        }
        catch (IOException e)
        {
            return 0;
        }
    }
}
