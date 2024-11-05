package com.amazonaws.iot.fleetwise.vehiclesimulator.cert;

import lombok.Data;

@Data
public final class CertificateAndPrivateKey {

    private final String privateKey;
    private final String certificatePem;

   /* public CertificateAndPrivateKey( String privateKey,  String certificatePem) {
        this.privateKey = privateKey;
        this.certificatePem = certificatePem;
    }

    public String getPrivateKey() {
        return this.privateKey;
    }

    public String getCertificatePem() {
        return this.certificatePem;
    }*/
}

