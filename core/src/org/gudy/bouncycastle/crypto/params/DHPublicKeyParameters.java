package org.gudy.bouncycastle.crypto.params;

import java.math.BigInteger;

public class DHPublicKeyParameters
    extends DHKeyParameters
{
    private BigInteger      y;

    public DHPublicKeyParameters(
        BigInteger      y,
        DHParameters    params)
    {
        super(false, params);

        this.y = y;
    }

    public BigInteger getY()
    {
        return y;
    }

    public boolean equals(
        Object  obj)
    {
        if (!(obj instanceof DHPublicKeyParameters))
        {
            return false;
        }

        DHPublicKeyParameters   pKey = (DHPublicKeyParameters)obj;

        if (!pKey.getY().equals(y))
        {
            return false;
        }

        return super.equals(obj);
    }
}
