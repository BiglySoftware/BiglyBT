package org.gudy.bouncycastle.asn1.x509;

import org.gudy.bouncycastle.asn1.*;

public class GeneralNames
    extends ASN1Encodable
{
    ASN1Sequence            seq;

    public static GeneralNames getInstance(
        Object  obj)
    {
        if (obj == null || obj instanceof GeneralNames)
        {
            return (GeneralNames)obj;
        }

        if (obj instanceof ASN1Sequence)
        {
            return new GeneralNames((ASN1Sequence)obj);
        }

        throw new IllegalArgumentException("illegal object in getInstance: " + obj.getClass().getName());
    }

    public static GeneralNames getInstance(
        ASN1TaggedObject obj,
        boolean          explicit)
    {
        return getInstance(ASN1Sequence.getInstance(obj, explicit));
    }

    /**
     * Construct a GeneralNames object containing one GeneralName.
     *
     * @param name the name to be contained.
     */
    public GeneralNames(
        GeneralName  name)
    {
        this.seq = new DERSequence(name);
    }

    public GeneralNames(
        ASN1Sequence  seq)
    {
        this.seq = seq;
    }

    public GeneralName[] getNames()
    {
        GeneralName[]   names = new GeneralName[seq.size()];

        for (int i = 0; i != seq.size(); i++)
        {
            names[i] = GeneralName.getInstance(seq.getObjectAt(i));
        }

        return names;
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     * <pre>
     * GeneralNames ::= SEQUENCE SIZE {1..MAX} OF GeneralName
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
        String        sep = System.getProperty("line.separator");
        GeneralName[] names = getNames();

        buf.append("GeneralNames:");
        buf.append(sep);

        for (int i = 0; i != names.length; i++)
        {
            buf.append("    ");
            buf.append(names[i]);
            buf.append(sep);
        }
        return buf.toString();
    }
}
