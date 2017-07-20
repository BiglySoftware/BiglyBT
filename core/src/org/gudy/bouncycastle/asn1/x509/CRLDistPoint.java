package org.gudy.bouncycastle.asn1.x509;

import org.gudy.bouncycastle.asn1.*;

public class CRLDistPoint
    extends ASN1Encodable
{
    ASN1Sequence  seq = null;

    public static CRLDistPoint getInstance(
        ASN1TaggedObject obj,
        boolean          explicit)
    {
        return getInstance(ASN1Sequence.getInstance(obj, explicit));
    }

    public static CRLDistPoint getInstance(
        Object  obj)
    {
        if (obj instanceof CRLDistPoint || obj == null)
        {
            return (CRLDistPoint)obj;
        }
        else if (obj instanceof ASN1Sequence)
        {
            return new CRLDistPoint((ASN1Sequence)obj);
        }

        throw new IllegalArgumentException("unknown object in factory");
    }

    public CRLDistPoint(
        ASN1Sequence seq)
    {
        this.seq = seq;
    }

    public CRLDistPoint(
        DistributionPoint[] points)
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        for (int i = 0; i != points.length; i++)
        {
            v.add(points[i]);
        }

        seq = new DERSequence(v);
    }

    /**
     * Return the distribution points making up the sequence.
     *
     * @return DistributionPoint[]
     */
    public DistributionPoint[] getDistributionPoints()
    {
        DistributionPoint[]    dp = new DistributionPoint[seq.size()];

        for (int i = 0; i != seq.size(); i++)
        {
            dp[i] = DistributionPoint.getInstance(seq.getObjectAt(i));
        }

        return dp;
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     * <pre>
     * CRLDistPoint ::= SEQUENCE SIZE {1..MAX} OF DistributionPoint
     * </pre>
     */
    @Override
    public DERObject toASN1Object()
    {
        return seq;
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        String       sep = System.getProperty("line.separator");

        buf.append("CRLDistPoint:");
        buf.append(sep);
        DistributionPoint dp[] = getDistributionPoints();
        for (int i = 0; i != dp.length; i++)
        {
            buf.append("    ");
            buf.append(dp[i]);
            buf.append(sep);
        }
        return buf.toString();
    }
}
