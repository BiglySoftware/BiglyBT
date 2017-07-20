package org.gudy.bouncycastle.asn1.sec;

import java.math.BigInteger;
import java.util.Enumeration;

import org.gudy.bouncycastle.asn1.*;

/**
 * the elliptic curve private key object from SEC 1
 */
public class ECPrivateKeyStructure
    extends ASN1Encodable
{
    private ASN1Sequence  seq;

    public ECPrivateKeyStructure(
        ASN1Sequence  seq)
    {
        this.seq = seq;
    }

    public ECPrivateKeyStructure(
        BigInteger  key)
    {
        byte[]  bytes = key.toByteArray();

        if (bytes[0] == 0)
        {
            byte[]  tmp = new byte[bytes.length - 1];

            System.arraycopy(bytes, 1, tmp, 0, tmp.length);
            bytes = tmp;
        }

        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(new DERInteger(1));
        v.add(new DEROctetString(bytes));

        seq = new DERSequence(v);
    }

    public BigInteger getKey()
    {
        ASN1OctetString  octs = (ASN1OctetString)seq.getObjectAt(1);

        return new BigInteger(1, octs.getOctets());
    }

    public DERBitString getPublicKey()
    {
        return (DERBitString)getObjectInTag(1);
    }

    public ASN1Object getParameters()
    {
        return getObjectInTag(0);
    }

    private ASN1Object getObjectInTag(int tagNo)
    {
        Enumeration e = seq.getObjects();

        while (e.hasMoreElements())
        {
            DEREncodable obj = (DEREncodable)e.nextElement();

            if (obj instanceof ASN1TaggedObject)
            {
                ASN1TaggedObject tag = (ASN1TaggedObject)obj;
                if (tag.getTagNo() == tagNo)
                {
                    return (ASN1Object)((DEREncodable)tag.getObject()).getDERObject();
                }
            }
        }
        return null;
    }

    /**
     * ECPrivateKey ::= SEQUENCE {
     *     version INTEGER { ecPrivkeyVer1(1) } (ecPrivkeyVer1),
     *     privateKey OCTET STRING,
     *     parameters [0] Parameters OPTIONAL,
     *     publicKey [1] BIT STRING OPTIONAL }
     */
    @Override
    public DERObject toASN1Object()
    {
        return seq;
    }
}
