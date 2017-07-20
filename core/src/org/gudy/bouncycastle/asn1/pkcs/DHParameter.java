package org.gudy.bouncycastle.asn1.pkcs;

import java.math.BigInteger;
import java.util.Enumeration;

import org.gudy.bouncycastle.asn1.*;

public class DHParameter
    implements DEREncodable
{
    DERInteger      p, g, l;

    public DHParameter(
        BigInteger  p,
        BigInteger  g,
        int         l)
    {
        this.p = new DERInteger(p);
        this.g = new DERInteger(g);

        if (l != 0)
        {
            this.l = new DERInteger(l);
        }
        else
        {
            this.l = null;
        }
    }

    public DHParameter(
        ASN1Sequence  seq)
    {
        Enumeration     e = seq.getObjects();

        p = (DERInteger)e.nextElement();
        g = (DERInteger)e.nextElement();

        if (e.hasMoreElements())
        {
            l = (DERInteger)e.nextElement();
        }
        else
        {
            l = null;
        }
    }

    public BigInteger getP()
    {
        return p.getPositiveValue();
    }

    public BigInteger getG()
    {
        return g.getPositiveValue();
    }

    public BigInteger getL()
    {
        if (l == null)
        {
            return null;
        }

        return l.getPositiveValue();
    }

    @Override
    public DERObject getDERObject()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(p);
        v.add(g);

        if (this.getL() != null)
        {
            v.add(l);
        }

        return new DERSequence(v);
    }
}
