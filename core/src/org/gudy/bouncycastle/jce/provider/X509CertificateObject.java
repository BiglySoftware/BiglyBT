package org.gudy.bouncycastle.jce.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.util.*;

import javax.security.auth.x500.X500Principal;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.gudy.bouncycastle.asn1.misc.NetscapeCertType;
import org.gudy.bouncycastle.asn1.misc.NetscapeRevocationURL;
import org.gudy.bouncycastle.asn1.misc.VerisignCzagExtension;
import org.gudy.bouncycastle.asn1.util.ASN1Dump;
import org.gudy.bouncycastle.asn1.x509.*;
import org.gudy.bouncycastle.asn1.x509.X509Extension;
import org.gudy.bouncycastle.jce.X509Principal;
import org.gudy.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;
import org.gudy.bouncycastle.util.encoders.Hex;

public class X509CertificateObject
    extends X509Certificate
    implements PKCS12BagAttributeCarrier
{
    private X509CertificateStructure    c;
    private Hashtable                   pkcs12Attributes = new Hashtable();
    private Vector                      pkcs12Ordering = new Vector();

    public X509CertificateObject(
        X509CertificateStructure    c)
    {
        this.c = c;
    }

    @Override
    public void checkValidity()
        throws CertificateExpiredException, CertificateNotYetValidException
    {
        this.checkValidity(new Date());
    }

    @Override
    public void checkValidity(
        Date    date)
        throws CertificateExpiredException, CertificateNotYetValidException
    {
        if (date.after(this.getNotAfter()))
        {
            throw new CertificateExpiredException("certificate expired on " + c.getEndDate().getTime());
        }

        if (date.before(this.getNotBefore()))
        {
            throw new CertificateNotYetValidException("certificate not valid till " + c.getStartDate().getTime());
        }
    }

    @Override
    public int getVersion()
    {
        return c.getVersion();
    }

    @Override
    public BigInteger getSerialNumber()
    {
        return c.getSerialNumber().getValue();
    }

    @Override
    public Principal getIssuerDN()
    {
        return new X509Principal(c.getIssuer());
    }

    @Override
    public X500Principal getIssuerX500Principal()
    {
        try
        {
            ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
            ASN1OutputStream        aOut = new ASN1OutputStream(bOut);

            aOut.writeObject(c.getIssuer());

            return new X500Principal(bOut.toByteArray());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("can't encode issuer DN");
        }
    }

    @Override
    public Principal getSubjectDN()
    {
        return new X509Principal(c.getSubject());
    }

    @Override
    public X500Principal getSubjectX500Principal()
    {
        try
        {
            ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
            ASN1OutputStream        aOut = new ASN1OutputStream(bOut);

            aOut.writeObject(c.getSubject());

            return new X500Principal(bOut.toByteArray());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("can't encode issuer DN");
        }
    }

    @Override
    public Date getNotBefore()
    {
        return c.getStartDate().getDate();
    }

    @Override
    public Date getNotAfter()
    {
        return c.getEndDate().getDate();
    }

    @Override
    public byte[] getTBSCertificate()
        throws CertificateEncodingException
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        DEROutputStream         dOut = new DEROutputStream(bOut);

        try
        {
            dOut.writeObject(c.getTBSCertificate());

            return bOut.toByteArray();
        }
        catch (IOException e)
        {
            throw new CertificateEncodingException(e.toString());
        }
    }

    @Override
    public byte[] getSignature()
    {
        return c.getSignature().getBytes();
    }

    /**
     * return a more "meaningful" representation for the signature algorithm used in
     * the certficate.
     */
    @Override
    public String getSigAlgName()
    {
        Provider    prov = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        String      algName = prov.getProperty("Alg.Alias.Signature." + this.getSigAlgOID());

        if (algName != null)
        {
            return algName;
        }

        Provider[] provs = Security.getProviders();

        //
        // search every provider looking for a real algorithm
        //
        for (int i = 0; i != provs.length; i++)
        {
            algName = provs[i].getProperty("Alg.Alias.Signature." + this.getSigAlgOID());
            if (algName != null)
            {
                return algName;
            }
        }

        return this.getSigAlgOID();
    }

    /**
     * return the object identifier for the signature.
     */
    @Override
    public String getSigAlgOID()
    {
        return c.getSignatureAlgorithm().getObjectId().getId();
    }

    /**
     * return the signature parameters, or null if there aren't any.
     */
    @Override
    public byte[] getSigAlgParams()
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();

        if (c.getSignatureAlgorithm().getParameters() != null)
        {
            try
            {
                DEROutputStream         dOut = new DEROutputStream(bOut);

                dOut.writeObject(c.getSignatureAlgorithm().getParameters());
            }
            catch (Exception e)
            {
                throw new RuntimeException("exception getting sig parameters " + e);
            }

            return bOut.toByteArray();
        }
        else
        {
            return null;
        }
    }

    @Override
    public boolean[] getIssuerUniqueID()
    {
        DERBitString    id = c.getTBSCertificate().getIssuerUniqueId();

        if (id != null)
        {
            byte[]          bytes = id.getBytes();
            boolean[]       boolId = new boolean[bytes.length * 8 - id.getPadBits()];

            for (int i = 0; i != boolId.length; i++)
            {
                boolId[i] = (bytes[i / 8] & (0x80 >>> (i % 8))) != 0;
            }

            return boolId;
        }

        return null;
    }

    @Override
    public boolean[] getSubjectUniqueID()
    {
        DERBitString    id = c.getTBSCertificate().getSubjectUniqueId();

        if (id != null)
        {
            byte[]          bytes = id.getBytes();
            boolean[]       boolId = new boolean[bytes.length * 8 - id.getPadBits()];

            for (int i = 0; i != boolId.length; i++)
            {
                boolId[i] = (bytes[i / 8] & (0x80 >>> (i % 8))) != 0;
            }

            return boolId;
        }

        return null;
    }

    @Override
    public boolean[] getKeyUsage()
    {
        byte[]  bytes = this.getExtensionBytes("2.5.29.15");
        int     length = 0;

        if (bytes != null)
        {
            try
            {
                DERInputStream  dIn = new DERInputStream(new ByteArrayInputStream(bytes));
                DERBitString    bits = (DERBitString)dIn.readObject();

                bytes = bits.getBytes();
                length = (bytes.length * 8) - bits.getPadBits();
            }
            catch (Exception e)
            {
                throw new RuntimeException("error processing key usage extension");
            }

            boolean[]       keyUsage = new boolean[(length < 9) ? 9 : length];

            for (int i = 0; i != length; i++)
            {
                keyUsage[i] = (bytes[i / 8] & (0x80 >>> (i % 8))) != 0;
            }

            return keyUsage;
        }

        return null;
    }

    @Override
    public int getBasicConstraints()
    {
        byte[]  bytes = this.getExtensionBytes("2.5.29.19");

        if (bytes != null)
        {
            try
            {
                DERInputStream  dIn = new DERInputStream(new ByteArrayInputStream(bytes));
                ASN1Sequence    seq = (ASN1Sequence)dIn.readObject();

                if (seq.size() == 2)
                {
                    if (((DERBoolean)seq.getObjectAt(0)).isTrue())
                    {
                        return ((DERInteger)seq.getObjectAt(1)).getValue().intValue();
                    }
                    else
                    {
                        return -1;
                    }
                }
                else if (seq.size() == 1)
                {
                    if (seq.getObjectAt(0) instanceof DERBoolean)
                    {
                        if (((DERBoolean)seq.getObjectAt(0)).isTrue())
                        {
                            return Integer.MAX_VALUE;
                        }
                        else
                        {
                            return -1;
                        }
                    }
                    else
                    {
                        return -1;
                    }
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException("error processing key usage extension");
            }
        }

        return -1;
    }

    @Override
    public Set getCriticalExtensionOIDs()
    {
        if (this.getVersion() == 3)
        {
            HashSet         set = new HashSet();
            X509Extensions  extensions = c.getTBSCertificate().getExtensions();

            if (extensions != null)
            {
                Enumeration     e = extensions.oids();

                while (e.hasMoreElements())
                {
                    DERObjectIdentifier oid = (DERObjectIdentifier)e.nextElement();
                    X509Extension       ext = extensions.getExtension(oid);

                    if (ext.isCritical())
                    {
                        set.add(oid.getId());
                    }
                }

                return set;
            }
        }

        return null;
    }

    private byte[] getExtensionBytes(String oid)
    {
        X509Extensions exts = c.getTBSCertificate().getExtensions();

        if (exts != null)
        {
            X509Extension   ext = exts.getExtension(new DERObjectIdentifier(oid));
            if (ext != null)
            {
                return ext.getValue().getOctets();
            }
        }

        return null;
    }

    @Override
    public byte[] getExtensionValue(String oid)
    {
        X509Extensions exts = c.getTBSCertificate().getExtensions();

        if (exts != null)
        {
            X509Extension   ext = exts.getExtension(new DERObjectIdentifier(oid));

            if (ext != null)
            {
                ByteArrayOutputStream    bOut = new ByteArrayOutputStream();
                DEROutputStream            dOut = new DEROutputStream(bOut);

                try
                {
                    dOut.writeObject(ext.getValue());

                    return bOut.toByteArray();
                }
                catch (Exception e)
                {
                    throw new RuntimeException("error encoding " + e.toString());
                }
            }
        }

        return null;
    }

    @Override
    public Set getNonCriticalExtensionOIDs()
    {
        if (this.getVersion() == 3)
        {
            HashSet         set = new HashSet();
            X509Extensions  extensions = c.getTBSCertificate().getExtensions();

            if (extensions != null)
            {
                Enumeration     e = extensions.oids();

                while (e.hasMoreElements())
                {
                    DERObjectIdentifier oid = (DERObjectIdentifier)e.nextElement();
                    X509Extension       ext = extensions.getExtension(oid);

                    if (!ext.isCritical())
                    {
                        set.add(oid.getId());
                    }
                }

                return set;
            }
        }

        return null;
    }

    @Override
    public boolean hasUnsupportedCriticalExtension()
    {
        if (this.getVersion() == 3)
        {
            X509Extensions  extensions = c.getTBSCertificate().getExtensions();

            if (extensions != null)
            {
                Enumeration     e = extensions.oids();

                while (e.hasMoreElements())
                {
                    DERObjectIdentifier oid = (DERObjectIdentifier)e.nextElement();
                    if (oid.getId().equals("2.5.29.15")
                       || oid.getId().equals("2.5.29.19"))
                    {
                        continue;
                    }

                    X509Extension       ext = extensions.getExtension(oid);

                    if (ext.isCritical())
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public PublicKey getPublicKey()
    {
        return JDKKeyFactory.createPublicKeyFromPublicKeyInfo(c.getSubjectPublicKeyInfo());
    }

    @Override
    public byte[] getEncoded()
        throws CertificateEncodingException
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        DEROutputStream         dOut = new DEROutputStream(bOut);

        try
        {
            dOut.writeObject(c);

            return bOut.toByteArray();
        }
        catch (IOException e)
        {
            throw new CertificateEncodingException(e.toString());
        }
    }

    @Override
    public void setBagAttribute(
        DERObjectIdentifier oid,
        DEREncodable        attribute)
    {
        pkcs12Attributes.put(oid, attribute);
        pkcs12Ordering.addElement(oid);
    }

    @Override
    public DEREncodable getBagAttribute(
        DERObjectIdentifier oid)
    {
        return (DEREncodable)pkcs12Attributes.get(oid);
    }

    @Override
    public Enumeration getBagAttributeKeys()
    {
        return pkcs12Ordering.elements();
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        String          nl = System.getProperty("line.separator");

        buf.append("  [0]         Version: ").append(this.getVersion()).append(nl);
        buf.append("         SerialNumber: ").append(this.getSerialNumber()).append(nl);
        buf.append("             IssuerDN: ").append(this.getIssuerDN()).append(nl);
        buf.append("           Start Date: ").append(this.getNotBefore()).append(nl);
        buf.append("           Final Date: ").append(this.getNotAfter()).append(nl);
        buf.append("            SubjectDN: ").append(this.getSubjectDN()).append(nl);
        buf.append("           Public Key: ").append(this.getPublicKey()).append(nl);
        buf.append("  Signature Algorithm: ").append(this.getSigAlgName()).append(nl);

        byte[]  sig = this.getSignature();

        buf.append("            Signature: ").append(new String(Hex.encode(sig, 0, 20))).append(nl);
        for (int i = 20; i < sig.length; i += 20)
        {
            if (i < sig.length - 20)
            {
                buf.append("                       ").append(new String(Hex.encode(sig, i, 20))).append(nl);
            }
            else
            {
                buf.append("                       ").append(new String(Hex.encode(sig, i, sig.length - i))).append(nl);
            }
        }

        X509Extensions  extensions = c.getTBSCertificate().getExtensions();

        if (extensions != null)
        {
            Enumeration     e = extensions.oids();

            if (e.hasMoreElements())
            {
                buf.append("       Extensions: \n");
            }

            while (e.hasMoreElements())
            {
                DERObjectIdentifier     oid = (DERObjectIdentifier)e.nextElement();
                X509Extension           ext = extensions.getExtension(oid);

                if (ext.getValue() != null)
                {
                    byte[]                  octs = ext.getValue().getOctets();
                    ByteArrayInputStream    bIn = new ByteArrayInputStream(octs);
                    DERInputStream          dIn = new DERInputStream(bIn);
                    buf.append("                       critical(").append(ext.isCritical()).append(") ");
                    try
                    {
                        if (oid.equals(X509Extensions.BasicConstraints))
                        {
                            buf.append(new BasicConstraints((ASN1Sequence) dIn.readObject())).append(nl);
                        }
                        else if (oid.equals(X509Extensions.KeyUsage))
                        {
                            buf.append(new KeyUsage((DERBitString) dIn.readObject())).append(nl);
                        }
                        else if (oid.equals(MiscObjectIdentifiers.netscapeCertType))
                        {
                            buf.append(new NetscapeCertType((DERBitString) dIn.readObject())).append(nl);
                        }
                        else if (oid.equals(MiscObjectIdentifiers.netscapeRevocationURL))
                        {
                            buf.append(new NetscapeRevocationURL((DERIA5String) dIn.readObject())).append(nl);
                        }
                        else if (oid.equals(MiscObjectIdentifiers.verisignCzagExtension))
                        {
                            buf.append(new VerisignCzagExtension((DERIA5String) dIn.readObject())).append(nl);
                        }
                        else
                        {
                            buf.append(oid.getId());
                            buf.append(" value = ").append(ASN1Dump.dumpAsString(dIn.readObject())).append(nl);
                            //buf.append(" value = " + "*****" + nl);
                        }
                    }
                    catch (Exception ex)
                    {
                        buf.append(oid.getId());
                   //     buf.append(" value = " + new String(Hex.encode(ext.getValue().getOctets())) + nl);
                        buf.append(" value = " + "*****").append(nl);
                    }
                }
                else
                {
                    buf.append(nl);
                }
            }
        }

        return buf.toString();
    }

    @Override
    public final void verify(
        PublicKey   key)
        throws CertificateException, NoSuchAlgorithmException,
        InvalidKeyException, NoSuchProviderException, SignatureException
    {
        Signature   signature = null;

        if (!c.getSignatureAlgorithm().equals(c.getTBSCertificate().getSignature()))
        {
            throw new CertificateException("signature algorithm in TBS cert not same as outer cert");
        }

        try
        {
            signature = Signature.getInstance(c.getSignatureAlgorithm().getObjectId().getId(), BouncyCastleProvider.PROVIDER_NAME);
        }
        catch (Exception e)
        {
            signature = Signature.getInstance(c.getSignatureAlgorithm().getObjectId().getId());
        }

        signature.initVerify(key);

        signature.update(this.getTBSCertificate());

        if (!signature.verify(this.getSignature()))
        {
            throw new InvalidKeyException("Public key presented not for certificate signature");
        }
    }

    @Override
    public final void verify(
        PublicKey   key,
        String      sigProvider)
        throws CertificateException, NoSuchAlgorithmException,
        InvalidKeyException, NoSuchProviderException, SignatureException
    {
        Signature signature = Signature.getInstance(c.getSignatureAlgorithm().getObjectId().getId(), sigProvider);

        signature.initVerify(key);

        signature.update(this.getTBSCertificate());

        if (!signature.verify(this.getSignature()))
        {
            throw new InvalidKeyException("Public key presented not for certificate signature");
        }
    }
}
