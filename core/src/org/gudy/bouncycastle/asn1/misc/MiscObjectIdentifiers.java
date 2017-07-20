package org.gudy.bouncycastle.asn1.misc;

import org.gudy.bouncycastle.asn1.DERObjectIdentifier;

public interface MiscObjectIdentifiers
{
    //
    // Netscape
    //       iso/itu(2) joint-assign(16) us(840) uscompany(1) netscape(113730) cert-extensions(1) }
    //
    static final String                 netscape                = "2.16.840.1.113730.1";
    static final DERObjectIdentifier    netscapeCertType        = new DERObjectIdentifier(netscape + ".1");
    static final DERObjectIdentifier    netscapeBaseURL         = new DERObjectIdentifier(netscape + ".2");
    static final DERObjectIdentifier    netscapeRevocationURL   = new DERObjectIdentifier(netscape + ".3");
    static final DERObjectIdentifier    netscapeCARevocationURL = new DERObjectIdentifier(netscape + ".4");
    static final DERObjectIdentifier    netscapeRenewalURL      = new DERObjectIdentifier(netscape + ".7");
    static final DERObjectIdentifier    netscapeCApolicyURL     = new DERObjectIdentifier(netscape + ".8");
    static final DERObjectIdentifier    netscapeSSLServerName   = new DERObjectIdentifier(netscape + ".12");
    static final DERObjectIdentifier    netscapeCertComment     = new DERObjectIdentifier(netscape + ".13");
    //
    // Verisign
    //       iso/itu(2) joint-assign(16) us(840) uscompany(1) verisign(113733) cert-extensions(1) }
    //
    static final String                 verisign                = "2.16.840.1.113733.1";

    //
    // CZAG - country, zip, age, and gender
    //
    static final DERObjectIdentifier    verisignCzagExtension   = new DERObjectIdentifier(verisign + ".6.3");
}
