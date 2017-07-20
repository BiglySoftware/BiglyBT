package org.gudy.bouncycastle.crypto.generators;

import java.math.BigInteger;
import java.security.SecureRandom;

import org.gudy.bouncycastle.crypto.digests.SHA1Digest;
import org.gudy.bouncycastle.crypto.params.DSAParameters;
import org.gudy.bouncycastle.crypto.params.DSAValidationParameters;

/**
 * generate suitable parameters for DSA, in line with FIPS 186-2.
 */
public class DSAParametersGenerator
{
    private int             size;
    private int             certainty;
    private SecureRandom    random;

    private static BigInteger ONE = BigInteger.valueOf(1);
    private static BigInteger TWO = BigInteger.valueOf(2);

    /**
     * initialise the key generator.
     *
     * @param size size of the key (range 2^512 -> 2^1024 - 64 bit increments)
     * @param certainty measure of robustness of prime (for FIPS 186-2 compliance this should be at least 80).
     * @param random random byte source.
     */
    public void init(
        int             size,
        int             certainty,
        SecureRandom    random)
    {
        this.size = size;
        this.certainty = certainty;
        this.random = random;
    }

    /**
     * add value to b, returning the result in a. The a value is treated
     * as a BigInteger of length (a.length * 8) bits. The result is
     * modulo 2^a.length in case of overflow.
     */
    private void add(
        byte[]  a,
        byte[]  b,
        int     value)
    {
        int     x = (b[b.length - 1] & 0xff) + value;

        a[b.length - 1] = (byte)x;
        x >>>= 8;

        for (int i = b.length - 2; i >= 0; i--)
        {
            x += (b[i] & 0xff);
            a[i] = (byte)x;
            x >>>= 8;
        }
    }

    /**
     * which generates the p and g values from the given parameters,
     * returning the DSAParameters object.
     * <p>
     * Note: can take a while...
     */
    public DSAParameters generateParameters()
    {
        byte[]          seed = new byte[20];
        byte[]          part1 = new byte[20];
        byte[]          part2 = new byte[20];
        byte[]          u = new byte[20];
        SHA1Digest      sha1 = new SHA1Digest();
        int             n = (size - 1) / 160;
        byte[]          w = new byte[size / 8];

        BigInteger      q = null, p = null, g = null;
        int             counter = 0;
        boolean         primesFound = false;

        while (!primesFound)
        {
            do
            {
                random.nextBytes(seed);

                sha1.update(seed, 0, seed.length);

                sha1.doFinal(part1, 0);

                System.arraycopy(seed, 0, part2, 0, seed.length);

                add(part2, seed, 1);

                sha1.update(part2, 0, part2.length);

                sha1.doFinal(part2, 0);

                for (int i = 0; i != u.length; i++)
                {
                    u[i] = (byte)(part1[i] ^ part2[i]);
                }

                u[0] |= (byte)0x80;
                u[19] |= (byte)0x01;

                q = new BigInteger(1, u);
            }
            while (!q.isProbablePrime(certainty));

            counter = 0;

            int offset = 2;

            while (counter < 4096)
            {
                for (int k = 0; k < n; k++)
                {
                    add(part1, seed, offset + k);
                    sha1.update(part1, 0, part1.length);
                    sha1.doFinal(part1, 0);
                    System.arraycopy(part1, 0, w, w.length - (k + 1) * part1.length, part1.length);
                }

                add(part1, seed, offset + n);
                sha1.update(part1, 0, part1.length);
                sha1.doFinal(part1, 0);
                System.arraycopy(part1, part1.length - ((w.length - (n) * part1.length)), w, 0, w.length - n * part1.length);

                w[0] |= (byte)0x80;

                BigInteger  x = new BigInteger(1, w);

                BigInteger  c = x.mod(q.multiply(TWO));

                p = x.subtract(c.subtract(ONE));

                if (p.testBit(size - 1))
                {
                    if (p.isProbablePrime(certainty))
                    {
                        primesFound = true;
                        break;
                    }
                }

                counter += 1;
                offset += n + 1;
            }
		}

		//
		// calculate the generator g
	    //
        BigInteger  pMinusOneOverQ = p.subtract(ONE).divide(q);

        for (;;)
        {
            BigInteger h = new BigInteger(size, random);

            if (h.compareTo(ONE) <= 0 || h.compareTo(p.subtract(ONE)) >= 0)
            {
                continue;
            }

            g = h.modPow(pMinusOneOverQ, p);
            if (g.compareTo(ONE) <= 0)
            {
                continue;
            }

            break;
        }

        return new DSAParameters(p, q, g, new DSAValidationParameters(seed, counter));
    }
}
