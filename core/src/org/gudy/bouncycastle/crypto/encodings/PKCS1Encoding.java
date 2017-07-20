package org.gudy.bouncycastle.crypto.encodings;

import java.security.SecureRandom;

import org.gudy.bouncycastle.crypto.AsymmetricBlockCipher;
import org.gudy.bouncycastle.crypto.CipherParameters;
import org.gudy.bouncycastle.crypto.InvalidCipherTextException;
import org.gudy.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.gudy.bouncycastle.crypto.params.ParametersWithRandom;

/**
 * this does your basic PKCS 1 v1.5 padding - whether or not you should be using this
 * depends on your application - see PKCS1 Version 2 for details.
 */
public class PKCS1Encoding
    implements AsymmetricBlockCipher
{
    private static int      HEADER_LENGTH = 10;

    private SecureRandom            random;
    private AsymmetricBlockCipher   engine;
    private boolean                 forEncryption;
    private boolean                 forPrivateKey;

    public PKCS1Encoding(
        AsymmetricBlockCipher   cipher)
    {
        this.engine = cipher;
    }

    public AsymmetricBlockCipher getUnderlyingCipher()
    {
        return engine;
    }

    @Override
    public void init(
        boolean             forEncryption,
        CipherParameters    param)
    {
        AsymmetricKeyParameter  kParam;

        if (param instanceof ParametersWithRandom)
        {
            ParametersWithRandom    rParam = (ParametersWithRandom)param;

            this.random = rParam.getRandom();
            kParam = (AsymmetricKeyParameter)rParam.getParameters();
        }
        else
        {
            this.random = new SecureRandom();
            kParam = (AsymmetricKeyParameter)param;
        }

        engine.init(forEncryption, kParam);

        this.forPrivateKey = kParam.isPrivate();
        this.forEncryption = forEncryption;
    }

    @Override
    public int getInputBlockSize()
    {
        int     baseBlockSize = engine.getInputBlockSize();

        if (forEncryption)
        {
            return baseBlockSize - HEADER_LENGTH;
        }
        else
        {
            return baseBlockSize;
        }
    }

    @Override
    public int getOutputBlockSize()
    {
        int     baseBlockSize = engine.getOutputBlockSize();

        if (forEncryption)
        {
            return baseBlockSize;
        }
        else
        {
            return baseBlockSize - HEADER_LENGTH;
        }
    }

    @Override
    public byte[] processBlock(
        byte[]  in,
        int     inOff,
        int     inLen)
        throws InvalidCipherTextException
    {
        if (forEncryption)
        {
            return encodeBlock(in, inOff, inLen);
        }
        else
        {
            return decodeBlock(in, inOff, inLen);
        }
    }

    private byte[] encodeBlock(
        byte[]  in,
        int     inOff,
        int     inLen)
        throws InvalidCipherTextException
    {
        byte[]  block = new byte[engine.getInputBlockSize()];

        if (forPrivateKey)
        {
            block[0] = 0x01;                        // type code 1

            for (int i = 1; i != block.length - inLen - 1; i++)
            {
                block[i] = (byte)0xFF;
            }
        }
        else
        {
            random.nextBytes(block);                // random fill

            block[0] = 0x02;                        // type code 2

            //
            // a zero byte marks the end of the padding, so all
            // the pad bytes must be non-zero.
            //
            for (int i = 1; i != block.length - inLen - 1; i++)
            {
                while (block[i] == 0)
                {
                    block[i] = (byte)random.nextInt();
                }
            }
        }

        block[block.length - inLen - 1] = 0x00;       // mark the end of the padding
        System.arraycopy(in, inOff, block, block.length - inLen, inLen);

        return engine.processBlock(block, 0, block.length);
    }

    /**
     * @exception InvalidCipherTextException if the decrypted block is not in PKCS1 format.
     */
    private byte[] decodeBlock(
        byte[]  in,
        int     inOff,
        int     inLen)
        throws InvalidCipherTextException
    {
        byte[]  block = engine.processBlock(in, inOff, inLen);

        if (block.length < getOutputBlockSize())
        {
            throw new InvalidCipherTextException("block truncated");
        }

        if (block[0] != 1 && block[0] != 2)
        {
            throw new InvalidCipherTextException("unknown block type");
        }

        //
        // find and extract the message block.
        //
        int start;

        for (start = 1; start != block.length; start++)
        {
            if (block[start] == 0)
            {
                break;
            }
        }

        start++;           // data should start at the next byte

        if (start >= block.length || start < HEADER_LENGTH)
        {
            throw new InvalidCipherTextException("no data in block");
        }

        byte[]  result = new byte[block.length - start];

        System.arraycopy(block, start, result, 0, result.length);

        return result;
    }
}
