package org.gudy.bouncycastle.crypto.params;

import java.security.SecureRandom;

import org.gudy.bouncycastle.crypto.KeyGenerationParameters;

public class ElGamalKeyGenerationParameters
    extends KeyGenerationParameters
{
    private ElGamalParameters    params;

    public ElGamalKeyGenerationParameters(
        SecureRandom        random,
        ElGamalParameters   params)
    {
        super(random, params.getP().bitLength() - 1);

        this.params = params;
    }

    public ElGamalParameters getParameters()
    {
        return params;
    }
}
