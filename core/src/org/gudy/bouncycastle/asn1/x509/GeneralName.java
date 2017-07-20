package org.gudy.bouncycastle.asn1.x509;

import org.gudy.bouncycastle.asn1.*;
import org.gudy.bouncycastle.util.IPAddress;
import org.gudy.bouncycastle.util.Strings;

/**
 * The GeneralName object.
 * <pre>
 * GeneralName ::= CHOICE {
 *      otherName                       [0]     OtherName,
 *      rfc822Name                      [1]     IA5String,
 *      dNSName                         [2]     IA5String,
 *      x400Address                     [3]     ORAddress,
 *      directoryName                   [4]     Name,
 *      ediPartyName                    [5]     EDIPartyName,
 *      uniformResourceIdentifier       [6]     IA5String,
 *      iPAddress                       [7]     OCTET STRING,
 *      registeredID                    [8]     OBJECT IDENTIFIER}
 *
 * OtherName ::= SEQUENCE {
 *      type-id    OBJECT IDENTIFIER,
 *      value      [0] EXPLICIT ANY DEFINED BY type-id }
 *
 * EDIPartyName ::= SEQUENCE {
 *      nameAssigner            [0]     DirectoryString OPTIONAL,
 *      partyName               [1]     DirectoryString }
 *
 * Name ::= CHOICE { RDNSequence }
 * </pre>
 */
public class GeneralName
    extends ASN1Encodable
    implements ASN1Choice
{
    public static final int otherName                     = 0;
    public static final int rfc822Name                    = 1;
    public static final int dNSName                       = 2;
    public static final int x400Address                   = 3;
    public static final int directoryName                 = 4;
    public static final int ediPartyName                  = 5;
    public static final int uniformResourceIdentifier     = 6;
    public static final int iPAddress                     = 7;
    public static final int registeredID                  = 8;

    DEREncodable      obj;
    int               tag;

    public GeneralName(
        X509Name  dirName)
    {
        this.obj = dirName;
        this.tag = 4;
    }

    /**
     * @deprecated this constructor seems the wrong way round! Use GeneralName(tag, name).
     */
    public GeneralName(
        DERObject name, int tag)
    {
        this.obj = name;
        this.tag = tag;
    }

    /**
     * When the subjectAltName extension contains an Internet mail address,
     * the address MUST be included as an rfc822Name. The format of an
     * rfc822Name is an "addr-spec" as defined in RFC 822 [RFC 822].
     *
     * When the subjectAltName extension contains a domain name service
     * label, the domain name MUST be stored in the dNSName (an IA5String).
     * The name MUST be in the "preferred name syntax," as specified by RFC
     * 1034 [RFC 1034].
     *
     * When the subjectAltName extension contains a URI, the name MUST be
     * stored in the uniformResourceIdentifier (an IA5String). The name MUST
     * be a non-relative URL, and MUST follow the URL syntax and encoding
     * rules specified in [RFC 1738].  The name must include both a scheme
     * (e.g., "http" or "ftp") and a scheme-specific-part.  The scheme-
     * specific-part must include a fully qualified domain name or IP
     * address as the host.
     *
     * When the subjectAltName extension contains a iPAddress, the address
     * MUST be stored in the octet string in "network byte order," as
     * specified in RFC 791 [RFC 791]. The least significant bit (LSB) of
     * each octet is the LSB of the corresponding byte in the network
     * address. For IP Version 4, as specified in RFC 791, the octet string
     * MUST contain exactly four octets.  For IP Version 6, as specified in
     * RFC 1883, the octet string MUST contain exactly sixteen octets [RFC
     * 1883].
     */
    public GeneralName(
        int           tag,
        ASN1Encodable name)
    {
        this.obj = name;
        this.tag = tag;
    }

    /**
     * Create a GeneralName for the given tag from the passed in String.
     * <p>
     * This constructor can handle:
     * <ul>
     * <li>rfc822Name
     * <li>iPAddress
     * <li>directoryName
     * <li>dNSName
     * <li>uniformResourceIdentifier
     * <li>registeredID
     * </ul>
     * For x400Address, otherName and ediPartyName there is no common string
     * format defined.
     * <p>
     * Note: A directory name can be encoded in different ways into a byte
     * representation. Be aware of this if the byte representation is used for
     * comparing results.
     *
     * @param tag tag number
     * @param name string representation of name
     * @throws IllegalArgumentException if the string encoding is not correct or     *             not supported.
     */
    public GeneralName(
        int       tag,
        String    name)
    {
        this.tag = tag;

        if (tag == rfc822Name || tag == dNSName || tag == uniformResourceIdentifier)
        {
            this.obj = new DERIA5String(name);
        }
        else if (tag == registeredID)
        {
            this.obj = new DERObjectIdentifier(name);
        }
        else if (tag == directoryName)
        {
            this.obj = new X509Name(name);
        }
        else if (tag == iPAddress)
        {
            if (IPAddress.isValid(name))
            {
                this.obj = new DEROctetString(Strings.toUTF8ByteArray(name));
            }
            else
            {
                throw new IllegalArgumentException("IP Address is invalid");
            }
        }
        else
        {
            throw new IllegalArgumentException("can't process String for tag: " + tag);
        }
    }

    public static GeneralName getInstance(
        Object obj)
    {
        if (obj == null || obj instanceof GeneralName)
        {
            return (GeneralName)obj;
        }

        if (obj instanceof ASN1TaggedObject)
        {
            ASN1TaggedObject    tagObj = (ASN1TaggedObject)obj;
            int                 tag = tagObj.getTagNo();

            switch (tag)
            {
            case otherName:
                return new GeneralName(tag, ASN1Sequence.getInstance(tagObj, false));
            case rfc822Name:
                return new GeneralName(tag, DERIA5String.getInstance(tagObj, false));
            case dNSName:
                return new GeneralName(tag, DERIA5String.getInstance(tagObj, false));
            case x400Address:
                throw new IllegalArgumentException("unknown tag: " + tag);
            case directoryName:
                return new GeneralName(tag, ASN1Sequence.getInstance(tagObj, true));
            case ediPartyName:
                return new GeneralName(tag, ASN1Sequence.getInstance(tagObj, false));
            case uniformResourceIdentifier:
                return new GeneralName(tag, DERIA5String.getInstance(tagObj, false));
            case iPAddress:
                return new GeneralName(tag, ASN1OctetString.getInstance(tagObj, false));
            case registeredID:
                return new GeneralName(tag, DERObjectIdentifier.getInstance(tagObj, false));
            }
        }

        throw new IllegalArgumentException("unknown object in getInstance");
    }

    public static GeneralName getInstance(
        ASN1TaggedObject tagObj,
        boolean          explicit)
    {
        return GeneralName.getInstance(ASN1TaggedObject.getInstance(tagObj, true));
    }

    public int getTagNo()
    {
        return tag;
    }

    public DEREncodable getName()
    {
        return obj;
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder();

        buf.append(tag);
        buf.append(": ");
        switch (tag)
        {
        case rfc822Name:
        case dNSName:
        case uniformResourceIdentifier:
            buf.append(DERIA5String.getInstance(obj).getString());
            break;
        case directoryName:
            buf.append(X509Name.getInstance(obj).toString());
            break;
        default:
            buf.append(obj.toString());
        }
        return buf.toString();
    }

    @Override
    public DERObject toASN1Object()
    {
        if (tag == directoryName)       // directoryName is explicitly tagged as it is a CHOICE
        {
            return new DERTaggedObject(true, tag, obj);
        }
        else
        {
            return new DERTaggedObject(false, tag, obj);
        }
    }
}
