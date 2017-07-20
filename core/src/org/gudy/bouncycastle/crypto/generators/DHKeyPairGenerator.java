package org.gudy.bouncycastle.crypto.generators;

import java.math.BigInteger;

import org.gudy.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.gudy.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator;
import org.gudy.bouncycastle.crypto.KeyGenerationParameters;
import org.gudy.bouncycastle.crypto.params.DHKeyGenerationParameters;
import org.gudy.bouncycastle.crypto.params.DHParameters;
import org.gudy.bouncycastle.crypto.params.DHPrivateKeyParameters;
import org.gudy.bouncycastle.crypto.params.DHPublicKeyParameters;

/**
 * a Diffie-Helman key pair generator.
 *
 * This generates keys consistent for use in the MTI/A0 key agreement protocol
 * as described in "Handbook of Applied Cryptography", Pages 516-519.
 */
public class DHKeyPairGenerator
    implements AsymmetricCipherKeyPairGenerator
{
    private DHKeyGenerationParameters param;

    @Override
    public void init(
        KeyGenerationParameters param)
    {
        this.param = (DHKeyGenerationParameters)param;
    }

    @Override
    public AsymmetricCipherKeyPair generateKeyPair()
    {
        BigInteger      p, g, x, y;
        int             qLength = param.getStrength() - 1;
        DHParameters    dhParams = param.getParameters();

        p = dhParams.getP();
        g = dhParams.getG();

        //
        // calculate the private key
        //
		x = new BigInteger(qLength, param.getRandom());

        //
        // calculate the public key.
        //
        y = g.modPow(x, p);

        return new AsymmetricCipherKeyPair(
                new DHPublicKeyParameters(y, dhParams),
                new DHPrivateKeyParameters(x, dhParams));
    }
}
