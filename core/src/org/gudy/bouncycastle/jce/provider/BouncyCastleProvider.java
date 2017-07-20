package org.gudy.bouncycastle.jce.provider;

import java.security.Provider;

/**
 * To add the provider at runtime use:
 * <pre>
 * import java.security.Security;
 * import org.gudy.bouncycastle.jce.provider.BouncyCastleProvider;
 *
 * Security.addProvider(new BouncyCastleProvider());
 * </pre>
 * The provider can also be configured as part of your environment via
 * static registration by adding an entry to the java.security properties
 * file (found in $JAVA_HOME/jre/lib/security/java.security, where
 * $JAVA_HOME is the location of your JDK/JRE distribution). You'll find
 * detailed instructions in the file but basically it comes down to adding
 * a line:
 * <pre>
 * <code>
 *    security.provider.&lt;n&gt;=org.gudy.bouncycastle.jce.provider.BouncyCastleProvider
 * </code>
 * </pre>
 * Where &lt;n&gt; is the preference you want the provider at (1 being the
 * most prefered).
 * <p>Note: JCE algorithm names should be uppercase only so the case insensitive
 * test for getInstance works.
 */
public final class BouncyCastleProvider extends Provider
{
	private static String info = "BouncyCastle Security Provider v1.23";

    public static String PROVIDER_NAME = "BC_VUZE";

	/**
	 * Construct a new provider.  This should only be required when
	 * using runtime registration of the provider using the
	 * <code>Security.addProvider()</code> mechanism.
	 */
	public BouncyCastleProvider()
	{
		super(PROVIDER_NAME, 1.23, info);

        //
        // KeyStore
        //
        put("KeyStore.BKS", "org.gudy.bouncycastle.jce.provider.JDKKeyStore");
        put("KeyStore.BouncyCastle", "org.gudy.bouncycastle.jce.provider.JDKKeyStore$BouncyCastleStore");
        put("KeyStore.PKCS12", "org.gudy.bouncycastle.jce.provider.JDKPKCS12KeyStore$BCPKCS12KeyStore");
		put("KeyStore.BCPKCS12", "org.gudy.bouncycastle.jce.provider.JDKPKCS12KeyStore$BCPKCS12KeyStore");
        put("KeyStore.PKCS12-DEF", "org.gudy.bouncycastle.jce.provider.JDKPKCS12KeyStore$DefPKCS12KeyStore");
        put("Alg.Alias.KeyStore.UBER", "BouncyCastle");
        put("Alg.Alias.KeyStore.BOUNCYCASTLE", "BouncyCastle");
        put("Alg.Alias.KeyStore.bouncycastle", "BouncyCastle");

        //
        // certificate factories.
        //
        put("CertificateFactory.X.509", "org.gudy.bouncycastle.jce.provider.JDKX509CertificateFactory");
		put("Alg.Alias.CertificateFactory.X509", "X.509");

        //
        // algorithm parameter generators
        //
        put("AlgorithmParameterGenerator.DH", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$DH");
        put("AlgorithmParameterGenerator.DSA", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$DSA");
        put("AlgorithmParameterGenerator.ELGAMAL", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$ElGamal");
        put("AlgorithmParameterGenerator.DES", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$DES");
        put("AlgorithmParameterGenerator.DESEDE", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$DES");
        put("AlgorithmParameterGenerator.1.2.840.113549.3.7", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$DES");
        put("AlgorithmParameterGenerator.1.3.14.3.2.7", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$DES");
        put("AlgorithmParameterGenerator.IDEA", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$IDEA");
        put("AlgorithmParameterGenerator.1.3.6.1.4.1.188.7.1.1.2", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$IDEA");
        put("AlgorithmParameterGenerator.RC2", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$RC2");
        put("AlgorithmParameterGenerator.1.2.840.113549.3.2", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$RC2");
        put("AlgorithmParameterGenerator.CAST5", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$CAST5");
        put("AlgorithmParameterGenerator.1.2.840.113533.7.66.10", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$CAST5");
        put("AlgorithmParameterGenerator.AES", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameterGenerator$AES");
        put("Alg.Alias.AlgorithmParameterGenerator.2.16.840.1.101.3.4.2", "AES");  // these first 3 are wrong, but seem to have got around
        put("Alg.Alias.AlgorithmParameterGenerator.2.16.840.1.101.3.4.22", "AES");
        put("Alg.Alias.AlgorithmParameterGenerator.2.16.840.1.101.3.4.42", "AES");
		put("Alg.Alias.AlgorithmParameterGenerator.2.16.840.1.101.3.4.1.2", "AES");
		put("Alg.Alias.AlgorithmParameterGenerator.2.16.840.1.101.3.4.1.22", "AES");
		put("Alg.Alias.AlgorithmParameterGenerator.2.16.840.1.101.3.4.1.42", "AES");
        //
        // algorithm parameters
        //
        put("AlgorithmParameters.DH", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$DH");
        put("AlgorithmParameters.DSA", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$DSA");
        put("AlgorithmParameters.ELGAMAL", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$ElGamal");
        put("AlgorithmParameters.IES", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IES");
        put("AlgorithmParameters.PKCS12PBE", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$PKCS12PBE");
        put("AlgorithmParameters.1.2.840.113549.3.7", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.IDEA", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IDEAAlgorithmParameters");
        put("AlgorithmParameters.1.3.6.1.4.1.188.7.1.1.2", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IDEAAlgorithmParameters");
        put("AlgorithmParameters.CAST5", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$CAST5AlgorithmParameters");
        put("AlgorithmParameters.1.2.840.113533.7.66.10", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$CAST5AlgorithmParameters");
        put("Alg.Alias.AlgorithmParameters.PBEWITHSHA1ANDRC2", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.PBEWITHSHAAND3-KEYTRIPLEDES", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.PBEWITHSHAAND2-KEYTRIPLEDES", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.PBEWITHSHAANDRC2", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.PBEWITHSHAANDRC4", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.PBEWITHSHAANDTWOFISH", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.PBEWITHSHAANDIDEA", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.1.2.840.113549.1.12.1.1", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.1.2.840.113549.1.12.1.2", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.1.2.840.113549.1.12.1.3", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.1.2.840.113549.1.12.1.4", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.1.2.840.113549.1.12.1.5", "PKCS12PBE");
        put("Alg.Alias.AlgorithmParameters.1.2.840.113549.1.12.1.6", "PKCS12PBE");

        //
        // key agreement
        //
        put("KeyAgreement.DH", "org.gudy.bouncycastle.jce.provider.JCEDHKeyAgreement");
        put("KeyAgreement.ECDH", "org.gudy.bouncycastle.jce.provider.JCEECDHKeyAgreement$DH");
        put("KeyAgreement.ECDHC", "org.gudy.bouncycastle.jce.provider.JCEECDHKeyAgreement$DHC");

		//
		// cipher engines
		//
		put("Cipher.DES", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$DES");
		put("Cipher.DESEDE", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$DESede");
		put("Cipher.1.2.840.113549.3.7", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$DESedeCBC");
		put("Cipher.1.3.14.3.2.7", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$DESCBC");
        put("Cipher.DESEDEWRAP", "org.gudy.bouncycastle.jce.provider.WrapCipherSpi$DESEDEWrap");
        put("Cipher.1.2.840.113549.1.9.16.3.6", "org.gudy.bouncycastle.jce.provider.WrapCipherSpi$DESEDEWrap");
		put("Cipher.SKIPJACK", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$Skipjack");
		put("Cipher.BLOWFISH", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$Blowfish");
		put("Cipher.TWOFISH", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$Twofish");
		put("Cipher.RC2", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$RC2");
        put("Cipher.RC2WRAP", "org.gudy.bouncycastle.jce.provider.WrapCipherSpi$RC2Wrap");
        put("Cipher.1.2.840.113549.1.9.16.3.7", "org.gudy.bouncycastle.jce.provider.WrapCipherSpi$RC2Wrap");
        put("Cipher.ARC4", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$RC4");
        put("Cipher.RC4", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$RC4");
		put("Alg.Alias.Cipher.1.2.840.113549.3.4", "RC4");
		put("Cipher.RC5", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$RC5");
		put("Cipher.1.2.840.113549.3.2", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$RC2CBC");
		put("Alg.Alias.Cipher.RC5-32", "RC5");
		put("Cipher.RC5-64", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$RC564");
		put("Cipher.RC6", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$RC6");
		put("Cipher.RIJNDAEL", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$Rijndael");
		put("Cipher.AES", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$AES");
		put("Alg.Alias.Cipher.2.16.840.1.101.3.4.2", "AES");
		put("Alg.Alias.Cipher.2.16.840.1.101.3.4.22", "AES");
		put("Alg.Alias.Cipher.2.16.840.1.101.3.4.42", "AES");
		put("Cipher.2.16.840.1.101.3.4.1.2", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$AESCBC");
		put("Cipher.2.16.840.1.101.3.4.1.22", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$AESCBC");
		put("Cipher.2.16.840.1.101.3.4.1.42", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$AESCBC");
		put("Cipher.AESWRAP", "org.gudy.bouncycastle.jce.provider.WrapCipherSpi$AESWrap");
		put("Cipher.SERPENT", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$Serpent");
		put("Cipher.CAST5", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$CAST5");
		put("Cipher.1.2.840.113533.7.66.10", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$CAST5CBC");
		put("Cipher.CAST6", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$CAST6");
		put("Cipher.IDEA", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$IDEA");
		put("Cipher.1.3.6.1.4.1.188.7.1.1.2", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$IDEACBC");

/*
		put("Cipher.DES/CFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$DES_CFB8");
		put("Cipher.DESEDE/CFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$DESede_CFB8");
		put("Cipher.SKIPJACK/CFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$Skipjack_CFB8");
		put("Cipher.BLOWFISH/CFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$Blowfish_CFB8");
		put("Cipher.TWOFISH/CFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$Twofish_CFB8");
		put("Cipher.IDEA/CFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$IDEA_CFB8");

		put("Alg.Alias.Cipher.DES/CFB8/NOPADDING", "DES/CFB8");
		put("Alg.Alias.Cipher.DESEDE/CFB8/NOPADDING", "DESEDE/CFB8");
		put("Alg.Alias.Cipher.SKIPJACK/CFB8/NOPADDING", "SKIPJACK/CFB8");
		put("Alg.Alias.Cipher.BLOWFISH/CFB8/NOPADDING", "Blowfish/CFB8");
		put("Alg.Alias.Cipher.TWOFISH/CFB8/NOPADDING", "Twofish/CFB8");
		put("Alg.Alias.Cipher.IDEA/CFB8/NOPADDING", "IDEA/CFB8");

		put("Cipher.DES/OFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$DES_OFB8");
		put("Cipher.DESEDE/OFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$DESede_OFB8");
		put("Cipher.SKIPJACK/OFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$Skipjack_OFB8");
		put("Cipher.BLOWFISH/OFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$Blowfish_OFB8");
		put("Cipher.TWOFISH/OFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$Twofish_OFB8");
		put("Cipher.IDEA/OFB8", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$IDEA_OFB8");

		put("Alg.Alias.Cipher.DES/OFB8/NOPADDING", "DES/OFB8");
		put("Alg.Alias.Cipher.DESEDE/OFB8/NOPADDING", "DESEDE/OFB8");
		put("Alg.Alias.Cipher.SKIPJACK/OFB8/NOPADDING", "SKIPJACK/OFB8");
		put("Alg.Alias.Cipher.BLOWFISH/OFB8/NOPADDING", "BLOWFISH/OFB8");
		put("Alg.Alias.Cipher.TWOFISH/OFB8/NOPADDING", "TWOFISH/OFB8");
		put("Alg.Alias.Cipher.IDEA/OFB8/NOPADDING", "IDEA/OFB8");
*/

		put("Cipher.RSA", "org.gudy.bouncycastle.jce.provider.JCERSACipher$NoPadding");
		put("Cipher.RSA/RAW", "org.gudy.bouncycastle.jce.provider.JCERSACipher$NoPadding");
		put("Cipher.RSA/PKCS1", "org.gudy.bouncycastle.jce.provider.JCERSACipher$PKCS1v1_5Padding");
		put("Cipher.1.2.840.113549.1.1.1", "org.gudy.bouncycastle.jce.provider.JCERSACipher$PKCS1v1_5Padding");
		put("Cipher.2.5.8.1.1", "org.gudy.bouncycastle.jce.provider.JCERSACipher$PKCS1v1_5Padding");
		put("Cipher.RSA/1", "org.gudy.bouncycastle.jce.provider.JCERSACipher$PKCS1v1_5Padding_PrivateOnly");
		put("Cipher.RSA/2", "org.gudy.bouncycastle.jce.provider.JCERSACipher$PKCS1v1_5Padding_PublicOnly");
		put("Cipher.RSA/OAEP", "org.gudy.bouncycastle.jce.provider.JCERSACipher$OAEPPadding");
		put("Cipher.1.2.840.113549.1.1.7", "org.gudy.bouncycastle.jce.provider.JCERSACipher$OAEPPadding");
		put("Cipher.RSA/ISO9796-1", "org.gudy.bouncycastle.jce.provider.JCERSACipher$ISO9796d1Padding");

		put("Cipher.ECIES", "org.gudy.bouncycastle.jce.provider.JCEIESCipher$ECIES");
		put("Cipher.ELGAMAL", "org.gudy.bouncycastle.jce.provider.JCEElGamalCipher$NoPadding");
		put("Cipher.ELGAMAL/PKCS1", "org.gudy.bouncycastle.jce.provider.JCEElGamalCipher$PKCS1v1_5Padding");

		put("Alg.Alias.Cipher.RSA//RAW", "RSA");
		put("Alg.Alias.Cipher.RSA//NOPADDING", "RSA");
		put("Alg.Alias.Cipher.RSA//PKCS1PADDING", "RSA/PKCS1");
		put("Alg.Alias.Cipher.RSA//OAEPPADDING", "RSA/OAEP");
		put("Alg.Alias.Cipher.RSA//ISO9796-1PADDING", "RSA/ISO9796-1");
		put("Alg.Alias.Cipher.RSA/ECB/NOPADDING", "RSA");
		put("Alg.Alias.Cipher.RSA/ECB/PKCS1PADDING", "RSA/PKCS1");
		put("Alg.Alias.Cipher.RSA/ECB/OAEPPADDING", "RSA/OAEP");
		put("Alg.Alias.Cipher.RSA/ECB/ISO9796-1PADDING", "RSA/ISO9796-1");
		put("Alg.Alias.Cipher.RSA/NONE/NOPADDING", "RSA");
		put("Alg.Alias.Cipher.RSA/NONE/PKCS1PADDING", "RSA/PKCS1");
		put("Alg.Alias.Cipher.RSA/NONE/OAEPPADDING", "RSA/OAEP");
		put("Alg.Alias.Cipher.RSA/NONE/ISO9796-1PADDING", "RSA/ISO9796-1");
		put("Alg.Alias.Cipher.RSA/1/PCKS1PADDING", "RSA/1");
		put("Alg.Alias.Cipher.RSA/2/PCKS1PADDING", "RSA/2");

		put("Alg.Alias.Cipher.ELGAMAL/ECB/PKCS1PADDING", "ELGAMAL/PKCS1");
		put("Alg.Alias.Cipher.ELGAMAL/NONE/PKCS1PADDING", "ELGAMAL/PKCS1");

        put("Cipher.PBEWITHMD5ANDDES", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithMD5AndDES");
        put("Cipher.BROKENPBEWITHMD5ANDDES", "org.gudy.bouncycastle.jce.provider.BrokenJCEBlockCipher$BrokePBEWithMD5AndDES");
        put("Cipher.PBEWITHMD5ANDRC2", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithMD5AndRC2");
        put("Cipher.PBEWITHSHA1ANDDES", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithSHA1AndDES");
        put("Cipher.BROKENPBEWITHSHA1ANDDES", "org.gudy.bouncycastle.jce.provider.BrokenJCEBlockCipher$BrokePBEWithSHA1AndDES");
        put("Cipher.PBEWITHSHA1ANDRC2", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithSHA1AndRC2");
        put("Cipher.PBEWITHSHAAND3-KEYTRIPLEDES-CBC", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithSHAAndDES3Key");
        put("Cipher.BROKENPBEWITHSHAAND3-KEYTRIPLEDES-CBC", "org.gudy.bouncycastle.jce.provider.BrokenJCEBlockCipher$BrokePBEWithSHAAndDES3Key");
        put("Cipher.OLDPBEWITHSHAAND3-KEYTRIPLEDES-CBC", "org.gudy.bouncycastle.jce.provider.BrokenJCEBlockCipher$OldPBEWithSHAAndDES3Key");
        put("Cipher.PBEWITHSHAAND2-KEYTRIPLEDES-CBC", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithSHAAndDES2Key");
        put("Cipher.BROKENPBEWITHSHAAND2-KEYTRIPLEDES-CBC", "org.gudy.bouncycastle.jce.provider.BrokenJCEBlockCipher$BrokePBEWithSHAAndDES2Key");
        put("Cipher.PBEWITHSHAAND128BITRC2-CBC", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithSHAAnd128BitRC2");
        put("Cipher.PBEWITHSHAAND40BITRC2-CBC", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithSHAAnd40BitRC2");
        put("Cipher.PBEWITHSHAAND128BITRC4", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$PBEWithSHAAnd128BitRC4");
        put("Cipher.PBEWITHSHAAND40BITRC4", "org.gudy.bouncycastle.jce.provider.JCEStreamCipher$PBEWithSHAAnd40BitRC4");

        put("Cipher.PBEWITHSHAANDTWOFISH-CBC", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithSHAAndTwofish");
        put("Cipher.OLDPBEWITHSHAANDTWOFISH-CBC", "org.gudy.bouncycastle.jce.provider.BrokenJCEBlockCipher$OldPBEWithSHAAndTwofish");
        put("Cipher.PBEWITHSHAANDIDEA-CBC", "org.gudy.bouncycastle.jce.provider.JCEBlockCipher$PBEWithSHAAndIDEA");

        put("Alg.Alias.Cipher.1.2.840.113549.1.12.1.1", "PBEWITHSHAAND128BITRC4");
        put("Alg.Alias.Cipher.1.2.840.113549.1.12.1.2", "PBEWITHSHAAND40BITRC4");
        put("Alg.Alias.Cipher.1.2.840.113549.1.12.1.3", "PBEWITHSHAAND3-KEYTRIPLEDES-CBC");
        put("Alg.Alias.Cipher.1.2.840.113549.1.12.1.4", "PBEWITHSHAAND2-KEYTRIPLEDES-CBC");
        put("Alg.Alias.Cipher.1.2.840.113549.1.12.1.5", "PBEWITHSHAAND128BITRC2-CBC");
        put("Alg.Alias.Cipher.1.2.840.113549.1.12.1.6", "PBEWITHSHAAND40BITRC2-CBC");

        //
        // key generators.
        //
        put("KeyGenerator.DES", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$DES");
        put("Alg.Alias.KeyGenerator.1.3.14.3.2.7", "DES");
        put("KeyGenerator.DESEDE", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$DESede");
        put("KeyGenerator.1.2.840.113549.3.7", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$DESede3");
        put("KeyGenerator.DESEDEWRAP", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$DESede");
        put("KeyGenerator.SKIPJACK", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$Skipjack");
        put("KeyGenerator.BLOWFISH", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$Blowfish");
        put("KeyGenerator.TWOFISH", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$Twofish");
        put("KeyGenerator.RC2", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$RC2");
        put("KeyGenerator.1.2.840.113549.3.2", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$RC2");
        put("KeyGenerator.RC4", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$RC4");
        put("Alg.Alias.KeyGenerator.ARC4", "RC4");
        put("Alg.Alias.KeyGenerator.1.2.840.113549.3.4", "RC4");
        put("KeyGenerator.RC5", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$RC5");
        put("Alg.Alias.KeyGenerator.RC5-32", "RC5");
        put("KeyGenerator.RC5-64", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$RC564");
        put("KeyGenerator.RC6", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$RC6");
        put("KeyGenerator.RIJNDAEL", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$Rijndael");
        put("KeyGenerator.AES", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$AES");
        put("KeyGenerator.2.16.840.1.101.3.4.2", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$AES128");
        put("KeyGenerator.2.16.840.1.101.3.4.22", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$AES192");
        put("KeyGenerator.2.16.840.1.101.3.4.42", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$AES256");
		put("KeyGenerator.2.16.840.1.101.3.4.1.2", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$AES128");
		put("KeyGenerator.2.16.840.1.101.3.4.1.22", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$AES192");
		put("KeyGenerator.2.16.840.1.101.3.4.1.42", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$AES256");
        put("KeyGenerator.AESWRAP", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$AES");
        put("KeyGenerator.SERPENT", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$Serpent");
        put("KeyGenerator.CAST5", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$CAST5");
        put("KeyGenerator.1.2.840.113533.7.66.10", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$CAST5");
        put("KeyGenerator.CAST6", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$CAST6");
        put("KeyGenerator.IDEA", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$IDEA");
        put("KeyGenerator.1.3.6.1.4.1.188.7.1.1.2", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$IDEA");
        put("KeyGenerator.HMACMD2", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$MD2HMAC");
        put("KeyGenerator.HMACMD4", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$MD4HMAC");
        put("KeyGenerator.HMACMD5", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$MD5HMAC");
        put("KeyGenerator.HMACRIPEMD128", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$RIPEMD128HMAC");
        put("KeyGenerator.HMACRIPEMD160", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$RIPEMD160HMAC");
        put("KeyGenerator.HMACSHA1", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$HMACSHA1");
        put("KeyGenerator.HMACTIGER", "org.gudy.bouncycastle.jce.provider.JCEKeyGenerator$HMACTIGER");

        //
        // key pair generators.
        //
        put("KeyPairGenerator.RSA", "org.gudy.bouncycastle.jce.provider.JDKKeyPairGenerator$RSA");
        put("KeyPairGenerator.DH", "org.gudy.bouncycastle.jce.provider.JDKKeyPairGenerator$DH");
        put("KeyPairGenerator.DSA", "org.gudy.bouncycastle.jce.provider.JDKKeyPairGenerator$DSA");
        put("KeyPairGenerator.ELGAMAL", "org.gudy.bouncycastle.jce.provider.JDKKeyPairGenerator$ElGamal");
        put("KeyPairGenerator.ECDSA", "org.gudy.bouncycastle.jce.provider.JDKKeyPairGenerator$ECDSA");
        put("KeyPairGenerator.ECDH", "org.gudy.bouncycastle.jce.provider.JDKKeyPairGenerator$ECDH");
        put("KeyPairGenerator.ECDHC", "org.gudy.bouncycastle.jce.provider.JDKKeyPairGenerator$ECDHC");
        put("KeyPairGenerator.ECIES", "org.gudy.bouncycastle.jce.provider.JDKKeyPairGenerator$ECDH");

        put("Alg.Alias.KeyPairGenerator.1.2.840.113549.1.1.1", "RSA");

        //
        // key factories
        //
        put("KeyFactory.RSA", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$RSA");
        put("KeyFactory.DH", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$DH");
        put("KeyFactory.DSA", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$DSA");
        put("KeyFactory.ELGAMAL", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$ElGamal");
        put("KeyFactory.ElGamal", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$ElGamal");
        put("KeyFactory.EC", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$EC");
        put("KeyFactory.ECDSA", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$ECDSA");
        put("KeyFactory.ECDH", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$ECDH");
        put("KeyFactory.ECDHC", "org.gudy.bouncycastle.jce.provider.JDKKeyFactory$ECDHC");

        put("Alg.Alias.KeyFactory.1.2.840.113549.1.1.1", "RSA");
        put("Alg.Alias.KeyFactory.1.2.840.10040.4.1", "DSA");

        //
        // Algorithm parameters
        //
        put("AlgorithmParameters.DES", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("Alg.Alias.AlgorithmParameters.1.3.14.3.2.7", "DES");
        put("AlgorithmParameters.DESEDE", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.1.2.840.113549.3.7", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.RC2", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$RC2AlgorithmParameters");
        put("AlgorithmParameters.1.2.840.113549.3.2", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$RC2AlgorithmParameters");
        put("AlgorithmParameters.RC5", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.RC6", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.IDEA", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IDEAAlgorithmParameters");
        put("AlgorithmParameters.BLOWFISH", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.TWOFISH", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.SKIPJACK", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.RIJNDAEL", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("AlgorithmParameters.AES", "org.gudy.bouncycastle.jce.provider.JDKAlgorithmParameters$IVAlgorithmParameters");
        put("Alg.Alias.AlgorithmParameters.2.16.840.1.101.3.4.2", "AES");
        put("Alg.Alias.AlgorithmParameters.2.16.840.1.101.3.4.22", "AES");
        put("Alg.Alias.AlgorithmParameters.2.16.840.1.101.3.4.42", "AES");
		put("Alg.Alias.AlgorithmParameters.2.16.840.1.101.3.4.1.2", "AES");
		put("Alg.Alias.AlgorithmParameters.2.16.840.1.101.3.4.1.22", "AES");
		put("Alg.Alias.AlgorithmParameters.2.16.840.1.101.3.4.1.42", "AES");

        //
        // secret key factories.
        //
        put("SecretKeyFactory.PBE/PKCS5", "org.gudy.bouncycastle.jce.provider.JCESecretKeyFactory$PBE_PKCS5");
        put("SecretKeyFactory.PBE/PKCS12", "org.gudy.bouncycastle.jce.provider.JCESecretKeyFactory$PBE_PKCS12");
        put("SecretKeyFactory.DES", "org.gudy.bouncycastle.jce.provider.JCESecretKeyFactory$DES");
        put("SecretKeyFactory.DESEDE", "org.gudy.bouncycastle.jce.provider.JCESecretKeyFactory$DESede");
        put("SecretKeyFactory.DESEDE", "org.gudy.bouncycastle.jce.provider.JCESecretKeyFactory$DESede");

        put("Alg.Alias.SecretKeyFactory.PBE", "PBE/PKCS5");
        put("Alg.Alias.SecretKeyFactory.PBEWITHMD5ANDDES", "PBE/PKCS5");
        put("Alg.Alias.SecretKeyFactory.BROKENPBEWITHMD5ANDDES", "PBE/PKCS5");
        put("Alg.Alias.SecretKeyFactory.PBEWITHMD5ANDRC2", "PBE/PKCS5");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHA1ANDDES", "PBE/PKCS5");
        put("Alg.Alias.SecretKeyFactory.BROKENPBEWITHSHA1ANDDES", "PBE/PKCS5");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHA1ANDRC2", "PBE/PKCS5");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHAAND3-KEYTRIPLEDES-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.OLDPBEWITHSHAAND3-KEYTRIPLEDES-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.BROKENPBEWITHSHAAND3-KEYTRIPLEDES-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHAAND2-KEYTRIPLEDES-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.BROKENPBEWITHSHAAND2-KEYTRIPLEDES-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHAAND128BITRC4", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHAAND40BITRC4", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHAAND128BITRC2-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHAAND40BITRC2-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHAANDTWOFISH-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.OLDPBEWITHSHAANDTWOFISH-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHSHAANDIDEA-CBC", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHHMACSHA", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.PBEWITHHMACRIPEMD160", "PBE/PKCS12");

        put("Alg.Alias.SecretKeyFactory.1.2.840.113549.1.12.1.1", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.1.2.840.113549.1.12.1.2", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.1.2.840.113549.1.12.1.3", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.1.2.840.113549.1.12.1.4", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.1.2.840.113549.1.12.1.5", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.1.2.840.113549.1.12.1.6", "PBE/PKCS12");
        put("Alg.Alias.SecretKeyFactory.1.3.14.3.2.26", "PBE/PKCS12");

        //
        // MAC's
        //
        put("Mac.DESMAC", "org.gudy.bouncycastle.jce.provider.JCEMac$DES");
        put("Alg.Alias.Mac.DES", "DESMAC");
        put("Mac.DESMAC/CFB8", "org.gudy.bouncycastle.jce.provider.JCEMac$DESCFB8");
        put("Alg.Alias.Mac.DES/CFB8", "DESMAC/CFB8");

        put("Mac.DESEDEMAC", "org.gudy.bouncycastle.jce.provider.JCEMac$DESede");
        put("Alg.Alias.Mac.DESEDE", "DESEDEMAC");
        put("Mac.DESEDEMAC/CFB8", "org.gudy.bouncycastle.jce.provider.JCEMac$DESedeCFB8");
        put("Alg.Alias.Mac.DESEDE/CFB8", "DESEDEMAC/CFB8");

        put("Mac.SKIPJACKMAC", "org.gudy.bouncycastle.jce.provider.JCEMac$Skipjack");
        put("Alg.Alias.Mac.SKIPJACK", "SKIPJACKMAC");
        put("Mac.SKIPJACKMAC/CFB8", "org.gudy.bouncycastle.jce.provider.JCEMac$SkipjackCFB8");
        put("Alg.Alias.Mac.SKIPJACK/CFB8", "SKIPJACKMAC/CFB8");

        put("Mac.IDEAMAC", "org.gudy.bouncycastle.jce.provider.JCEMac$IDEA");
        put("Alg.Alias.Mac.IDEA", "IDEAMAC");
        put("Mac.IDEAMAC/CFB8", "org.gudy.bouncycastle.jce.provider.JCEMac$IDEACFB8");
        put("Alg.Alias.Mac.IDEA/CFB8", "IDEAMAC/CFB8");

        put("Mac.RC2MAC", "org.gudy.bouncycastle.jce.provider.JCEMac$RC2");
        put("Alg.Alias.Mac.RC2", "RC2MAC");
        put("Mac.RC2MAC/CFB8", "org.gudy.bouncycastle.jce.provider.JCEMac$RC2CFB8");
        put("Alg.Alias.Mac.RC2/CFB8", "RC2MAC/CFB8");

        put("Mac.RC5MAC", "org.gudy.bouncycastle.jce.provider.JCEMac$RC5");
        put("Alg.Alias.Mac.RC5", "RC5MAC");
        put("Mac.RC5MAC/CFB8", "org.gudy.bouncycastle.jce.provider.JCEMac$RC5CFB8");
        put("Alg.Alias.Mac.RC5/CFB8", "RC5MAC/CFB8");

        put("Mac.HMACMD2", "org.gudy.bouncycastle.jce.provider.JCEMac$MD2");
        put("Alg.Alias.Mac.HMAC-MD2", "HMACMD2");
        put("Alg.Alias.Mac.HMAC/MD2", "HMACMD2");

        put("Mac.HMACMD4", "org.gudy.bouncycastle.jce.provider.JCEMac$MD4");
        put("Alg.Alias.Mac.HMAC-MD4", "HMACMD4");
        put("Alg.Alias.Mac.HMAC/MD4", "HMACMD4");

        put("Mac.HMACMD5", "org.gudy.bouncycastle.jce.provider.JCEMac$MD5");
        put("Alg.Alias.Mac.HMAC-MD5", "HMACMD5");
        put("Alg.Alias.Mac.HMAC/MD5", "HMACMD5");

        put("Mac.HMACRIPEMD128", "org.gudy.bouncycastle.jce.provider.JCEMac$RIPEMD128");
        put("Alg.Alias.Mac.HMAC-RIPEMD128", "HMACRIPEMD128");
        put("Alg.Alias.Mac.HMAC/RIPEMD128", "HMACRIPEMD128");

        put("Mac.HMACRIPEMD160", "org.gudy.bouncycastle.jce.provider.JCEMac$RIPEMD160");
        put("Alg.Alias.Mac.HMAC-RIPEMD160", "HMACRIPEMD160");
        put("Alg.Alias.Mac.HMAC/RIPEMD160", "HMACRIPEMD160");

        put("Mac.HMACSHA1", "org.gudy.bouncycastle.jce.provider.JCEMac$SHA1");
        put("Alg.Alias.Mac.HMAC-SHA1", "HMACSHA1");
        put("Alg.Alias.Mac.HMAC/SHA1", "HMACSHA1");

        put("Mac.HMACSHA256", "org.gudy.bouncycastle.jce.provider.JCEMac$SHA256");
        put("Alg.Alias.Mac.HMAC-SHA256", "HMACSHA256");
        put("Alg.Alias.Mac.HMAC/SHA256", "HMACSHA256");

        put("Mac.HMACSHA384", "org.gudy.bouncycastle.jce.provider.JCEMac$SHA384");
        put("Alg.Alias.Mac.HMAC-SHA384", "HMACSHA384");
        put("Alg.Alias.Mac.HMAC/SHA384", "HMACSHA384");

        put("Mac.HMACSHA512", "org.gudy.bouncycastle.jce.provider.JCEMac$SHA512");
        put("Alg.Alias.Mac.HMAC-SHA512", "HMACSHA512");
        put("Alg.Alias.Mac.HMAC/SHA512", "HMACSHA512");

        put("Mac.HMACTiger", "org.gudy.bouncycastle.jce.provider.JCEMac$Tiger");
        put("Alg.Alias.Mac.HMAC-Tiger", "HMACTiger");
        put("Alg.Alias.Mac.HMAC/Tiger", "HMACTiger");

        put("Mac.PBEWITHHMACSHA", "org.gudy.bouncycastle.jce.provider.JCEMac$PBEWithSHA");
        put("Mac.PBEWITHHMACRIPEMD160", "org.gudy.bouncycastle.jce.provider.JCEMac$PBEWithRIPEMD160");
        put("Alg.Alias.Mac.1.3.14.3.2.26", "PBEWITHHMACSHA");

        //
        // MessageDigests
        //
        put("MessageDigest.SHA-1", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$SHA1");
        put("Alg.Alias.MessageDigest.SHA1", "SHA-1");
        put("Alg.Alias.MessageDigest.SHA", "SHA-1");
        put("Alg.Alias.MessageDigest.1.3.14.3.2.26", "SHA-1");
        put("MessageDigest.SHA-256", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$SHA256");
        put("MessageDigest.SHA-384", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$SHA384");
        put("MessageDigest.SHA-512", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$SHA512");
        put("MessageDigest.MD2", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$MD2");
        put("MessageDigest.MD4", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$MD4");
        put("MessageDigest.MD5", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$MD5");
        put("MessageDigest.1.2.840.113549.2.5", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$MD5");
        put("MessageDigest.RIPEMD128", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$RIPEMD128");
        put("MessageDigest.RIPEMD160", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$RIPEMD160");
		put("MessageDigest.RIPEMD256", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$RIPEMD256");
		put("MessageDigest.RIPEMD320", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$RIPEMD320");
        put("MessageDigest.Tiger", "org.gudy.bouncycastle.jce.provider.JDKMessageDigest$Tiger");

        //
        // signature algorithms.
        //
        put("Signature.MD2WithRSAEncryption", "org.gudy.bouncycastle.jce.provider.JDKDigestSignature$MD2WithRSAEncryption");
        put("Signature.MD5WithRSAEncryption", "org.gudy.bouncycastle.jce.provider.JDKDigestSignature$MD5WithRSAEncryption");
        put("Signature.SHA1WithRSAEncryption", "org.gudy.bouncycastle.jce.provider.JDKDigestSignature$SHA1WithRSAEncryption");
        put("Signature.RIPEMD160WithRSAEncryption", "org.gudy.bouncycastle.jce.provider.JDKDigestSignature$RIPEMD160WithRSAEncryption");
		put("Signature.RIPEMD128WithRSAEncryption", "org.gudy.bouncycastle.jce.provider.JDKDigestSignature$RIPEMD128WithRSAEncryption");
		put("Signature.RIPEMD256WithRSAEncryption", "org.gudy.bouncycastle.jce.provider.JDKDigestSignature$RIPEMD256WithRSAEncryption");
        put("Signature.DSA", "org.gudy.bouncycastle.jce.provider.JDKDSASigner$stdDSA");
        put("Signature.ECDSA", "org.gudy.bouncycastle.jce.provider.JDKDSASigner$ecDSA");
        put("Signature.SHA1withRSA/ISO9796-2", "org.gudy.bouncycastle.jce.provider.JDKISOSignature$SHA1WithRSAEncryption");
        put("Signature.MD5withRSA/ISO9796-2", "org.gudy.bouncycastle.jce.provider.JDKISOSignature$MD5WithRSAEncryption");
        put("Signature.RIPEMD160withRSA/ISO9796-2", "org.gudy.bouncycastle.jce.provider.JDKISOSignature$RIPEMD160WithRSAEncryption");

		put("Signature.SHA1withRSA/PSS", "org.gudy.bouncycastle.jce.provider.JDKPSSSigner$SHA1withRSA");
		put("Signature.SHA256withRSA/PSS", "org.gudy.bouncycastle.jce.provider.JDKPSSSigner$SHA256withRSA");
		put("Signature.SHA384withRSA/PSS", "org.gudy.bouncycastle.jce.provider.JDKPSSSigner$SHA384withRSA");
		put("Signature.SHA512withRSA/PSS", "org.gudy.bouncycastle.jce.provider.JDKPSSSigner$SHA512withRSA");

		put("Alg.Alias.Signature.MD2withRSAEncryption", "MD2WithRSAEncryption");
		put("Alg.Alias.Signature.MD5withRSAEncryption", "MD5WithRSAEncryption");
		put("Alg.Alias.Signature.SHA1withRSAEncryption", "SHA1WithRSAEncryption");

		put("Alg.Alias.Signature.SHA256withRSAEncryption", "SHA256withRSA/PSS");
		put("Alg.Alias.Signature.SHA384withRSAEncryption", "SHA384withRSA/PSS");
		put("Alg.Alias.Signature.SHA512withRSAEncryption", "SHA512withRSA/PSS");

		put("Alg.Alias.Signature.SHA256WithRSAEncryption", "SHA256withRSA/PSS");
		put("Alg.Alias.Signature.SHA384WithRSAEncryption", "SHA384withRSA/PSS");
		put("Alg.Alias.Signature.SHA512WithRSAEncryption", "SHA512withRSA/PSS");

		put("Alg.Alias.Signature.SHA256WITHRSAENCRYPTION", "SHA256withRSA/PSS");
		put("Alg.Alias.Signature.SHA384WITHRSAENCRYPTION", "SHA384withRSA/PSS");
		put("Alg.Alias.Signature.SHA512WITHRSAENCRYPTION", "SHA512withRSA/PSS");

		put("Alg.Alias.Signature.RIPEMD160withRSAEncryption", "RIPEMD160WithRSAEncryption");

		put("Alg.Alias.Signature.1.2.840.113549.1.1.2", "MD2WithRSAEncryption");
		put("Alg.Alias.Signature.MD2WithRSA", "MD2WithRSAEncryption");
		put("Alg.Alias.Signature.MD2withRSA", "MD2WithRSAEncryption");
		put("Alg.Alias.Signature.MD2/RSA", "MD2WithRSAEncryption");
		put("Alg.Alias.Signature.MD5WithRSA", "MD5WithRSAEncryption");
		put("Alg.Alias.Signature.MD5withRSA", "MD5WithRSAEncryption");
		put("Alg.Alias.Signature.MD5/RSA", "MD5WithRSAEncryption");
		put("Alg.Alias.Signature.1.2.840.113549.1.1.4", "MD5WithRSAEncryption");
		put("Alg.Alias.Signature.SHA1WithRSA", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.SHA1withRSA", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.SHA1/RSA", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.SHA-1/RSA", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.1.2.840.113549.1.1.5", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.1.3.14.3.2.26with1.2.840.113549.1.1.1", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.1.3.14.3.2.26with1.2.840.113549.1.1.5", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.1.2.840.113549.2.5with1.2.840.113549.1.1.1", "MD5WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD160WithRSA", "RIPEMD160WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD160withRSA", "RIPEMD160WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD128WithRSA", "RIPEMD128WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD128withRSA", "RIPEMD128WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD256WithRSA", "RIPEMD256WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD256withRSA", "RIPEMD256WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD-160/RSA", "RIPEMD160WithRSAEncryption");
		put("Alg.Alias.Signature.RMD160withRSA", "RIPEMD160WithRSAEncryption");
		put("Alg.Alias.Signature.RMD160/RSA", "RIPEMD160WithRSAEncryption");
		put("Alg.Alias.Signature.1.3.36.3.3.1.2", "RIPEMD160WithRSAEncryption");
		put("Alg.Alias.Signature.1.3.36.3.3.1.3", "RIPEMD128WithRSAEncryption");
		put("Alg.Alias.Signature.1.3.36.3.3.1.4", "RIPEMD256WithRSAEncryption");

		put("Alg.Alias.Signature.MD2WITHRSAENCRYPTION", "MD2WithRSAEncryption");
		put("Alg.Alias.Signature.MD5WITHRSAENCRYPTION", "MD5WithRSAEncryption");
		put("Alg.Alias.Signature.SHA1WITHRSAENCRYPTION", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD160WITHRSAENCRYPTION", "RIPEMD160WithRSAEncryption");

		put("Alg.Alias.Signature.MD5WITHRSA", "MD5WithRSAEncryption");
		put("Alg.Alias.Signature.SHA1WITHRSA", "SHA1WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD160WITHRSA", "RIPEMD160WithRSAEncryption");
		put("Alg.Alias.Signature.RMD160WITHRSA", "RIPEMD160WithRSAEncryption");
		put("Alg.Alias.Signature.RIPEMD160WITHRSA", "RIPEMD160WithRSAEncryption");

		put("Alg.Alias.Signature.SHA1withECDSA", "ECDSA");
		put("Alg.Alias.Signature.ECDSAwithSHA1", "ECDSA");
		put("Alg.Alias.Signature.SHA1WITHECDSA", "ECDSA");
		put("Alg.Alias.Signature.ECDSAWITHSHA1", "ECDSA");
		put("Alg.Alias.Signature.SHA1WithECDSA", "ECDSA");
		put("Alg.Alias.Signature.ECDSAWithSHA1", "ECDSA");
        put("Alg.Alias.Signature.1.2.840.10045.4.1", "ECDSA");
		put("Alg.Alias.Signature.SHA/DSA", "DSA");
		put("Alg.Alias.Signature.SHA1withDSA", "DSA");
		put("Alg.Alias.Signature.SHA1WITHDSA", "DSA");
		put("Alg.Alias.Signature.1.3.14.3.2.26with1.2.840.10040.4.1", "DSA");
		put("Alg.Alias.Signature.1.3.14.3.2.26with1.2.840.10040.4.3", "DSA");
		put("Alg.Alias.Signature.DSAwithSHA1", "DSA");
		put("Alg.Alias.Signature.DSAWITHSHA1", "DSA");
		put("Alg.Alias.Signature.SHA1WithDSA", "DSA");
		put("Alg.Alias.Signature.DSAWithSHA1", "DSA");
        put("Alg.Alias.Signature.1.2.840.10040.4.3", "DSA");
        put("Alg.Alias.Signature.MD5WithRSA/ISO9796-2", "MD5withRSA/ISO9796-2");
        put("Alg.Alias.Signature.SHA1WithRSA/ISO9796-2", "SHA1withRSA/ISO9796-2");
        put("Alg.Alias.Signature.RIPEMD160WithRSA/ISO9796-2", "RIPEMD160withRSA/ISO9796-2");

	// Certification Path API
	put("CertPathValidator.PKIX", "org.gudy.bouncycastle.jce.provider.PKIXCertPathValidatorSpi" );
	put("CertPathValidator.PKIX ValidationAlgorithm", "RFC2459");
	put("CertPathBuilder.PKIX", "org.gudy.bouncycastle.jce.provider.PKIXCertPathBuilderSpi" );
	put("CertPathBuilder.PKIX ValidationAlgorithm", "RFC2459");
	put("CertStore.Collection", "org.gudy.bouncycastle.jce.provider.CertStoreCollectionSpi" );
	}
}
