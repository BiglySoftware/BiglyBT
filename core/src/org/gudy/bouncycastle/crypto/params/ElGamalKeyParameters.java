package org.gudy.bouncycastle.crypto.params;

public class ElGamalKeyParameters
    extends AsymmetricKeyParameter
{
    private ElGamalParameters    params;

    protected ElGamalKeyParameters(
        boolean         isPrivate,
        ElGamalParameters    params)
    {
        super(isPrivate);

        this.params = params;
    }

    public ElGamalParameters getParameters()
    {
        return params;
    }

    public boolean equals(
        Object  obj)
    {
        if (!(obj instanceof ElGamalKeyParameters))
        {
            return false;
        }

        ElGamalKeyParameters    dhKey = (ElGamalKeyParameters)obj;

        return (params != null && !params.equals(dhKey.getParameters()));
    }
}
