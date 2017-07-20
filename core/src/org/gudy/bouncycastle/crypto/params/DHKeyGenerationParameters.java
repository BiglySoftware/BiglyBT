package org.gudy.bouncycastle.crypto.params;

import java.security.SecureRandom;

import org.gudy.bouncycastle.crypto.KeyGenerationParameters;

public class DHKeyGenerationParameters
    extends KeyGenerationParameters
{
    private DHParameters    params;

    public DHKeyGenerationParameters(
        SecureRandom    random,
        DHParameters    params)
    {
        super(random, params.getP().bitLength() - 1);

        this.params = params;
    }

    public DHParameters getParameters()
    {
        return params;
    }
}
