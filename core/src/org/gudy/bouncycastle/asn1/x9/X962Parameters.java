package org.gudy.bouncycastle.asn1.x9;

import org.gudy.bouncycastle.asn1.DEREncodable;
import org.gudy.bouncycastle.asn1.DERObject;
import org.gudy.bouncycastle.asn1.DERObjectIdentifier;

public class X962Parameters
    implements DEREncodable
{
    private DERObject           params = null;

    public X962Parameters(
        X9ECParameters      ecParameters)
    {
        this.params = ecParameters.getDERObject();
    }

    public X962Parameters(
        DERObjectIdentifier  namedCurve)
    {
        this.params = namedCurve;
    }

    public X962Parameters(
        DERObject           obj)
    {
        this.params = obj;
    }

    public boolean isNamedCurve()
    {
        return (params instanceof DERObjectIdentifier);
    }

    public DERObject getParameters()
    {
        return params;
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     * <pre>
     * Parameters ::= CHOICE {
     *    ecParameters ECParameters,
     *    namedCurve   CURVES.&id({CurveNames}),
     *    implicitlyCA NULL
     * }
     * </pre>
     */
    @Override
    public DERObject getDERObject()
    {
        return params;
    }
}
