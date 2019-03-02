package com.biglybt.core.security.impl;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

public class
SETrustingManager
        extends X509ExtendedTrustManager {
    private X509TrustManager delegate;

    public SETrustingManager(
            X509TrustManager _delegate) {
        delegate = _delegate;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        if (delegate != null) {
            delegate.checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType,
                                   Socket socket) throws CertificateException {
        if (delegate != null) {
            delegate.checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType,
                                   SSLEngine engine) throws CertificateException {
        if (delegate != null) {
            delegate.checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        if (delegate != null) {
            delegate.checkServerTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType,
                                   Socket socket) throws CertificateException {
        if (delegate != null) {
            delegate.checkServerTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType,
                                   SSLEngine engine) throws CertificateException {
        if (delegate != null) {
            delegate.checkServerTrusted(chain, authType);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (delegate != null) {
            return (delegate.getAcceptedIssuers());
        }
        return null;
    }
}
