package org.gudy.bouncycastle.asn1.x509;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.crypto.Digest;
import org.gudy.bouncycastle.crypto.digests.SHA1Digest;

/**
 * The SubjectKeyIdentifier object.
 * <pre>
 * SubjectKeyIdentifier::= OCTET STRING
 * </pre>
 */
public class SubjectKeyIdentifier
    extends ASN1Encodable
{
    private byte[] keyidentifier;

    public static SubjectKeyIdentifier getInstance(
        ASN1TaggedObject obj,
        boolean          explicit)
    {
        return getInstance(ASN1OctetString.getInstance(obj, explicit));
    }

    public static SubjectKeyIdentifier getInstance(
        Object obj)
    {
        if (obj instanceof SubjectKeyIdentifier)
        {
            return (SubjectKeyIdentifier)obj;
        }

        if (obj instanceof SubjectPublicKeyInfo)
        {
            return new SubjectKeyIdentifier((SubjectPublicKeyInfo)obj);
        }

        if (obj instanceof ASN1OctetString)
        {
            return new SubjectKeyIdentifier((ASN1OctetString)obj);
        }

        if (obj instanceof X509Extension)
        {
            return getInstance(X509Extension.convertValueToObject((X509Extension)obj));
        }

        throw new IllegalArgumentException("Invalid SubjectKeyIdentifier: " + obj.getClass().getName());
    }

    public SubjectKeyIdentifier(
        byte[] keyid)
    {
        this.keyidentifier=keyid;
    }

    public SubjectKeyIdentifier(
        ASN1OctetString  keyid)
    {
        this.keyidentifier=keyid.getOctets();

    }

    /**
     *
     * Calculates the keyidentifier using a SHA1 hash over the BIT STRING
     * from SubjectPublicKeyInfo as defined in RFC2459.
     *
     **/
    public SubjectKeyIdentifier(
        SubjectPublicKeyInfo    spki)
    {
        Digest  digest = new SHA1Digest();
        byte[]  resBuf = new byte[digest.getDigestSize()];

        byte[] bytes = spki.getPublicKeyData().getBytes();
        digest.update(bytes, 0, bytes.length);
        digest.doFinal(resBuf, 0);
        this.keyidentifier=resBuf;
    }

    public byte[] getKeyIdentifier()
    {
        return keyidentifier;
    }

    @Override
    public DERObject toASN1Object()
    {
        return new DEROctetString(keyidentifier);
    }
}
