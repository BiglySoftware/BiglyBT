package org.gudy.bouncycastle.openssl;

import java.io.IOException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.RC2ParameterSpec;

import org.gudy.bouncycastle.crypto.PBEParametersGenerator;
import org.gudy.bouncycastle.crypto.generators.OpenSSLPBEParametersGenerator;
import org.gudy.bouncycastle.crypto.params.KeyParameter;

final class PEMUtilities
{
    static byte[] crypt(
        boolean encrypt,
        String  provider,
        byte[]  bytes,
        char[]  password,
        String  dekAlgName,
        byte[]  iv)
        throws IOException
    {
        AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv);
        String                 alg;
        String                 blockMode = "CBC";
        String                 padding = "PKCS5Padding";
        Key                    sKey;


        // Figure out block mode and padding.
        if (dekAlgName.endsWith("-CFB"))
        {
            blockMode = "CFB";
            padding = "NoPadding";
        }
        if (dekAlgName.endsWith("-ECB") ||
            "DES-EDE".equals(dekAlgName) ||
            "DES-EDE3".equals(dekAlgName))
        {
            // ECB is actually the default (though seldom used) when OpenSSL
            // uses DES-EDE (des2) or DES-EDE3 (des3).
            blockMode = "ECB";
            paramSpec = null;
        }
        if (dekAlgName.endsWith("-OFB"))
        {
            blockMode = "OFB";
            padding = "NoPadding";
        }


        // Figure out algorithm and key size.
        if (dekAlgName.startsWith("DES-EDE"))
        {
            alg = "DESede";
            // "DES-EDE" is actually des2 in OpenSSL-speak!
            // "DES-EDE3" is des3.
            boolean des2 = !dekAlgName.startsWith("DES-EDE3");
            sKey = getKey(password, alg, 24, iv, des2);
        }
        else if (dekAlgName.startsWith("DES-"))
        {
            alg = "DES";
            sKey = getKey(password, alg, 8, iv);
        }
        else if (dekAlgName.startsWith("BF-"))
        {
            alg = "Blowfish";
            sKey = getKey(password, alg, 16, iv);
        }
        else if (dekAlgName.startsWith("RC2-"))
        {
            alg = "RC2";
            int keyBits = 128;
            if (dekAlgName.startsWith("RC2-40-"))
            {
                keyBits = 40;
            }
            else if (dekAlgName.startsWith("RC2-64-"))
            {
                keyBits = 64;
            }
            sKey = getKey(password, alg, keyBits / 8, iv);
            if (paramSpec == null) // ECB block mode
            {
                paramSpec = new RC2ParameterSpec(keyBits);
            }
            else
            {
                paramSpec = new RC2ParameterSpec(keyBits, iv);
            }
        }
        else if (dekAlgName.startsWith("AES-"))
        {
            alg = "AES";
            byte[] salt = iv;
            if (salt.length > 8)
            {
                salt = new byte[8];
                System.arraycopy(iv, 0, salt, 0, 8);
            }

            int keyBits;
            if (dekAlgName.startsWith("AES-128-"))
            {
                keyBits = 128;
            }
            else if (dekAlgName.startsWith("AES-192-"))
            {
                keyBits = 192;
            }
            else if (dekAlgName.startsWith("AES-256-"))
            {
                keyBits = 256;
            }
            else
            {
                throw new IOException("unknown AES encryption with private key");
            }
            sKey = getKey(password, "AES", keyBits / 8, salt);
        }
        else
        {
            throw new IOException("unknown encryption with private key");
        }

        String transformation = alg + "/" + blockMode + "/" + padding;

        try
        {
            Cipher c = Cipher.getInstance(transformation, provider);
            int    mode = encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;

            if (paramSpec == null) // ECB block mode
            {
                c.init(mode, sKey);
            }
            else
            {
                c.init(mode, sKey, paramSpec);
            }
            return c.doFinal(bytes);
        }
        catch (Exception e)
        {
            throw new IOException("exception using cipher: " + e.toString());
        }
    }

    private static SecretKey getKey(
        char[]  password,
        String  algorithm,
        int     keyLength,
        byte[]  salt)
        throws IOException
    {
        return getKey(password, algorithm, keyLength, salt, false);
    }

    private static SecretKey getKey(
        char[]  password,
        String  algorithm,
        int     keyLength,
        byte[]  salt,
        boolean des2)
        throws IOException
    {
        OpenSSLPBEParametersGenerator   pGen = new OpenSSLPBEParametersGenerator();

        pGen.init(PBEParametersGenerator.PKCS5PasswordToBytes(password), salt);

        KeyParameter keyParam;
        keyParam = (KeyParameter) pGen.generateDerivedParameters(keyLength * 8);
        byte[] key = keyParam.getKey();
        if (des2 && key.length >= 24)
        {
            // For DES2, we must copy first 8 bytes into the last 8 bytes.
            System.arraycopy(key, 0, key, 16, 8);
        }
        return new javax.crypto.spec.SecretKeySpec(key, algorithm);
    }
}
