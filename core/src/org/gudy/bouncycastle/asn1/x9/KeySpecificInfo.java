package org.gudy.bouncycastle.asn1.x9;

import java.util.Enumeration;

import org.gudy.bouncycastle.asn1.*;

/**
 * ASN.1 def for Diffie-Hellman key exchange KeySpecificInfo structure. See
 * RFC 2631, or X9.42, for further details.
 */
public class KeySpecificInfo
    implements DEREncodable
{
    private DERObjectIdentifier algorithm;
    private ASN1OctetString      counter;

    public KeySpecificInfo(
        DERObjectIdentifier algorithm,
        ASN1OctetString      counter)
    {
        this.algorithm = algorithm;
        this.counter = counter;
    }

    public KeySpecificInfo(
        ASN1Sequence  seq)
    {
        Enumeration e = seq.getObjects();

        algorithm = (DERObjectIdentifier)e.nextElement();
        counter = (ASN1OctetString)e.nextElement();
    }

    public DERObjectIdentifier getAlgorithm()
    {
        return algorithm;
    }

    public ASN1OctetString getCounter()
    {
        return counter;
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     * <pre>
     *  KeySpecificInfo ::= SEQUENCE {
     *      algorithm OBJECT IDENTIFIER,
     *      counter OCTET STRING SIZE (4..4)
     *  }
     * </pre>
     */
    @Override
    public DERObject getDERObject()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        v.add(algorithm);
        v.add(counter);

        return new DERSequence(v);
    }
}
