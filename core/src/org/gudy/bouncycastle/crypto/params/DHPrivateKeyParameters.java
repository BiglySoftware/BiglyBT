package org.gudy.bouncycastle.crypto.params;

import java.math.BigInteger;

public class DHPrivateKeyParameters
    extends DHKeyParameters
{
    private BigInteger      x;

    public DHPrivateKeyParameters(
        BigInteger      x,
        DHParameters    params)
    {
        super(true, params);

        this.x = x;
    }

    public BigInteger getX()
    {
        return x;
    }

    public boolean equals(
        Object  obj)
    {
        if (!(obj instanceof DHPrivateKeyParameters))
        {
            return false;
        }

        DHPrivateKeyParameters  pKey = (DHPrivateKeyParameters)obj;

        if (!pKey.getX().equals(x))
        {
            return false;
        }

        return super.equals(obj);
    }
}
