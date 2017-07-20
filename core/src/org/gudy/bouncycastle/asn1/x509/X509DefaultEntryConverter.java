package org.gudy.bouncycastle.asn1.x509;

import java.io.IOException;

import org.gudy.bouncycastle.asn1.*;

/**
 * The default converter for X509 DN entries when going from their
 * string value to ASN.1 strings.
 */
public class X509DefaultEntryConverter
    extends X509NameEntryConverter
{
    /**
     * Apply default coversion for the given value depending on the oid
     * and the character range of the value.
     *
     * @param oid the object identifier for the DN entry
     * @param value the value associated with it
     * @return the ASN.1 equivalent for the string value.
     */
    @Override
    public DERObject getConvertedValue(
        DERObjectIdentifier  oid,
        String               value)
    {
        if (value.length() != 0 && value.charAt(0) == '#')
        {
            try
            {
                return convertHexEncoded(value, 1);
            }
            catch (IOException e)
            {
                throw new RuntimeException("can't recode value for oid " + oid.getId());
            }
        }
        else if (oid.equals(X509Name.EmailAddress) || oid.equals(X509Name.DC))
        {
            return new DERIA5String(value);
        }
        else if (oid.equals(X509Name.DATE_OF_BIRTH))  // accept time string as well as # (for compatibility)
        {
            return new DERGeneralizedTime(value);
        }
        else if (oid.equals(X509Name.C) || oid.equals(X509Name.SN) || oid.equals(X509Name.DN_QUALIFIER))
        {
             return new DERPrintableString(value);
        }

        return new DERUTF8String(value);
    }
}
