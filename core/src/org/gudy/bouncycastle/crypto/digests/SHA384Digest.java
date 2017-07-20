package org.gudy.bouncycastle.crypto.digests;

/**
 * Draft FIPS 180-2 implementation of SHA-384. <b>Note:</b> As this is
 * based on a draft this implementation is subject to change.
 *
 * <pre>
 *         block  word  digest
 * SHA-1   512    32    160
 * SHA-256 512    32    256
 * SHA-384 1024   64    384
 * SHA-512 1024   64    512
 * </pre>
 */
public class SHA384Digest
    extends LongDigest
{

	private static final int	DIGEST_LENGTH = 48;

	/**
	 * Standard constructor
	 */
    public SHA384Digest()
    {
    }

	/**
	 * Copy constructor.  This will copy the state of the provided
	 * message digest.
	 */
	public SHA384Digest(SHA384Digest t)
	{
		super(t);
	}

    @Override
    public String getAlgorithmName()
	{
		return "SHA-384";
	}

	@Override
	public int getDigestSize()
	{
		return DIGEST_LENGTH;
	}

    @Override
    public int doFinal(
        byte[]  out,
        int     outOff)
    {
        finish();

        unpackWord(H1, out, outOff);
        unpackWord(H2, out, outOff + 8);
        unpackWord(H3, out, outOff + 16);
        unpackWord(H4, out, outOff + 24);
        unpackWord(H5, out, outOff + 32);
        unpackWord(H6, out, outOff + 40);

        reset();

        return DIGEST_LENGTH;
    }

    /**
     * reset the chaining variables
     */
    @Override
    public void reset()
    {
        super.reset();

		/* SHA-384 initial hash value
		 * The first 64 bits of the fractional parts of the square roots
		 * of the 9th through 16th prime numbers
		 */
		H1 = 0xcbbb9d5dc1059ed8l;
		H2 = 0x629a292a367cd507l;
		H3 = 0x9159015a3070dd17l;
		H4 = 0x152fecd8f70e5939l;
		H5 = 0x67332667ffc00b31l;
		H6 = 0x8eb44a8768581511l;
		H7 = 0xdb0c2e0d64f98fa7l;
		H8 = 0x47b5481dbefa4fa4l;
    }
}
