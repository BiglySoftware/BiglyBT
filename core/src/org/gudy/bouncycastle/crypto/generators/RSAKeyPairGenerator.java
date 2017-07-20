package org.gudy.bouncycastle.crypto.generators;

import java.math.BigInteger;

import org.gudy.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.gudy.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator;
import org.gudy.bouncycastle.crypto.KeyGenerationParameters;
import org.gudy.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.gudy.bouncycastle.crypto.params.RSAKeyParameters;
import org.gudy.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;

/**
 * an RSA key pair generator.
 */
public class RSAKeyPairGenerator
    implements AsymmetricCipherKeyPairGenerator
{
    private static BigInteger ONE = BigInteger.valueOf(1);

    private RSAKeyGenerationParameters param;

    @Override
    public void init(
        KeyGenerationParameters param)
    {
        this.param = (RSAKeyGenerationParameters)param;
    }

    @Override
    public AsymmetricCipherKeyPair generateKeyPair()
    {
        BigInteger    p, q, n, d, e, pSub1, qSub1, phi;

        //
        // p and q values should have a length of half the strength in bits
        //
        int pbitlength = (param.getStrength() + 1) / 2;
		int qbitlength = (param.getStrength() - pbitlength);
		e = param.getPublicExponent();

        //
        // generate p, prime and (p-1) relatively prime to e
        //
		for (;;)
		{
			p = new BigInteger(pbitlength, 1, param.getRandom());

			if (p.mod(e).equals(ONE))
			{
				continue;
			}

			if (!p.isProbablePrime(param.getCertainty()))
			{
				continue;
			}

			if (e.gcd(p.subtract(ONE)).equals(ONE))
			{
				break;
			}
		}

        //
        // generate a modulus of the required length
		//
        for (;;)
        {
		    // generate q, prime and (q-1) relatively prime to e,
		    // and not equal to p
		    //
            for (;;)
            {
                q = new BigInteger(qbitlength, 1, param.getRandom());

                if (q.equals(p))
                {
                	continue;
                }

				if (q.mod(e).equals(ONE))
				{
					continue;
				}

				if (!q.isProbablePrime(param.getCertainty()))
				{
					continue;
				}

			    if (e.gcd(q.subtract(ONE)).equals(ONE))
			    {
			    	break;
			    }
            }

            //
            // calculate the modulus
            //
            n = p.multiply(q);

            if (n.bitLength() == param.getStrength())
            {
            	break;
            }

            //
            // if we get here our primes aren't big enough, make the largest
            // of the two p and try again
            //
            p = p.max(q);
        }

        if (p.compareTo(q) < 0)
        {
            phi = p;
            p = q;
            q = phi;
        }

        pSub1 = p.subtract(ONE);
        qSub1 = q.subtract(ONE);
        phi = pSub1.multiply(qSub1);

        //
        // calculate the private exponent
        //
        d = e.modInverse(phi);

        //
        // calculate the CRT factors
        //
        BigInteger    dP, dQ, qInv;

        dP = d.remainder(pSub1);
        dQ = d.remainder(qSub1);
        qInv = q.modInverse(p);

        return new AsymmetricCipherKeyPair(
                new RSAKeyParameters(false, n, e),
                new RSAPrivateCrtKeyParameters(n, e, d, p, q, dP, dQ, qInv));
    }
}
