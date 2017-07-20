package org.gudy.bouncycastle.crypto.generators;

import java.math.BigInteger;

import org.gudy.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.gudy.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator;
import org.gudy.bouncycastle.crypto.KeyGenerationParameters;
import org.gudy.bouncycastle.crypto.params.ElGamalKeyGenerationParameters;
import org.gudy.bouncycastle.crypto.params.ElGamalParameters;
import org.gudy.bouncycastle.crypto.params.ElGamalPrivateKeyParameters;
import org.gudy.bouncycastle.crypto.params.ElGamalPublicKeyParameters;

/**
 * a ElGamal key pair generator.
 * <p>
 * This generates keys consistent for use with ElGamal as described in
 * page 164 of "Handbook of Applied Cryptography".
 */
public class ElGamalKeyPairGenerator
    implements AsymmetricCipherKeyPairGenerator
{
    private ElGamalKeyGenerationParameters param;

    @Override
    public void init(
        KeyGenerationParameters param)
    {
        this.param = (ElGamalKeyGenerationParameters)param;
    }

    @Override
    public AsymmetricCipherKeyPair generateKeyPair()
    {
        BigInteger           p, g, x, y;
        int                  qLength = param.getStrength() - 1;
        ElGamalParameters    elParams = param.getParameters();

        p = elParams.getP();
        g = elParams.getG();

        //
        // calculate the private key
        //
		x = new BigInteger(qLength, param.getRandom());

        //
        // calculate the public key.
        //
        y = g.modPow(x, p);

        return new AsymmetricCipherKeyPair(
                new ElGamalPublicKeyParameters(y, elParams),
                new ElGamalPrivateKeyParameters(x, elParams));
    }
}
