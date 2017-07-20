package org.gudy.bouncycastle.crypto.params;

import java.security.SecureRandom;

import org.gudy.bouncycastle.crypto.CipherParameters;

public class ParametersWithRandom
    implements CipherParameters
{
    private SecureRandom        random;
    private CipherParameters    parameters;

    public ParametersWithRandom(
        CipherParameters    parameters,
        SecureRandom        random)
    {
        this.random = random;
        this.parameters = parameters;
    }

    public ParametersWithRandom(
        CipherParameters    parameters)
    {
        this.random = null;
        this.parameters = parameters;
    }

    public SecureRandom getRandom()
    {
        if (random == null)
        {
            random = new SecureRandom();
        }
        return random;
    }

    public CipherParameters getParameters()
    {
        return parameters;
    }
}
