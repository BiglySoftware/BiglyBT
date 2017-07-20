package org.gudy.bouncycastle.asn1.pkcs;

import java.math.BigInteger;

import org.gudy.bouncycastle.asn1.*;

public class RC2CBCParameter
    implements DEREncodable
{
    DERInteger      version;
    ASN1OctetString iv;

    public static RC2CBCParameter getInstance(
        Object  o)
    {
        if (o instanceof ASN1Sequence)
        {
            return new RC2CBCParameter((ASN1Sequence)o);
        }

        throw new IllegalArgumentException("unknown object in RC2CBCParameter factory");
    }

    public RC2CBCParameter(
        byte[]  iv)
    {
        this.version = null;
        this.iv = new DEROctetString(iv);
    }

    public RC2CBCParameter(
        int     parameterVersion,
        byte[]  iv)
    {
        this.version = new DERInteger(parameterVersion);
        this.iv = new DEROctetString(iv);
    }

    public RC2CBCParameter(
        ASN1Sequence  seq)
    {
        if (seq.size() == 1)
        {
            version = null;
            iv = (ASN1OctetString)seq.getObjectAt(0);
        }
        else
        {
            version = (DERInteger)seq.getObjectAt(0);
            iv = (ASN1OctetString)seq.getObjectAt(1);
        }
    }

    public BigInteger getRC2ParameterVersion()
    {
        if (version == null)
        {
            return null;
        }

        return version.getValue();
    }

    public byte[] getIV()
    {
        return iv.getOctets();
    }

    @Override
    public DERObject getDERObject()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        if (version != null)
        {
            v.add(version);
        }

        v.add(iv);

        return new DERSequence(v);
    }
}
