package org.gudy.bouncycastle.asn1.x9;

import java.math.BigInteger;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.math.ec.ECCurve;

/**
 * ASN.1 def for Elliptic-Curve Curve structure. See
 * X9.62, for further details.
 */
public class X9Curve
    implements DEREncodable, X9ObjectIdentifiers
{
    private ECCurve     curve;
    private byte[]      seed;

	public X9Curve(
		ECCurve     curve)
	{
        this.curve = curve;
        this.seed = null;
	}

	public X9Curve(
		ECCurve     curve,
        byte[]      seed)
	{
        this.curve = curve;
        this.seed = seed;
	}

    public X9Curve(
        X9FieldID     fieldID,
        ASN1Sequence  seq)
    {
        if (fieldID.getIdentifier().equals(prime_field))
        {
            BigInteger      q = ((DERInteger)fieldID.getParameters()).getValue();
            X9FieldElement  x9A = new X9FieldElement(true, q, (ASN1OctetString)seq.getObjectAt(0));
            X9FieldElement  x9B = new X9FieldElement(true, q, (ASN1OctetString)seq.getObjectAt(1));
            curve = new ECCurve.Fp(q, x9A.getValue().toBigInteger(), x9B.getValue().toBigInteger());
        }
        else
        {
            throw new RuntimeException("not implemented");
        }

        if (seq.size() == 3)
        {
            seed = ((DERBitString)seq.getObjectAt(2)).getBytes();
        }
    }

    public ECCurve  getCurve()
    {
        return curve;
    }

    public byte[]   getSeed()
    {
        return seed;
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     * <pre>
     *  Curve ::= SEQUENCE {
     *      a               FieldElement,
     *      b               FieldElement,
     *      seed            BIT STRING      OPTIONAL
     *  }
     * </pre>
     */
    @Override
    public DERObject getDERObject()
    {
        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(new X9FieldElement(curve.getA()).getDERObject());
        v.add(new X9FieldElement(curve.getB()).getDERObject());

        if (seed != null)
        {
            v.add(new DERBitString(seed));
        }

        return new DERSequence(v);
    }
}
