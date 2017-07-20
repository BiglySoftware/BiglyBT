package org.gudy.bouncycastle.crypto.params;

public class DSAValidationParameters
{
    private byte[]  seed;
    private int     counter;

    public DSAValidationParameters(
        byte[]  seed,
        int     counter)
    {
        this.seed = seed;
        this.counter = counter;
    }

    public int getCounter()
    {
        return counter;
    }

    public byte[] getSeed()
    {
        return seed;
    }

    public boolean equals(
        Object o)
    {
        if (o == null || !(o instanceof DSAValidationParameters))
        {
            return false;
        }

        DSAValidationParameters  other = (DSAValidationParameters)o;

        if (other.counter != this.counter)
        {
            return false;
        }

        if (other.seed.length != this.seed.length)
        {
            return false;
        }

        for (int i = 0; i != other.seed.length; i++)
        {
            if (other.seed[i] != this.seed[i])
            {
                return false;
            }
        }

        return true;
    }
}
