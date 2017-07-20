package org.gudy.bouncycastle.asn1.x509;

import java.math.BigInteger;

import org.gudy.bouncycastle.asn1.DERInteger;

/**
 * The CRLNumber object.
 * <pre>
 * CRLNumber::= INTEGER(0..MAX)
 * </pre>
 */
public class CRLNumber
    extends DERInteger
{

    public CRLNumber(
        BigInteger number)
    {
        super(number);
    }

    public BigInteger getCRLNumber()
    {
        return getPositiveValue();
    }

    public String toString()
    {
        return "CRLNumber: " + getCRLNumber();
    }
}
