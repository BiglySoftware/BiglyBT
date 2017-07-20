package org.gudy.bouncycastle.crypto.generators;

import org.gudy.bouncycastle.crypto.CipherKeyGenerator;
import org.gudy.bouncycastle.crypto.params.DESParameters;

public class DESKeyGenerator
    extends CipherKeyGenerator
{
    @Override
    public byte[] generateKey()
    {
        byte[]  newKey = new byte[DESParameters.DES_KEY_LENGTH];

        do
        {
            random.nextBytes(newKey);

            DESParameters.setOddParity(newKey);
        }
        while (DESParameters.isWeakKey(newKey, 0));

        return newKey;
    }
}
