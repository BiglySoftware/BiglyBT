package org.gudy.bouncycastle.asn1.pkcs;

import java.math.BigInteger;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.asn1.x509.DigestInfo;

public class MacData
    implements DEREncodable
{
    DigestInfo                  digInfo;
    byte[]                      salt;
    BigInteger                  iterationCount;

    public static MacData getInstance(
        Object  obj)
    {
        if (obj instanceof MacData)
        {
            return (MacData)obj;
        }
        else if (obj instanceof ASN1Sequence)
        {
            return new MacData((ASN1Sequence)obj);
        }

        throw new IllegalArgumentException("unknown object in factory");
    }

    public MacData(
        ASN1Sequence seq)
    {
        this.digInfo = DigestInfo.getInstance(seq.getObjectAt(0));

        this.salt = ((ASN1OctetString)seq.getObjectAt(1)).getOctets();

        if (seq.size() == 3)
        {
            this.iterationCount = ((DERInteger)seq.getObjectAt(2)).getValue();
        }
        else
        {
            this.iterationCount = BigInteger.valueOf(1);
        }
    }

    public MacData(
        DigestInfo  digInfo,
        byte[]      salt,
        int         iterationCount)
    {
        this.digInfo = digInfo;
        this.salt = salt;
        this.iterationCount = BigInteger.valueOf(iterationCount);
    }

    public DigestInfo getMac()
    {
        return digInfo;
    }

    public byte[] getSalt()
    {
        return salt;
    }

    public BigInteger getIterationCount()
    {
        return iterationCount;
    }

    @Override
    public DERObject getDERObject()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(digInfo);
        v.add(new DEROctetString(salt));
        v.add(new DERInteger(iterationCount));

        return new DERSequence(v);
    }
}
